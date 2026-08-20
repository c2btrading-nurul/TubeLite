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

    suspend fun search(query: String): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideoResult() }
    }

    suspend fun getTrending(): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val kioskList = ServiceList.YouTube.kioskList
        val extractor = kioskList.getDefaultKioskExtractor()
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            .filter { it.duration > 0 }
            .map { it.toVideoResult() }
    }

    suspend fun getRelated(videoUrl: String): List<VideoResult> = withContext(Dispatchers.IO) {
        ensureInit()
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoResult() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChannel(channelUrl: String): ChannelInfo = withContext(Dispatchers.IO) {
        ensureInit()
        val extractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
        extractor.fetchPage()
        val videos = extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideoResult() }
        val avatar = try { extractor.avatars?.firstOrNull()?.url } catch (e: Exception) { null }
        ChannelInfo(extractor.name ?: "", avatar, videos)
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

        val audioStreams = info.audioStreams?.filter { it.content != null } ?: emptyList()
        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }

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

        val audioOptions = audioStreams
            .distinctBy { it.averageBitrate }
            .sortedByDescending { it.averageBitrate }
            .mapIndexed { i, a ->
                val kbps = if (a.averageBitrate > 0) "${a.averageBitrate / 1000}kbps" else "Audio ${i + 1}"
                AudioOption(kbps, a.content)
            }

        val subtitleOptions = try {
            info.subtitles
                ?.filter { it.url != null }
                ?.map { s ->
                    val lang = s.languageTag ?: "Subtitle"
                    val mime = s.format?.mimeType ?: "text/vtt"
                    SubtitleOption(lang, s.url!!, mime)
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val channelAvatar = try { info.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null }
        val channelUrl = try { info.uploaderUrl } catch (e: Exception) { null }

        PlayableStream(info.name, allOptions.first(), allOptions, audioOptions, subtitleOptions, thumb, channelAvatar, channelUrl)
    }
}
