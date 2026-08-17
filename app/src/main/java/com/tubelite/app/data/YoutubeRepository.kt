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
    val videoStreamUrl: String,
    val isHls: Boolean,
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

    suspend fun search(query: String): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val youtube = ServiceList.YouTube
        val extractor = youtube.getSearchExtractor(query)
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map {
                VideoResult(
                    title = it.name ?: "",
                    url = it.url,
                    uploaderName = it.uploaderName ?: "",
                    thumbnailUrl = it.thumbnails.firstOrNull()?.url,
                    durationSeconds = it.duration
                )
            }
    }

    suspend fun getPlayableStream(videoUrl: String): PlayableStream = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

        val progressive = info.videoStreams
            ?.filter { !it.isVideoOnly }
            ?.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }

        if (progressive != null) {
            PlayableStream(
                title = info.name,
                videoStreamUrl = progressive.content,
                isHls = false,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url
            )
        } else {
            val hls = info.hlsUrl
            PlayableStream(
                title = info.name,
                videoStreamUrl = hls ?: error("No playable stream found"),
                isHls = true,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url
            )
        }
    }
}
