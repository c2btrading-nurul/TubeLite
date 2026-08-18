package com.tubelite.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class PlayableStream(
    val title: String,
    val progressiveUrl: String?,
    val videoOnlyUrl: String?,
    val audioOnlyUrl: String?,
    val hlsUrl: String?,
    val thumbnailUrl: String?
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

    suspend fun search(query: String): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideoResult() }
    }

    /** YouTube-এর হোম ফিডের মতো ট্রেন্ডিং ভিডিওর তালিকা */
    suspend fun getTrending(): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val kioskList = ServiceList.YouTube.kioskList
        val extractor = kioskList.getDefaultKioskExtractor()
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideoResult() }
    }

    /**
     * Extracts a playable, ad-free stream. Tries progressive (video+audio combined) first;
     * if YouTube only offers separate DASH video-only/audio-only tracks (very common today),
     * returns both so the player can merge them; falls back to HLS as a last resort.
     */
    suspend fun getPlayableStream(videoUrl: String): PlayableStream = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val thumb = info.thumbnails.firstOrNull()?.url

        val progressive = info.videoStreams
            ?.filter { !it.isVideoOnly && it.content != null }
            ?.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }

        if (progressive != null) {
            return@withContext PlayableStream(info.name, progressive.content, null, null, null, thumb)
        }

        val bestVideoOnly = info.videoOnlyStreams
            ?.filter { it.content != null }
            ?.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }
        val bestAudio = info.audioStreams
            ?.filter { it.content != null }
            ?.maxByOrNull { it.averageBitrate }

        if (bestVideoOnly != null && bestAudio != null) {
            return@withContext PlayableStream(
                info.name, null, bestVideoOnly.content, bestAudio.content, null, thumb
            )
        }

        val hls = info.hlsUrl
        if (hls != null) {
            return@withContext PlayableStream(info.name, null, null, null, hls, thumb)
        }

        error("Could not get any stream")
    }
}
