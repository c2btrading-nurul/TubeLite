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

        if (maxItems <= 0) {
            return emptyList()
        }

        val result = firstPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideoResult() }
            .toMutableList()

        var page = firstPage

        while (result.size < maxItems) {

            val next = page.nextPage ?: break

            page = extractor.getPage(next)

            val newItems = page.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoResult() }

            if (newItems.isEmpty()) {
                break
            }

            result += newItems
        }

        return result
            .distinctBy { it.url }
            .take(maxItems)
    }

    /*
     * ------------------------------------------------------------
     * SEARCH
     * ------------------------------------------------------------
     */

    suspend fun search(
        query: String,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (query.isBlank() || maxItems <= 0) {
            return@withContext emptyList()
        }

        val extractor =
            ServiceList.YouTube.getSearchExtractor(query)

        extractor.fetchPage()

        loadMoreItems(
            extractor = extractor,
            firstPage = extractor.initialPage,
            maxItems = maxItems
        )
    }

    /*
     * ------------------------------------------------------------
     * SHORTS DETECTION
     * ------------------------------------------------------------
     *
     * NewPipe Extractor-এর নতুন version-এ
     * StreamInfoItem.isShortFormContent() ব্যবহার করা হচ্ছে না।
     *
     * Shorts শনাক্ত করার জন্য:
     *
     * 1. /shorts/ URL
     * 2. title-এর মধ্যে #shorts / shorts signal
     * 3. duration 180 sec বা তার কম
     *
     * গুরুত্বপূর্ণ:
     * Search result-এ কখনো duration = 0 আসতে পারে।
     * সেক্ষেত্রে URL/title-এ Shorts signal থাকলে
     * সেটাকে reject করা হবে না।
     * ------------------------------------------------------------
     */

    private fun isLikelyShort(
        video: VideoResult
    ): Boolean {

        val url =
            video.url
                .trim()
                .lowercase()

        val title =
            video.title
                .trim()
                .lowercase()

        val explicitShortUrl =
            url.contains("/shorts/") ||
            url.contains("youtube.com/shorts/") ||
            url.contains("youtu.be/shorts/")

        val shortSignals =
            listOf(
                "#shorts",
                "#short",
                "shorts",
                "short video"
            )

        val hasShortSignal =
            explicitShortUrl ||
            shortSignals.any { signal ->
                title.contains(signal)
            }

        /*
         * Duration না পাওয়া গেলে শুধু explicit Shorts signal
         * থাকলে video-টি allow করা হবে।
         */
        if (video.durationSeconds <= 0L) {
            return hasShortSignal
        }

        /*
         * Duration 180 sec-এর বেশি হলে Shorts হিসেবে নেওয়া হবে না।
         */
        if (video.durationSeconds > 180L) {
            return false
        }

        /*
         * Duration 1..180 sec হলে Shorts discovery query-এর
         * candidate হিসেবে allow করা হবে।
         */
        return true
    }

    /*
     * ------------------------------------------------------------
     * PERSONALIZED SHORTS
     * ------------------------------------------------------------
     *
     * Search History থেকে personalized Shorts query তৈরি করে।
     * এরপর generic Shorts এবং trending Shorts ব্যবহার করে।
     * ------------------------------------------------------------
     */

    suspend fun getPersonalizedShorts(
        context: Context,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        val historyQueries =
            try {
                SearchHistoryStore.getRecent(
                    context,
                    8
                )
            } catch (_: Exception) {
                emptyList()
            }

        val queries =
            buildList {

                /*
                 * Recent search history
                 */
                historyQueries.forEach { query ->

                    val q =
                        query
                            .trim()

                    if (q.isNotEmpty()) {

                        add("$q #shorts")
                        add("$q shorts")
                    }
                }

                /*
                 * Generic Shorts discovery
                 */
                add("#shorts")
                add("shorts")
                add("trending shorts")
                add("viral shorts")
                add("short video")
            }
                .distinct()

        val found =
            mutableListOf<VideoResult>()

        for (query in queries) {

            if (
                found
                    .distinctBy { it.url }
                    .size >= maxItems
            ) {
                break
            }

            try {

                val extractor =
                    ServiceList.YouTube
                        .getSearchExtractor(query)

                extractor.fetchPage()

                val candidates =
                    loadMoreItems(
                        extractor = extractor,
                        firstPage = extractor.initialPage,
                        maxItems = minOf(
                            50,
                            maxItems * 3
                        )
                    )

                found +=
                    candidates.filter {
                        isLikelyShort(it)
                    }

            } catch (_: Exception) {
                /*
                 * একটি query fail করলে
                 * পরের query চলবে।
                 */
            }
        }

        /*
         * Search থেকে পর্যাপ্ত Shorts না পেলে
         * trending feed থেকেও candidate নেওয়া হবে।
         */
        if (
            found
                .distinctBy { it.url }
                .size < maxItems
        ) {

            try {

                val trending =
                    getTrending(
                        maxItems = minOf(
                            100,
                            maxItems * 4
                        )
                    )

                found +=
                    trending.filter {
                        isLikelyShort(it)
                    }

            } catch (_: Exception) {
                /*
                 * Search result থাকলে সেটাই ব্যবহার করা হবে।
                 */
            }
        }

        /*
         * Final cleanup
         */
        return@withContext found
            .filter { video ->

                /*
                 * Duration জানা থাকলে 180 sec-এর মধ্যে হতে হবে।
                 *
                 * duration = 0 হলে URL/title signal-এর উপর
                 * isLikelyShort() already সিদ্ধান্ত নিয়েছে।
                 */
                video.durationSeconds <= 0L ||
                    video.durationSeconds <= 180L
            }
            .distinctBy { it.url }
            .take(maxItems)
    }

    /*
     * ------------------------------------------------------------
     * OLD SHORTS API
     * ------------------------------------------------------------
     *
     * পুরোনো caller থাকলে যেন ভেঙে না যায়।
     * ------------------------------------------------------------
     */

    suspend fun getShorts(
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        val queries =
            listOf(
                "#shorts",
                "shorts",
                "trending shorts",
                "viral shorts",
                "short video"
            )

        val found =
            mutableListOf<VideoResult>()

        for (query in queries) {

            if (
                found
                    .distinctBy { it.url }
                    .size >= maxItems
            ) {
                break
            }

            try {

                val extractor =
                    ServiceList.YouTube
                        .getSearchExtractor(query)

                extractor.fetchPage()

                val items =
                    loadMoreItems(
                        extractor = extractor,
                        firstPage = extractor.initialPage,
                        maxItems = minOf(
                            50,
                            maxItems * 3
                        )
                    )

                found +=
                    items.filter {
                        isLikelyShort(it)
                    }

            } catch (_: Exception) {
                /*
                 * পরের query চেষ্টা করা হবে।
                 */
            }
        }

        /*
         * Trending fallback
         */
        if (
            found
                .distinctBy { it.url }
                .size < maxItems
        ) {

            try {

                found +=
                    getTrending(
                        maxItems = minOf(
                            100,
                            maxItems * 4
                        )
                    ).filter {
                        isLikelyShort(it)
                    }

            } catch (_: Exception) {
                // Ignore
            }
        }

        return@withContext found
            .filter { video ->
                video.durationSeconds <= 0L ||
                    video.durationSeconds <= 180L
            }
            .distinctBy { it.url }
            .take(maxItems)
    }

    /*
     * ------------------------------------------------------------
     * TRENDING
     * ------------------------------------------------------------
     */

    suspend fun getTrending(
        maxItems: Int = 40
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        val kioskList =
            ServiceList.YouTube.kioskList

        val extractor =
            kioskList.getDefaultKioskExtractor()

        extractor.fetchPage()

        loadMoreItems(
            extractor = extractor,
            firstPage = extractor.initialPage,
            maxItems = maxItems
        ).filter {
            it.durationSeconds > 0
        }
    }

    /*
     * ------------------------------------------------------------
     * RELATED VIDEOS
     * ------------------------------------------------------------
     */

    suspend fun getRelated(
        videoUrl: String
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (videoUrl.isBlank()) {
            return@withContext emptyList()
        }

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

                val extra =
                    try {

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

    /*
     * ------------------------------------------------------------
     * CHANNEL VIDEOS
     * ------------------------------------------------------------
     */

    suspend fun getChannelVideos(
        channelUrl: String,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (
            channelUrl.isBlank() ||
            maxItems <= 0
        ) {
            return@withContext emptyList()
        }

        val channelExtractor =
            ServiceList.YouTube
                .getChannelExtractor(channelUrl)

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
            ServiceList.YouTube
                .getChannelTabExtractor(
                    videosTabHandler
                )

        tabExtractor.fetchPage()

        loadMoreItems(
            extractor = tabExtractor,
            firstPage = tabExtractor.initialPage,
            maxItems = maxItems
        )
    }

    /*
     * ------------------------------------------------------------
     * CHANNEL INFO
     * ------------------------------------------------------------
     */

    suspend fun getChannel(
        channelUrl: String
    ): ChannelInfo = withContext(Dispatchers.IO) {

        ensureInit()

        if (channelUrl.isBlank()) {
            return@withContext ChannelInfo(
                name = "",
                avatarUrl = null,
                videos = emptyList()
            )
        }

        val channelExtractor =
            ServiceList.YouTube
                .getChannelExtractor(channelUrl)

        channelExtractor.fetchPage()

        val name =
            channelExtractor.name ?: ""

        val avatar =
            try {

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
                    ServiceList.YouTube
                        .getChannelTabExtractor(
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
            name = name,
            avatarUrl = avatar,
            videos = videos
        )
    }

    /*
     * ------------------------------------------------------------
     * PLAYABLE STREAM
     * ------------------------------------------------------------
     */

    suspend fun getPlayableStream(
        videoUrl: String
    ): PlayableStream = withContext(Dispatchers.IO) {

        ensureInit()

        if (videoUrl.isBlank()) {
            error("Video URL is empty")
        }

        val info =
            StreamInfo.getInfo(
                ServiceList.YouTube,
                videoUrl
            )

        val thumb =
            info.thumbnails
                .firstOrNull()
                ?.url

        /*
         * Progressive video + audio
         */
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
                        label =
                            it.getResolution()
                                ?: "Auto",

                        progressiveUrl =
                            it.content,

                        videoOnlyUrl = null,
                        audioOnlyUrl = null,
                        hlsUrl = null
                    )
                }
                ?: emptyList()

        /*
         * Audio streams
         */
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

        /*
         * Video-only + best audio
         */
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
                            label =
                                it.getResolution()
                                    ?: "Auto",

                            progressiveUrl = null,

                            videoOnlyUrl =
                                it.content,

                            audioOnlyUrl =
                                bestAudio.content,

                            hlsUrl = null
                        )
                    }
                    ?: emptyList()

            } else {

                emptyList()
            }

        /*
         * HLS
         */
        val hlsOptions =
            info.hlsUrl?.let { hls ->

                listOf(
                    QualityOption(
                        label = "Auto (Live)",
                        progressiveUrl = null,
                        videoOnlyUrl = null,
                        audioOnlyUrl = null,
                        hlsUrl = hls
                    )
                )

            } ?: emptyList()

        /*
         * All video options
         */
        val allOptions =
            (
                progressiveOptions +
                    videoOnlyOptions
                )
                .distinctBy {
                    it.label
                }
                .ifEmpty {
                    hlsOptions
                }

        if (allOptions.isEmpty()) {
            error("Could not get any stream")
        }

        /*
         * Audio options
         */
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
                        if (
                            audio.averageBitrate > 0
                        ) {

                            "${audio.averageBitrate / 1000}kbps"

                        } else {

                            "Audio ${index + 1}"
                        }

                    AudioOption(
                        label = kbps,
                        url = audio.content
                    )
                }

        /*
         * Subtitle options
         */
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
                            subtitle.format
                                ?.mimeType
                                ?: "text/vtt"

                        SubtitleOption(
                            label = language,
                            url = subtitle.url!!,
                            mimeType = mime
                        )
                    }
                    ?: emptyList()

            } catch (_: Exception) {

                emptyList()
            }

        /*
         * Channel avatar
         */
        val channelAvatar =
            try {

                info.uploaderAvatars
                    ?.firstOrNull()
                    ?.url

            } catch (_: Exception) {

                null
            }

        /*
         * Channel URL
         */
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
