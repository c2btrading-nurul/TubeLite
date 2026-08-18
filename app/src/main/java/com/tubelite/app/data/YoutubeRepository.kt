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

data class QualityOption(
    val label: String,
    val progressiveUrl: String?,
    val videoOnlyUrl: String?,
    val audioOnlyUrl: String?,
    val hlsUrl: String?
)

data class PlayableStream(
    val title: String,
    val default: QualityOption,
    val options: List<QualityOption>,
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

    /** ট্রেন্ডিং ভিডিও — লাইভ/প্রিমিয়ার বাদ দেওয়া হয় কারণ সেগুলো প্রায়ই প্লে-ব্যাক ফেইল করে */
    suspend fun getTrending(): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val kioskList = ServiceList.YouTube.kioskList
        val extractor = kioskList.getDefaultKioskExtractor()
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            .filter { it.duration > 0 }
            .map { it.toVideoResult() }
    }

        /** সম্পর্কিত ভিডিও — বর্তমান ভিডিওর পেজ থেকেই আসে */
    suspend fun getRelated(videoUrl: String): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoResult() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPlayableStream(videoUrl: String): PlayableStream = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val thumb = info.thumbnails.firstOrNull()?.url

        val progressiveOptions = info.videoStreams
            ?.filter { !it.isVideoOnly && it.content != null }
            ?.sortedByDescending { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }
            ?.map { QualityOption(it.getResolution() ?: "Auto", it.content, null, null, null) }
            ?: emptyList()

        val bestAudio = info.audioStreams?.filter { it.content != null }?.maxByOrNull { it.averageBitrate }

        val videoOnlyOptions = if (bestAudio != null) {
            info.videoOnlyStreams
                ?.filter { it.content != null }
                ?.sortedByDescending { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }
                ?.map { QualityOption(it.getResolution() ?: "Auto", null, it.content, bestAudio.content, null) }
                ?: emptyList()
        } else emptyList()

        val hlsOptions = info.hlsUrl?.let { listOf(QualityOption("Auto (Live)", null, null, null, it)) } ?: emptyList()

        val allOptions = (progressiveOptions + videoOnlyOptions).distinctBy { it.label }.ifEmpty { hlsOptions }
        if (allOptions.isEmpty()) error("Could not get any stream")

        PlayableStream(info.name, allOptions.first(), allOptions, thumb)
    }
}
