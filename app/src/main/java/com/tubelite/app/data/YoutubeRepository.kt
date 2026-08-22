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

            page = extractor.getPage(next)

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

        val extractor =
            ServiceList.YouTube.getSearchExtractor(query)

        extractor.fetchPage()

        loadMoreItems(
            extractor,
            extractor.initialPage,
            maxItems
        )
    }

    /*
     * NewPipe Extractor-এর বর্তমান version-এ
     * StreamInfoItem.isShortFormContent() ব্যবহার করা যাবে না।
     *
     * তাই এখানে Shorts শনাক্ত করার জন্য:
     *
     * 1. /shorts/ URL
     * 2. title-এ shorts/#shorts signal
     * 3. সর্বোচ্চ 180 সেকেন্ড duration
     *
     * ব্যবহার করা হচ্ছে।
     */
    private fun isLikelyShort(
        video: VideoResult,
        allowUnknownDuration: Boolean = false
    ): Boolean {

        val url = video.url.lowercase()
        val title = video.title.lowercase()
        val duration = video.durationSeconds

        /*
         * NewPipe search results do not always expose the duration.
         * A value <= 0 therefore means "unknown", not "not a Short".
         */
        if (duration > 180L) {
            return false
        }

        val explicitShortUrl =
            url.contains("/shorts/") ||
            url.contains("youtube.com/shorts/") ||
            url.contains("youtu.be/shorts/")

        if (explicitShortUrl) {
            return true
        }

        val shortSignals = listOf(
            "#shorts",
            "#short",
            "shorts",
            "short video",
            "youtube short"
        )

        if (shortSignals.any { title.contains(it) }) {
            return true
        }

        /*
         * A known duration of 1..180 seconds is a valid Short candidate.
         */
        if (duration in 1L..180L) {
            return true
        }

        /*
         * For queries that explicitly ask YouTube for Shorts, the search
         * result may have an unknown duration. Keep it instead of returning
         * an empty feed. The caller still limits the amount of data.
         */
        return allowUnknownDuration && duration <= 0L
    }

    /*
     * Personalized Shorts feed.
     *
     * User-এর Search History থেকে interest নেওয়া হয়।
     * তারপর সেই interest অনুযায়ী Shorts search করা হয়।
     *
     * এরপর generic/trending Shorts search করা হয়
     * যাতে নতুন ও জনপ্রিয় Shorts-ও feed-এ আসে।
     */
    suspend fun getPersonalizedShorts(
        context: Context,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        val historyQueries = try {
            SearchHistoryStore.getRecent(
                context,
                8
            )
        } catch (_: Exception) {
            emptyList()
        }

        val queries = buildList {

            /*
             * User-এর recent search থেকে personalized queries
             */
            historyQueries.forEach { query ->

                val q = query.trim()

                if (q.isNotEmpty()) {
                    add("$q #shorts")
                    add("$q shorts")
                }
            }

            /*
             * Generic Shorts discovery
             */
            add("#shorts")
            add("trending shorts")
            add("viral shorts")
        }.distinct()

        val found = mutableListOf<VideoResult>()

        for (query in queries) {

            if (found.distinctBy { it.url }.size >= maxItems) {
                break
            }

            try {

                val extractor =
                    ServiceList.YouTube.getSearchExtractor(query)

                extractor.fetchPage()

                val candidates = loadMoreItems(
                    extractor = extractor,
                    firstPage = extractor.initialPage,
                    maxItems = minOf(
                        40,
                        maxItems * 2
                    )
                )

                found += candidates.filter {
                    isLikelyShort(
                        video = it,
                        allowUnknownDuration = true
                    )
                }

            } catch (_: Exception) {
                /*
                 * একটি query fail করলে পরের query চলবে।
                 */
            }
        }

        /*
         * Search থেকে পর্যাপ্ত Shorts না পেলে
         * YouTube trending feed থেকেও Shorts নেওয়া হবে।
         */
        if (found.distinctBy { it.url }.size < maxItems) {

            try {

                val trending = getTrending(
                    maxItems = minOf(
                        80,
                        maxItems * 3
                    )
                ).filter {
                    isLikelyShort(it)
                }

                found += trending

            } catch (_: Exception) {
                /*
                 * Search result থাকলে সেটাই ব্যবহার করা হবে।
                 */
            }
        }

        return@withContext found
            .filter {
                it.durationSeconds <= 0L ||
                        it.durationSeconds in 1L..180L
            }
            .distinctBy {
                it.url
            }
            .take(maxItems)
    }

    /*
     * পুরোনো caller-এর compatibility রাখার জন্য
     * getShorts() রাখা হয়েছে।
     *
     * এখানে isShortFormContent() নেই।
     */
    suspend fun getShorts(
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        val queries = listOf(
            "#shorts",
            "shorts",
            "trending shorts",
            "viral shorts"
        )

        val found = mutableListOf<VideoResult>()

        for (query in queries) {

            if (found.distinctBy { it.url }.size >= maxItems) {
                break
            }

            try {

                val extractor =
                    ServiceList.YouTube.getSearchExtractor(query)

                extractor.fetchPage()

                val items = loadMoreItems(
                    extractor = extractor,
                    firstPage = extractor.initialPage,
                    maxItems = minOf(
                        40,
                        maxItems * 2
                    )
                )

                found += items.filter {
                    isLikelyShort(
                        video = it,
                        allowUnknownDuration = true
                    )
                }

            } catch (_: Exception) {
                /*
                 * পরের query চেষ্টা করা হবে।
                 */
            }
        }

        return@withContext found
            .distinctBy {
                it.url
            }
            .take(maxItems)
    }

    suspend fun getTrending(
        maxItems: Int = 40
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        val kioskList =
            ServiceList.YouTube.kioskList

        val extractor =
            kioskList.getDefaultKioskExtractor()

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

            val info =
                StreamInfo.getInfo(
                    ServiceList.YouTube,
                    videoUrl
                )

            val directRelated =
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map {
                        it.toVideoResult()
                    }

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
                    .filter {
                        it.url != videoUrl
                    }
                    .distinctBy {
                        it.url
                    }
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
            ServiceList.YouTube.getChannelExtractor(
                channelUrl
            )

        channelExtractor.fetchPage()

        val videosTabHandler =
            channelExtractor.tabs.firstOrNull {

                it.contentFilters.contains(
                    org.schabi.newpipe.extractor
                        .channel.tabs.ChannelTabs.VIDEOS
                )
            }
                ?: return@withContext emptyList()

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

    suspend fun getChannel(
        channelUrl: String
    ): ChannelInfo = withContext(Dispatchers.IO) {

        ensureInit()

        val channelExtractor =
            ServiceList.YouTube.getChannelExtractor(
                channelUrl
            )

        channelExtractor.fetchPage()

        val name =
            channelExtractor.name ?: ""

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
                    org.schabi.newpipe.extractor
                        .channel.tabs.ChannelTabs.VIDEOS
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
                    .map {
                        it.toVideoResult()
                    }

            } else {
                emptyList()
            }

        ChannelInfo(
            name,
            avatar,
            videos
        )
    }

    suspend fun getPlayableStream(
        videoUrl: String
    ): PlayableStream = withContext(Dispatchers.IO) {

        ensureInit()

        val info =
            StreamInfo.getInfo(
                ServiceList.YouTube,
                videoUrl
            )

        val thumb =
            info.thumbnails
                .firstOrNull()
                ?.url

        val progressiveOptions =
            info.videoStreams
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
                        it.getResolution() ?: "Auto",
                        it.content,
                        null,
                        null,
                        null
                    )
                }
                ?: emptyList()

        val audioStreams =
            info.audioStreams
                ?.filter {
                    it.content != null
                }
                ?: emptyList()

        val bestAudio =
            audioStreams.maxByOrNull {
                it.averageBitrate
            }

        val videoOnlyOptions =
            if (bestAudio != null) {

                info.videoOnlyStreams
                    ?.filter {
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
                            it.getResolution() ?: "Auto",
                            null,
                            it.content,
                            bestAudio.content,
                            null
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
                        "Auto (Live)",
                        null,
                        null,
                        null,
                        it
                    )
                )

            } ?: emptyList()

        val allOptions =
            (progressiveOptions + videoOnlyOptions)
                .distinctBy {
                    it.label
                }
                .ifEmpty {
                    hlsOptions
                }

        if (allOptions.isEmpty()) {
            error("Could not get any stream")
        }

        val audioOptions =
            audioStreams
                .distinctBy {
                    it.averageBitrate
                }
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
                        kbps,
                        audio.content
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
                            subtitle.languageTag
                                ?: "Subtitle"

                        val mime =
                            subtitle.format?.mimeType
                                ?: "text/vtt"

                        SubtitleOption(
                            language,
                            subtitle.url!!,
                            mime
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
