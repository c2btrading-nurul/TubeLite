package com.tubelite.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoResult(
    val title: String,
    val url: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long
)

data class QualityOption(
    val label: String,
    val progressiveUrl: String?,
    val videoOnlyUrl: String?,
    val audioOnlyUrl: String?,
    val hlsUrl: String?
)

data class AudioOption(
    val label: String,
    val url: String
)

data class SubtitleOption(
    val label: String,
    val url: String,
    val mimeType: String
)

data class PlayableStream(
    val title: String,
    val default: QualityOption,
    val options: List<QualityOption>,
    val audioOptions: List<AudioOption>,
    val subtitleOptions: List<SubtitleOption>,
    val thumbnailUrl: String?,
    val channelAvatarUrl: String?,
    val channelUrl: String?
)

data class ChannelInfo(
    val name: String,
    val avatarUrl: String?,
    val videos: List<VideoResult>
)

object YoutubeRepository {

    private var initialized = false

    fun ensureInit() {
        if (!initialized) {
            NewPipe.init(DownloaderImpl.instance)
            initialized = true
        }
    }

    private fun StreamInfoItem.toVideoResult() = VideoResult(
        title = name ?: "",
        url = url,
        uploaderName = uploaderName ?: "",
        thumbnailUrl = thumbnails.firstOrNull()?.url,
        durationSeconds = duration
    )

    private fun <T : InfoItem> loadMoreItems(
        extractor: ListExtractor<T>,
        firstPage: ListExtractor.InfoItemsPage<T>,
        maxItems: Int
    ): List<VideoResult> {
        val result = firstPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideoResult() }
            .toMutableList()

        var page = firstPage

        while (result.size < maxItems) {
            val next = page.nextPage ?: break

            page = try {
                extractor.getPage(next)
            } catch (_: Exception) {
                break
            }

            result += page.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoResult() }

