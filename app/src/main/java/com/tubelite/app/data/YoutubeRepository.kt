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

    /*
     * ------------------------------------------------------------
     * STREAM ITEM -> VIDEO RESULT
     * ------------------------------------------------------------
     */

    private fun StreamInfoItem.toVideoResult(): VideoResult {
        return VideoResult(
            title = name ?: "",
            url = url,
            uploaderName = uploaderName ?: "",
            thumbnailUrl = thumbnails.firstOrNull()?.url,
            durationSeconds = duration
        )
    }

    /*
     * ------------------------------------------------------------
     * LOAD MULTIPLE PAGES
     * ------------------------------------------------------------
     */

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

            try {
                page = extractor.getPage(next)
            } catch (_: Exception) {
                break
            }

            if (page.items.isEmpty()) {
                break
            }

            result += page.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoResult() }
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

        try {

            val extractor =
                ServiceList.YouTube.getSearchExtractor(
                    query
                )

            extractor.fetchPage()

            loadMoreItems(
                extractor = extractor,
                firstPage = extractor.initialPage,
                maxItems = maxItems
            )

        } catch (_: Exception) {
            emptyList()
        }
    }

    /*
     * ============================================================
     * SHORTS DETECTION
     * ============================================================
     *
     * NewPipe-এর বর্তমান version-এ
     * StreamInfoItem.isShortFormContent()
     * ব্যবহার করা হচ্ছে না।
     *
     * Shorts শনাক্ত করার জন্য:
     *
     * 1. /shorts/ URL
     * 2. title-এ Shorts signal
     * 3. duration <= 180 seconds
     *
     * গুরুত্বপূর্ণ:
     *
     * YouTube search result অনেক সময় /watch?v= URL
     * দেয়, যদিও ভিডিওটি Shorts।
     *
     * তাই শুধু /shorts/ URL-এর উপর নির্ভর করা যাবে না।
     * ============================================================
     */

    private fun isLikelyShort(
        video: VideoResult
    ): Boolean {

        val url =
            video.url
                .lowercase()

        val title =
            video.title
                .lowercase()

        val duration =
            video.durationSeconds

        /*
         * Invalid duration বাদ।
         *
         * NewPipe মাঝে মাঝে unknown duration-এর জন্য
         * 0 বা negative value দিতে পারে।
         */
        if (duration <= 0L) {
            return false
        }

        /*
         * Shorts-এর maximum duration এখানে 180 sec।
         */
        if (duration > 180L) {
            return false
        }

        /*
         * Explicit Shorts URL।
         */
        val explicitShortUrl =
            url.contains("/shorts/")

        /*
         * Title signals।
         */
        val shortSignal =
            title.contains("#shorts") ||
            title.contains("#short") ||
            title.contains("shorts") ||
            title.contains("short video")

        /*
         * গুরুত্বপূর্ণ:
         *
         * Search query নিজেই Shorts-focused হলে
         * title-এ "shorts" না থাকলেও short video
         * পাওয়া যেতে পারে।
         *
         * তাই 180 sec-এর মধ্যে থাকা ভিডিওকে
         * candidate হিসেবে গ্রহণ করা হচ্ছে।
         */
        return explicitShortUrl ||
                shortSignal ||
                duration in 1L..180L
    }

    /*
     * ============================================================
     * PERSONALIZED SHORTS
     * ============================================================
     *
     * User-এর recent search history থেকে interest নেওয়া হয়।
     *
     * তারপর:
     *
     *   interest + #shorts
     *   interest + shorts
     *
     * এবং generic:
     *
     *   #shorts
     *   trending shorts
     *   viral shorts
     *
     * search করা হয়।
     *
     * পর্যাপ্ত result না পেলে trending feed থেকেও
     * short-form candidates নেওয়া হয়।
     * ============================================================
     */

    suspend fun getPersonalizedShorts(
        context: Context,
        maxItems: Int = 30
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        /*
         * --------------------------------------------------------
         * SEARCH HISTORY
         * --------------------------------------------------------
         */

        val historyQueries =
            try {

                SearchHistoryStore.getRecent(
                    context,
                    8
                )

            } catch (_: Exception) {

                emptyList()
            }

        /*
         * --------------------------------------------------------
         * BUILD PERSONALIZED QUERIES
         * --------------------------------------------------------
         */

        val queries =
            buildList {

                /*
                 * User-এর recent interests
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
                add("trending shorts")
                add("viral shorts")

            }.distinct()

        /*
         * --------------------------------------------------------
         * COLLECT RESULTS
         * --------------------------------------------------------
         */

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

                /*
                 * Shorts filter
                 */
                found += candidates.filter {
                    isLikelyShort(it)
                }

            } catch (_: Exception) {

                /*
                 * একটি query fail করলেও
                 * পরের query চলবে।
                 */
                continue
            }
        }

        /*
         * --------------------------------------------------------
         * TRENDING FALLBACK
         * --------------------------------------------------------
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
                 * Search result থাকলে সেটাই ব্যবহার হবে।
                 */
            }
        }

        /*
         * --------------------------------------------------------
         * FINAL RESULT
         * --------------------------------------------------------
         */

        return@withContext found
            .filter {
                it.durationSeconds in 1L..180L
            }
            .distinctBy {
                it.url
            }
            .take(maxItems)
    }

    /*
     * ============================================================
     * GENERIC SHORTS
     * ============================================================
     *
     * পুরোনো caller-এর compatibility রাখার জন্য।
     *
     * এখানে getPersonalizedShorts() duplicate করা হয়নি।
     * ============================================================
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
                "viral shorts"
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

                continue
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
            .filter {
                it.durationSeconds in 1L..180L
            }
            .distinctBy {
                it.url
            }
            .take(maxItems)
    }

    /*
     * ============================================================
     * TRENDING
     * ============================================================
     */

    suspend fun getTrending(
        maxItems: Int = 40
    ): List<VideoResult> = withContext(Dispatchers.IO) {

        ensureInit()

        if (maxItems <= 0) {
            return@withContext emptyList()
        }

        try {

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
                it.durationSeconds > 0L
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    /*
     * ============================================================
     * RELATED VIDEOS
     * ============================================================
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
                    .distinctBy {
                        it.url
                    }
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

                (
                    directRelated +
                        extra
                    )
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
     * ============================================================
     * CHANNEL VIDEOS
     * ============================================================
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

        try {

            val channelExtractor =
                ServiceList.YouTube
                    .getChannelExtractor(
                        channelUrl
                    )

            channelExtractor.fetchPage()

            val videosTabHandler =
                channelExtractor.tabs.firstOrNull {

                    it.contentFilters.contains(
                        org.schabi.newpipe
                            .extractor
                            .channel
                            .tabs
                            .ChannelTabs
                            .VIDEOS
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

        } catch (_: Exception) {

            emptyList()
        }
    }

    /*
     * ============================================================
     * CHANNEL INFO
     * ============================================================
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

        try {

            val channelExtractor =
                ServiceList.YouTube
                    .getChannelExtractor(
                        channelUrl
                    )

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
                        org.schabi.newpipe
                            .extractor
                            .channel
                            .tabs
                            .ChannelTabs
                            .VIDEOS
                    )
                }

            val videos =
                if (videosTabHandler != null) {

                    try {

                        val tabExtractor =
                            ServiceList.YouTube
                                .getChannelTabExtractor(
                                    videosTabHandler
                                )

                        tabExtractor.fetchPage()

                        tabExtractor
                            .initialPage
                            .items
                            .filterIsInstance<StreamInfoItem>()
                            .map {
                                it.toVideoResult()
                            }

                    } catch (_: Exception) {

                        emptyList()
                    }

                } else {

                    emptyList()
                }

            ChannelInfo(
                name = name,
                avatarUrl = avatar,
                videos = videos
            )

        } catch (_: Exception) {

            ChannelInfo(
                name = "",
                avatarUrl = null,
                videos = emptyList()
            )
        }
    }

    /*
     * ============================================================
     * PLAYABLE STREAM
     * ============================================================
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

        /*
         * --------------------------------------------------------
         * THUMBNAIL
         * --------------------------------------------------------
         */

        val thumb =
            info.thumbnails
                .firstOrNull()
                ?.url

        /*
         * --------------------------------------------------------
         * PROGRESSIVE VIDEO
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * AUDIO
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * VIDEO ONLY + BEST AUDIO
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * HLS
         * --------------------------------------------------------
         */

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

        /*
         * --------------------------------------------------------
         * ALL VIDEO OPTIONS
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * AUDIO OPTIONS
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * SUBTITLES
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * CHANNEL AVATAR
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * CHANNEL URL
         * --------------------------------------------------------
         */

        val channelUrl =
            try {

                info.uploaderUrl

            } catch (_: Exception) {

                null
            }

        /*
         * --------------------------------------------------------
         * FINAL PLAYABLE STREAM
         * --------------------------------------------------------
         */

        PlayableStream(
            title = info.name,

            default =
                allOptions.first(),

            options =
                allOptions,

            audioOptions =
                audioOptions,

            subtitleOptions =
                subtitleOptions,

            thumbnailUrl =
                thumb,

            channelAvatarUrl =
                channelAvatar,

            channelUrl =
                channelUrl
        )
    }
}
