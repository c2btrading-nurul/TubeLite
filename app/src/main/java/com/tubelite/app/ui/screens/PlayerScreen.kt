package com.tubelite.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tubelite.app.data.AudioOption
import com.tubelite.app.data.PlayableStream
import com.tubelite.app.data.QualityOption
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.download.DownloadHelper
import com.tubelite.app.playback.TubeMediaSourceFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP_OPTIONS = listOf(0, 5, 10, 15, 30, 60)
private const val SEEK_STEP_MS = 10_000L

private fun buildMediaItem(videoUrl: String, title: String, q: QualityOption): MediaItem {
    val bundle = Bundle()
    q.progressiveUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_PROGRESSIVE, it) }
    q.videoOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_VIDEO_ONLY, it) }
    q.audioOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_AUDIO_ONLY, it) }
    q.hlsUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_HLS, it) }
    val fallbackUri = q.progressiveUrl ?: q.hlsUrl ?: q.videoOnlyUrl ?: "about:blank"
    return MediaItem.Builder()
        .setMediaId(videoUrl)
        .setUri(fallbackUri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(bundle).build())
        .build()
}

@Composable
fun PlayerScreen(
    video: VideoResult,
    controller: MediaController?,
    autoPlayEnabled: Boolean,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onRelatedSelected: (VideoResult) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var streamTitle by remember { mutableStateOf(video.title) }
    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var audioOptions by remember { mutableStateOf<List<AudioOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<QualityOption?>(null) }
    var selectedAudioUrl by remember { mutableStateOf<String?>(null) }
    var selectedSpeed by remember { mutableFloatStateOf(1f) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var related by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var zoomFill by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    fun effectiveQuality(q: QualityOption): QualityOption =
        if (q.videoOnlyUrl != null && selectedAudioUrl != null) q.copy(audioOnlyUrl = selectedAudioUrl) else q

    LaunchedEffect(video.url, controller) {
        if (controller == null) return@LaunchedEffect

        val alreadyLoaded = controller.currentMediaItem?.mediaId == video.url &&
            controller.playbackState != Player.STATE_IDLE

        if (alreadyLoaded) {
            streamTitle = controller.currentMediaItem?.mediaMetadata?.title?.toString() ?: video.title
            loading = false
            try {
                val playable = YoutubeRepository.getPlayableStream(video.url)
                qualities = playable.options
                audioOptions = playable.audioOptions
                selectedQuality = playable.options.firstOrNull { it.label == selectedQuality?.label } ?: playable.default
            } catch (_: Exception) { }
            related = YoutubeRepository.getRelated(video.url)
            return@LaunchedEffect
        }

        loading = true
        error = null
        selectedAudioUrl = null
        try {
            val playable: PlayableStream = YoutubeRepository.getPlayableStream(video.url)
            streamTitle = playable.title
            qualities = playable.options
            audioOptions = playable.audioOptions
            selectedQuality = playable.default

            controller.setMediaItem(buildMediaItem(video.url, playable.title, playable.default))
            controller.prepare()
            controller.playWhenReady = autoPlayEnabled
            controller.setPlaybackParameters(PlaybackParameters(1f))
            selectedSpeed = 1f
        } catch (e: Exception) {
            error = "স্ট্রিম লোড করা যায়নি: ${e.message}"
        } finally {
            loading = false
        }

        related = YoutubeRepository.getRelated(video.url)
    }

    DisposableEffect(controller, related, autoPlayEnabled) {
        if (controller == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && autoPlayEnabled) {
                    related.firstOrNull()?.let {
