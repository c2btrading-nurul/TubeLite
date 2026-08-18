package com.tubelite.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tubelite.app.data.PlayableStream
import com.tubelite.app.data.QualityOption
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.download.DownloadHelper
import com.tubelite.app.playback.TubeMediaSourceFactory
import kotlinx.coroutines.delay

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP_TIMER_OPTIONS = listOf(15, 30, 45, 60) // মিনিট

// video-এর mediaId/artist/artwork সেট করা থাকে যাতে ব্যাকগ্রাউন্ড থেকে ফিরে এলে
// MediaController.currentMediaItem থেকে চলমান ভিডিওটা আবার চেনা যায়।
private fun buildMediaItem(title: String, q: QualityOption, video: VideoResult): MediaItem {
    val bundle = Bundle()
    q.progressiveUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_PROGRESSIVE, it) }
    q.videoOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_VIDEO_ONLY, it) }
    q.audioOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_AUDIO_ONLY, it) }
    q.hlsUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_HLS, it) }
    val fallbackUri = q.progressiveUrl ?: q.hlsUrl ?: q.videoOnlyUrl ?: "about:blank"
    return MediaItem.Builder()
        .setMediaId(video.url)
        .setUri(fallbackUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(video.uploaderName)
                .setArtworkUri(runCatching { Uri.parse(video.thumbnailUrl) }.getOrNull())
                .build()
        )
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(bundle).build())
        .build()
}