            if (page.items.isEmpty()) {
                break
            }
        }

        return result
            .distinctBy { it.url }
            .take(maxItems)
    }

    suspend fun search(
        query: String,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()

        loadMoreItems(
            extractor,
            extractor.initialPage,
            maxItems
        )
    }

    /**
     * Returns YouTube Shorts discovered through Shorts-related searches.
     *
     * The current NewPipe version used by this project does not expose
     * StreamInfoItem.isShortFormContent(), so Shorts are identified by
     * their canonical YouTube URL containing "/shorts/".
     */
    suspend fun getShorts(
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        val queries = listOf(
            "#shorts",
            "shorts"
        )

        val found = mutableListOf<VideoResult>()

        for (query in queries) {
            if (found.size >= maxItems) {
                break
            }

            try {
                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()

                val items = loadMoreItems(
                    extractor,
                    extractor.initialPage,
                    maxItems
                )

                found += items.filter { video ->
                    video.url.contains("/shorts/", ignoreCase = true)
                }
            } catch (_: Exception) {
                // Try the next Shorts discovery query.
            }
        }

        found
            .distinctBy { it.url }
            .take(maxItems)
    }

    suspend fun getTrending(
        maxItems: Int = 40
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        val kioskList = ServiceList.YouTube.kioskList
        val extractor = kioskList.getDefaultKioskExtractor()

        extractor.fetchPage()

        loadMoreItems(
            extractor,
            extractor.initialPage,
            maxItems
        ).filter {
            it.durationSeconds > 0
        }
    }

    suspend fun getRelated(
        videoUrl: String
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        try {
            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                videoUrl
            )

            val directRelated = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoResult() }

            if (directRelated.size >= 30) {
                directRelated
                    .distinctBy { it.url }
                    .take(30)
            } else {
                val extra = try {
                    search(
                        info.name,
                        maxItems = 30
                    )
                } catch (_: Exception) {
                    emptyList()
                }

                (directRelated + extra)
                    .filter { it.url != videoUrl }
                    .distinctBy { it.url }
                    .take(30)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getChannelVideos(
        channelUrl: String,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        val channelExtractor =
            ServiceList.YouTube.getChannelExtractor(channelUrl)

        channelExtractor.fetchPage()

        val videosTabHandler = channelExtractor.tabs.firstOrNull {
            it.contentFilters.contains(
                org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS
            )
        } ?: return@withContext emptyList()

        val tabExtractor =
            ServiceList.YouTube.getChannelTabExtractor(videosTabHandler)

        tabExtractor.fetchPage()

        loadMoreItems(
            tabExtractor,
            tabExtractor.initialPage,
            maxItems
        )
    }

    suspend fun getChannel(
        channelUrl: String
    ): ChannelInfo = withContext(Dispatchers.IO) {
        ensureInit()

        val channelExtractor =
            ServiceList.YouTube.getChannelExtractor(channelUrl)

        channelExtractor.fetchPage()

        val name = channelExtractor.name ?: ""

        val avatar = try {
            channelExtractor.avatars
                ?.firstOrNull()
                ?.url
        } catch (_: Exception) {
            null
        }

        val videosTabHandler = channelExtractor.tabs.firstOrNull {
            it.contentFilters.contains(
                org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS
            )
        }

        val videos = if (videosTabHandler != null) {
            val tabExtractor =
                ServiceList.YouTube.getChannelTabExtractor(videosTabHandler)

            tabExtractor.fetchPage()

            tabExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoResult() }
        } else {
            emptyList()
        }

        ChannelInfo(
            name = name,
            avatarUrl = avatar,
            videos = videos
        )
    }

    suspend fun getPlayableStream(
        videoUrl: String
    ): PlayableStream = withContext(Dispatchers.IO) {
        ensureInit()

        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            videoUrl
        )

        val thumb = info.thumbnails
            .firstOrNull()
            ?.url

        val progressiveOptions = info.videoStreams
            ?.filter {
                !it.isVideoOnly && it.content != null
            }
            ?.sortedByDescending {
                it.getResolution()
                    ?.replace("p", "")
                    ?.toIntOrNull()
                    ?: 0
            }
            ?.map {
                QualityOption(
                    label = it.getResolution() ?: "Auto",
                    progressiveUrl = it.content,
                    videoOnlyUrl = null,
                    audioOnlyUrl = null,
                    hlsUrl = null
                )
            }
            ?: emptyList()

        val audioStreams =
            info.audioStreams
                ?.filter { it.content != null }
                ?: emptyList()

        val bestAudio =
            audioStreams.maxByOrNull {
                it.averageBitrate
            }

        val videoOnlyOptions =
            if (bestAudio != null) {
                info.videoOnlyStreams
                    ?.filter { it.content != null }
                    ?.sortedByDescending {
                        it.getResolution()
                            ?.replace("p", "")
                            ?.toIntOrNull()
                            ?: 0
                    }
                    ?.map {
                        QualityOption(
                            label = it.getResolution() ?: "Auto",
                            progressiveUrl = null,
                            videoOnlyUrl = it.content,
                            audioOnlyUrl = bestAudio.content,
                            hlsUrl = null
                        )
                    }
                    ?: emptyList()
            } else {
                emptyList()
            }

        val hlsOptions =
            info.hlsUrl?.let {
                listOf(
                    QualityOption(
                        label = "Auto (Live)",
                        progressiveUrl = null,
                        videoOnlyUrl = null,
                        audioOnlyUrl = null,
                        hlsUrl = it
                    )
                )
            } ?: emptyList()

        val allOptions =
            (progressiveOptions + videoOnlyOptions)
                .distinctBy { it.label }
                .ifEmpty {
                    hlsOptions
                }

        if (allOptions.isEmpty()) {
            error("Could not get any stream")
        }

        val audioOptions = audioStreams
            .distinctBy { it.averageBitrate }
            .sortedByDescending { it.averageBitrate }
            .mapIndexed { index, audio ->
                val kbps =
                    if (audio.averageBitrate > 0) {
                        "${audio.averageBitrate / 1000}kbps"
                    } else {
                        "Audio ${index + 1}"
                    }

                AudioOption(
                    label = kbps,
                    url = audio.content
                )
            }

        val subtitleOptions = try {
            info.subtitles
                ?.filter { it.url != null }
                ?.map { subtitle ->
                    val lang =
                        subtitle.languageTag ?: "Subtitle"

                    val mime =
                        subtitle.format?.mimeType ?: "text/vtt"

                    SubtitleOption(
                        label = lang,
                        url = subtitle.url!!,
                        mimeType = mime
                    )
                }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val channelAvatar = try {
            info.uploaderAvatars
                ?.firstOrNull()
                ?.url
        } catch (_: Exception) {
            null
        }

        val channelUrl = try {
            info.uploaderUrl
        } catch (_: Exception) {
            null
        }

        PlayableStream(
            title = info.name,
            default = allOptions.first(),
            options = allOptions,
            audioOptions = audioOptions,
            subtitleOptions = subtitleOptions,
            thumbnailUrl = thumb,
            channelAvatarUrl = channelAvatar,
            channelUrl = channelUrl
        )
    }
}
