package com.tubelite.app.data

import android.content.Context
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

            if (page.items.isEmpty()) break
        }

        return result
            .distinctBy { it.url }
            .take(maxItems)
    }

    /**
     * Normal YouTube search.
     */
    suspend fun search(
        query: String,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) {
            return@withContext emptyList()
        }

        val extractor = ServiceList.YouTube.getSearchExtractor(cleanQuery)

        extractor.fetchPage()

        loadMoreItems(
            extractor,
            extractor.initialPage,
            maxItems
        )
    }

    /**
     * Generic Shorts discovery.
     *
     * We intentionally do NOT use StreamInfoItem.isShortFormContent()
     * because that API is not available in the current NewPipe version.
     *
     * Instead, YouTube Shorts are discovered through Shorts-oriented
     * search queries and limited to videos up to 3 minutes.
     */
    suspend fun getShorts(
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        val queries = listOf(
            "#shorts",
            "shorts",
            "popular shorts",
            "trending shorts",
            "viral shorts"
        )

        val found = mutableListOf<VideoResult>()

        for (query in queries) {
            if (found.size >= maxItems) break

            try {
                val extractor = ServiceList.YouTube.getSearchExtractor(query)

                extractor.fetchPage()

                val items = loadMoreItems(
                    extractor,
                    extractor.initialPage,
                    maxItems
                )

                found += items.filter { isShortCandidate(it) }
            } catch (_: Exception) {
                // Continue with the next discovery query.
            }
        }

        found
            .distinctBy { it.url }
            .take(maxItems)
    }

    /**
     * Personalized Shorts feed.
     *
     * Uses the user's recent search history to discover Shorts related
     * to their interests, while also mixing popular/trending Shorts.
     *
     * No Google account is required because SearchHistoryStore is local.
     */
    suspend fun getPersonalizedShorts(
        context: Context,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        val found = mutableListOf<VideoResult>()

        /*
         * 1. User interest from recent searches.
         *
         * SearchHistoryStore already stores the user's recent searches
         * locally, so no Google account is required.
         */
        val recentSearches = try {
            SearchHistoryStore.getRecent(
                context = context,
                limit = 10
            )
        } catch (_: Exception) {
            emptyList()
        }

        /*
         * Personalized queries get priority.
         *
         * Example:
         * "football shorts"
         * "android shorts"
         * "bangladesh travel shorts"
         */
        val personalizedQueries = recentSearches
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { query ->
                listOf(
                    "$query shorts",
                    "#shorts $query"
                )
            }

        /*
         * 2. General popular/trending Shorts.
         *
         * These make sure a new user with little/no search history
         * still receives a useful Shorts feed.
         */
        val discoveryQueries = listOf(
            "#shorts",
            "trending shorts",
            "popular shorts",
            "viral shorts",
            "youtube shorts"
        )

        val queries = (personalizedQueries + discoveryQueries)
            .distinct()
            .take(20)

        /*
         * Fetch enough candidates so that filtering does not leave
         * the feed empty.
         */
        val perQueryLimit = maxOf(
            12,
            minOf(30, maxItems)
        )

        for (query in queries) {
            if (found.size >= maxItems * 2) break

            try {
                val extractor = ServiceList.YouTube.getSearchExtractor(query)

                extractor.fetchPage()

                val items = loadMoreItems(
                    extractor,
                    extractor.initialPage,
                    perQueryLimit
                )

                found += items.filter { isShortCandidate(it) }
            } catch (_: Exception) {
                // A failed query should not break the entire Shorts feed.
            }
        }

        /*
         * Keep personalized/discovered order and remove duplicates.
         *
         * The first items are usually from the user's search interests,
         * followed by general trending/popular Shorts.
         */
        found
            .distinctBy { it.url }
            .take(maxItems)
    }

    /**
     * Current NewPipe version does not expose isShortFormContent().
     *
     * YouTube Shorts can currently be up to 3 minutes, so we use:
     *
     * 0 < duration <= 180 seconds
     *
     * Videos with unknown/invalid duration are excluded.
     */
    private fun isShortCandidate(video: VideoResult): Boolean {
        val duration = video.durationSeconds

        if (duration <= 0L) return false

        return duration <= 180L
    }

    /**
     * Trending videos.
     */
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

    /**
     * Related videos for the current video.
     */
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

    /**
     * Videos from a channel.
     */
    suspend fun getChannelVideos(
        channelUrl: String,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()

        val channelExtractor =
            ServiceList.YouTube.getChannelExtractor(channelUrl)

        channelExtractor.fetchPage()

        val videosTabHandler =
            channelExtractor.tabs.firstOrNull {
                it.contentFilters.contains(
                    org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS
                )
            } ?: return@withContext emptyList()

        val tabExtractor =
            ServiceList.YouTube.getChannelTabExtractor(
                videosTabHandler
            )

        tabExtractor.fetchPage()

        loadMoreItems(
            tabExtractor,
            tabExtractor.initialPage,
            maxItems
        )
    }

    /**
     * Channel information and first-page videos.
     */
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

        val videosTabHandler =
            channelExtractor.tabs.firstOrNull {
                it.contentFilters.contains(
                    org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS
                )
            }

        val videos =
            if (videosTabHandler != null) {
                val tabExtractor =
                    ServiceList.YouTube.getChannelTabExtractor(
                        videosTabHandler
                    )

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

    /**
     * Get playable video/audio streams.
     */
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
                !it.isVideoOnly &&
                    it.content != null
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

        val audioOptions =
            audioStreams
                .distinctBy { it.averageBitrate }
                .sortedByDescending {
                    it.averageBitrate
                }
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

        val subtitleOptions =
            try {
                info.subtitles
                    ?.filter {
                        it.url != null
                    }
                    ?.map { subtitle ->
                        val language =
                            subtitle.languageTag ?: "Subtitle"

                        val mimeType =
                            subtitle.format?.mimeType
                                ?: "text/vtt"

                        SubtitleOption(
                            label = language,
                            url = subtitle.url!!,
                            mimeType = mimeType
                        )
                    }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

        val channelAvatar =
            try {
                info.uploaderAvatars
                    ?.firstOrNull()
                    ?.url
            } catch (_: Exception) {
                null
            }

        val channelUrl =
            try {
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