// শুধু অডিও চালানোর জন্য (ডেটা বাঁচাতে / স্ক্রিন বন্ধ রেখে শোনার জন্য)
private fun buildAudioOnlyMediaItem(title: String, q: QualityOption, video: VideoResult): MediaItem {
    val audioUrl = q.audioOnlyUrl ?: q.progressiveUrl ?: "about:blank"
    val bundle = Bundle().apply { putString(TubeMediaSourceFactory.KEY_AUDIO_ONLY, audioUrl) }
    return MediaItem.Builder()
        .setMediaId(video.url)
        .setUri(audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(video.uploaderName)
                .setArtworkUri(runCatching { Uri.parse(video.thumbnailUrl) }.getOrNull())
                .build()
        )
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

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var streamTitle by remember { mutableStateOf(video.title) }
    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<QualityOption?>(null) }
    var selectedSpeed by remember { mutableFloatStateOf(1f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var related by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var captionsEnabled by remember { mutableStateOf(false) }
    var audioOnlyMode by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(video.url, controller) {
        if (controller == null) return@LaunchedEffect
        error = null
        sleepTimerMinutes = null

        // মিনি-প্লেয়ার থেকে আবার এক্সপ্যান্ড করলে PlayerScreen নতুন করে composition-এ বসে,
        // ফলে এই LaunchedEffect আবার রান হয়। কিন্তু controller-এ যদি এই ভিডিওটাই ইতিমধ্যে
        // চলমান থাকে (একই mediaId), তাহলে আর setMediaItem/prepare কল করা যাবে না —
        // নাহলে প্লেব্যাক পজিশন ০ থেকে রিসেট হয়ে যায়।
        val alreadyLoaded = controller.currentMediaItem?.mediaId == video.url &&
            controller.playbackState != Player.STATE_IDLE

        loading = !alreadyLoaded
        if (!alreadyLoaded) {
            captionsEnabled = false
            audioOnlyMode = false
        }

        try {
            val playable: PlayableStream = YoutubeRepository.getPlayableStream(video.url)
            streamTitle = playable.title
            qualities = playable.options
            selectedQuality = playable.default

            if (alreadyLoaded) {
                // শুধু UI স্টেট সিঙ্ক করা হচ্ছে; প্লেয়ারকে স্পর্শ করা হচ্ছে না যাতে চলমান পজিশন/অবস্থা বজায় থাকে
                selectedSpeed = controller.playbackParameters.speed
            } else {
                selectedSpeed = 1f
                controller.setMediaItem(buildMediaItem(playable.title, playable.default, video))
                controller.prepare()
                controller.playWhenReady = autoPlayEnabled
                controller.setPlaybackParameters(PlaybackParameters(1f))
            }
        } catch (e: Exception) {
            error = "স্ট্রিম লোড করা যায়নি: ${e.message}"
        } finally {
            loading = false
        }

        related = YoutubeRepository.getRelated(video.url)
    }

    // স্লিপ টাইমার: সময় হলে অটো-পজ করে দেয়। sleepTimerMinutes বদলালে আগেরটা বাতিল হয়ে নতুন করে শুরু হয়।
    LaunchedEffect(sleepTimerMinutes) {
        val minutes = sleepTimerMinutes
        if (minutes != null) {
            delay(minutes * 60_000L)
            controller?.pause()
            sleepTimerMinutes = null
        }
    }

    DisposableEffect(controller, related, autoPlayEnabled) {
        if (controller == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && autoPlayEnabled) {
                    related.firstOrNull()?.let { onRelatedSelected(it) }
                }
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {}
    }

    fun switchQuality(q: QualityOption) {
        if (controller == null || q == selectedQuality) return
        val pos = controller.currentPosition
        val wasPlaying = controller.isPlaying
        selectedQuality = q
        val item = if (audioOnlyMode) buildAudioOnlyMediaItem(streamTitle, q, video)
                   else buildMediaItem(streamTitle, q, video)
        controller.setMediaItem(item)
        controller.prepare()
        controller.seekTo(pos)
        controller.playWhenReady = wasPlaying
        controller.setPlaybackParameters(PlaybackParameters(selectedSpeed))
    }

    fun switchSpeed(speed: Float) {
        selectedSpeed = speed
        controller?.setPlaybackParameters(PlaybackParameters(speed))
    }

    fun toggleAudioOnly(enabled: Boolean) {
        val c = controller ?: return
        val q = selectedQuality ?: qualities.firstOrNull() ?: return
        val pos = c.currentPosition
        val wasPlaying = c.isPlaying
        audioOnlyMode = enabled
        val item = if (enabled) buildAudioOnlyMediaItem(streamTitle, q, video)
                   else buildMediaItem(streamTitle, q, video)
        c.setMediaItem(item)
        c.prepare()
        c.seekTo(pos)
        c.playWhenReady = wasPlaying
        c.setPlaybackParameters(PlaybackParameters(selectedSpeed))
    }

    fun toggleCaptions(enabled: Boolean) {
        val c = controller ?: return
        val hasText = c.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        if (enabled && !hasText) {
            Toast.makeText(context, "এই ভিডিওতে সাবটাইটেল পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
        c.trackSelectionParameters = c.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
        captionsEnabled = enabled
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.weight(1f) else Modifier.height(220.dp))
        ) {
            if (controller != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        PlayerView(it).apply {
                            player = controller
                            useController = true
                            keepScreenOn = true
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    controlsVisible = visibility == View.VISIBLE
                                }
                            )
                            // এক্সোপ্লেয়ারের নিজের বিল্ট-ইন settings গিয়ারটা লুকিয়ে ফেলা হচ্ছে,
                            // কারণ আমরা নিচের কাস্টম গিয়ার বাটন দিয়ে সেটা রিপ্লেস করছি।
                            findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
                        }
                    },
                    update = { playerView ->
                        // ট্র্যাক পরিবর্তনের পর এক্সোপ্লেয়ার এই বাটনটা আবার দেখানোর চেষ্টা করতে পারে, তাই প্রতি রিকম্পোজে আবার লুকানো হচ্ছে
                        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
                    }
                )
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Fully qualified call: avoids ambiguity with ColumnScope.AnimatedVisibility
            // from the outer Column receiver in scope at this call site.

            // উপরে ডানদিকে: Caption (CC) + Fullscreen বাটন
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Row(Modifier.padding(6.dp)) {
                    IconButton(onClick = { toggleCaptions(!captionsEnabled) }) {
                        Icon(
                            if (captionsEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,
                            contentDescription = "Captions",
                            tint = if (captionsEnabled) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = { onFullscreenChange(!isFullscreen) }) {
                        Icon(
                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }

            // নিচে ডানদিকে (এক্সোপ্লেয়ারের আগের settings গিয়ারের জায়গায়): Quality, Speed, Audio, Sleep Timer
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 42.dp)
            ) {
                Box {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                        Text(
                            "কোয়ালিটি",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        qualities.forEach { q ->
                            DropdownMenuItem(
                                text = { Text((if (q == selectedQuality) "✓ " else "   ") + q.label) },
                                onClick = { settingsOpen = false; switchQuality(q) }
                            )
                        }
                        Divider()
                        Text(
                            "প্লেব্যাক স্পিড",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        SPEED_OPTIONS.forEach { s ->
                            DropdownMenuItem(
                                text = { Text((if (s == selectedSpeed) "✓ " else "   ") + "${s}x") },
                                onClick = { settingsOpen = false; switchSpeed(s) }
                            )
                        }
                        Divider()
                        Text(
                            "অডিও",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        DropdownMenuItem(
                            text = { Text((if (audioOnlyMode) "✓ " else "   ") + "শুধু অডিও (ডেটা সাশ্রয়)") },
                            onClick = { settingsOpen = false; toggleAudioOnly(!audioOnlyMode) }
                        )
                        Divider()
                        Text(
                            "স্লিপ টাইমার",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        DropdownMenuItem(
                            text = { Text((if (sleepTimerMinutes == null) "✓ " else "   ") + "বন্ধ") },
                            onClick = { settingsOpen = false; sleepTimerMinutes = null }
                        )
                        SLEEP_TIMER_OPTIONS.forEach { m ->
                            DropdownMenuItem(
                                text = { Text((if (sleepTimerMinutes == m) "✓ " else "   ") + "$m মিনিট") },
                                onClick = { settingsOpen = false; sleepTimerMinutes = m }
                            )
                        }
                    }
                }
            }
        }

        if (isFullscreen) return@Column

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }

        Text(
            streamTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                val q = selectedQuality
                val url = q?.progressiveUrl ?: q?.videoOnlyUrl
                if (url != null) {
                    DownloadHelper.downloadVideo(context, url, streamTitle)
                    Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("ডাউনলোড")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (related.isNotEmpty()) {
            Text(
                "সম্পর্কিত ভিডিও",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(related) { v ->
                    RelatedVideoRow(v) { onRelatedSelected(v) }
                }
            }
        }
    }
}

@Composable
private fun RelatedVideoRow(video: VideoResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .width(140.dp)
                .height(80.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                video.uploaderName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
