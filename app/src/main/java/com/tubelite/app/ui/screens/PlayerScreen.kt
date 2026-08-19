package com.tubelite.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PlaylistAdd
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tubelite.app.data.AudioOption
import com.tubelite.app.data.NowPlayingStore
import com.tubelite.app.data.PlayableStream
import com.tubelite.app.data.QualityOption
import com.tubelite.app.data.SubtitleOption
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
private const val CONTROLLER_SHOW_MS = 3500

/**
 * টাচ ইভেন্ট নিজে প্রসেস করে single/double tap আলাদা করে, কিন্তু কন্ট্রোলার দেখা অবস্থায়
 * PlayerView-এর নিজস্ব বাটন/সিকবার কাজ করার জন্য টাচ ফরওয়ার্ড করে দেয়। কন্ট্রোলার হাইড
 * থাকা অবস্থায় টাচ আটকে রাখে, যাতে PlayerView নিজে থেকে toggle করতে না পারে।
 */
private class DoubleTapSeekOverlay(context: android.content.Context) : FrameLayout(context) {
    var onDoubleTapLeft: (() -> Unit)? = null
    var onDoubleTapRight: (() -> Unit)? = null
    var onConfirmedSingleTap: (() -> Unit)? = null
    var isControllerVisible: () -> Boolean = { false }

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onConfirmedSingleTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (e.x > width / 2f) onDoubleTapRight?.invoke() else onDoubleTapLeft?.invoke()
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return if (isControllerVisible()) {
            super.dispatchTouchEvent(ev) // বাটন/সিকবার কাজ করার জন্য স্বাভাবিকভাবে পাস
        } else {
            true // কন্ট্রোলার হাইড থাকলে PlayerView-কে টাচ দেওয়া হচ্ছে না
        }
    }
}

private fun buildMediaItem(
    videoUrl: String,
    title: String,
    uploader: String,
    thumbnail: String?,
    q: QualityOption,
    subtitle: SubtitleOption?
): MediaItem {
    val bundle = Bundle()
    q.progressiveUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_PROGRESSIVE, it) }
    q.videoOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_VIDEO_ONLY, it) }
    q.audioOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_AUDIO_ONLY, it) }
    q.hlsUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_HLS, it) }
    val fallbackUri = q.progressiveUrl ?: q.hlsUrl ?: q.videoOnlyUrl ?: "about:blank"

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(uploader)
        .apply { thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
        .build()

    val builder = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setUri(fallbackUri)
        .setMediaMetadata(metadata)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(bundle).build())

    if (subtitle != null) {
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setMimeType(subtitle.mimeType)
                    .setLanguage(subtitle.label)
                    .build()
            )
        )
    }
    return builder.build()
}

@Composable
fun PlayerScreen(
    video: VideoResult,
    controller: MediaController?,
    autoPlayEnabled: Boolean,
    isFullscreen: Boolean,
    alreadyPrepared: Boolean,
    onPrepared: (String) -> Unit,
    hasPrevious: Boolean,
    onPrevious: () -> Unit,
    hasNext: Boolean,
    onNext: () -> Unit,
    queueNextVideo: VideoResult?,
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
    var subtitleOptions by remember { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<QualityOption?>(null) }
    var selectedAudioUrl by remember { mutableStateOf<String?>(null) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleOption?>(null) }
    var selectedSpeed by remember { mutableFloatStateOf(1f) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var related by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var zoomFill by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }

    fun effectiveQuality(q: QualityOption): QualityOption =
        if (q.videoOnlyUrl != null && selectedAudioUrl != null) q.copy(audioOnlyUrl = selectedAudioUrl) else q

    fun currentMediaItem(q: QualityOption) =
        buildMediaItem(video.url, streamTitle, video.uploaderName, video.thumbnailUrl, effectiveQuality(q), selectedSubtitle)

    LaunchedEffect(video.url, controller) {
        if (controller == null) return@LaunchedEffect

        if (alreadyPrepared) {
            loading = false
            try {
                val playable = YoutubeRepository.getPlayableStream(video.url)
                qualities = playable.options
                audioOptions = playable.audioOptions
                subtitleOptions = playable.subtitleOptions
                selectedQuality = playable.options.firstOrNull { it.label == selectedQuality?.label } ?: playable.default
            } catch (_: Exception) { }
            related = YoutubeRepository.getRelated(video.url)
            return@LaunchedEffect
        }

        loading = true
        error = null
        selectedAudioUrl = null
        selectedSubtitle = null
        try {
            val playable: PlayableStream = YoutubeRepository.getPlayableStream(video.url)
            streamTitle = playable.title
            qualities = playable.options
            audioOptions = playable.audioOptions
            subtitleOptions = playable.subtitleOptions
            selectedQuality = playable.default

            controller.setMediaItem(currentMediaItem(playable.default))
            controller.prepare()
            controller.playWhenReady = autoPlayEnabled
            controller.setPlaybackParameters(PlaybackParameters(1f))
            selectedSpeed = 1f

            NowPlayingStore.save(context, video)
            com.tubelite.app.data.WatchHistoryStore.add(context, video)
            onPrepared(video.url)
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
                    val next = queueNextVideo ?: related.firstOrNull()
                    next?.let { onRelatedSelected(it) }
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

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(600)
            seekFeedback = null
        }
    }

    fun rebuildKeepingPosition(newQuality: QualityOption) {
        if (controller == null) return
        val pos = controller.currentPosition
        val wasPlaying = controller.isPlaying
        controller.setMediaItem(currentMediaItem(newQuality))
        controller.prepare()
        controller.seekTo(pos)
        controller.playWhenReady = wasPlaying
        controller.setPlaybackParameters(PlaybackParameters(selectedSpeed))
    }

    fun switchQuality(q: QualityOption) {
        if (q == selectedQuality) return
        selectedAudioUrl = null
        selectedQuality = q
        rebuildKeepingPosition(q)
    }

    fun switchAudio(audio: AudioOption) {
        val q = selectedQuality ?: return
        if (q.videoOnlyUrl == null) return
        selectedAudioUrl = audio.url
        rebuildKeepingPosition(q)
    }

    fun switchSubtitle(sub: SubtitleOption?) {
        selectedSubtitle = sub
        val q = selectedQuality ?: return
        rebuildKeepingPosition(q)
    }

    fun switchSpeed(speed: Float) {
        selectedSpeed = speed
        controller?.setPlaybackParameters(PlaybackParameters(speed))
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutes = minutes
        if (minutes > 0) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                controller?.pause()
            }
        }
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
                    factory = { ctx ->
                        val playerView = PlayerView(ctx).apply {
                            player = controller
                            useController = true
                            keepScreenOn = true
                            controllerHideOnTouch = false
                            controllerShowTimeoutMs = CONTROLLER_SHOW_MS
                            setOnClickListener { } // নেটিভ tap-to-toggle নিষ্ক্রিয় করা হচ্ছে
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    controlsVisible = visibility == View.VISIBLE
                                }
                            )
                        }
                        DoubleTapSeekOverlay(ctx).apply {
                            addView(
                                playerView,
                                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            )
                        }
                    },
                    update = { overlay ->
                        val container = overlay as DoubleTapSeekOverlay
                        val playerView = container.getChildAt(0) as PlayerView
                        playerView.resizeMode = if (zoomFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE

                        container.isControllerVisible = { controlsVisible }

                        container.onConfirmedSingleTap = {
                            if (playerView.isControllerFullyVisible) playerView.hideController() else playerView.showController()
                        }
                        container.onDoubleTapLeft = {
                            val c = controller
                            if (c != null) {
                                c.seekTo((c.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
                                seekFeedback = "-১০ সেকেন্ড"
                            }
                        }
                        container.onDoubleTapRight = {
                            val c = controller
                            if (c != null) {
                                c.seekTo(c.currentPosition + SEEK_STEP_MS)
                                seekFeedback = "+১০ সেকেন্ড"
                            }
                        }
                    }
                )
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            seekFeedback?.let {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        it,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (isFullscreen) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    androidx.compose.animation.AnimatedVisibility(visible = controlsVisible) {
                        IconButton(
                            onClick = { zoomFill = !zoomFill },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.AspectRatio,
                                contentDescription = "Fit / Fill screen",
                                tint = if (zoomFill) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                }
            }            
            
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                androidx.compose.animation.AnimatedVisibility(visible = controlsVisible) {
                    Row(Modifier.padding(6.dp)) {
                        if (subtitleOptions.isNotEmpty()) {
                            IconButton(onClick = {
                                switchSubtitle(if (selectedSubtitle == null) subtitleOptions.first() else null)
                            }) {
                                Icon(
                                    Icons.Default.ClosedCaption,
                                    contentDescription = "Subtitles",
                                    tint = if (selectedSubtitle != null) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
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
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                androidx.compose.animation.AnimatedVisibility(visible = controlsVisible) {
                    Box {
                        IconButton(onClick = { settingsOpen = true }, modifier = Modifier.padding(6.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = settingsOpen,
                            onDismissRequest = { settingsOpen = false; expandedSection = null }
                        ) {
                            Column(Modifier.width(230.dp)) {
                                SettingsAccordionRow(
                                    label = "Quality",
                                    value = selectedQuality?.label ?: "",
                                    expanded = expandedSection == "quality",
                                    onHeaderClick = { expandedSection = if (expandedSection == "quality") null else "quality" }
                                ) {
                                    qualities.forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text((if (q == selectedQuality) "✓ " else "   ") + q.label) },
                                            onClick = { switchQuality(q); settingsOpen = false; expandedSection = null }
                                        )
                                    }
                                }
                                Divider()
                                SettingsAccordionRow(
                                    label = "Speed",
                                    value = "${selectedSpeed}x",
                                    expanded = expandedSection == "speed",
                                    onHeaderClick = { expandedSection = if (expandedSection == "speed") null else "speed" }
                                ) {
                                    SPEED_OPTIONS.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text((if (s == selectedSpeed) "✓ " else "   ") + "${s}x") },
                                            onClick = { switchSpeed(s); settingsOpen = false; expandedSection = null }
                                        )
                                    }
                                }
                                if (audioOptions.size > 1 && selectedQuality?.videoOnlyUrl != null) {
                                    Divider()
                                    SettingsAccordionRow(
                                        label = "Audio",
                                        value = "",
                                        expanded = expandedSection == "audio",
                                        onHeaderClick = { expandedSection = if (expandedSection == "audio") null else "audio" }
                                    ) {
                                        audioOptions.forEach { a ->
                                            val isSelected = (selectedAudioUrl ?: selectedQuality?.audioOnlyUrl) == a.url
                                            DropdownMenuItem(
                                                text = { Text((if (isSelected) "✓ " else "   ") + a.label) },
                                                onClick = { switchAudio(a); settingsOpen = false; expandedSection = null }
                                            )
                                        }
                                    }
                                }
                                Divider()
                                SettingsAccordionRow(
                                    label = "Sleep Timer",
                                    value = if (sleepMinutes == 0) "Off" else "${sleepMinutes}m",
                                    expanded = expandedSection == "sleep",
                                    onHeaderClick = { expandedSection = if (expandedSection == "sleep") null else "sleep" }
                                ) {
                                    SLEEP_OPTIONS.forEach { m ->
                                        val label = if (m == 0) "Off" else "$m min"
                                        DropdownMenuItem(
                                            text = { Text((if (m == sleepMinutes) "✓ " else "   ") + label) },
                                            onClick = { setSleepTimer(m); settingsOpen = false; expandedSection = null }
                                        )
                                    }
                                }
                            }
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

        var saveMenuOpen by remember { mutableStateOf(false) }
        var showNewPlaylistDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        if (showNewPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showNewPlaylistDialog = false },
                title = { Text("নতুন প্লে-লিস্ট") },
                text = {
                    OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, placeholder = { Text("নাম") })
                },
                confirmButton = {
                    TextButton(onClick = {
                        val n = newPlaylistName.trim()
                        if (n.isNotEmpty()) {
                            com.tubelite.app.data.PlaylistStore.createPlaylist(context, n)
                            com.tubelite.app.data.PlaylistStore.addVideo(context, n, video)
                            Toast.makeText(context, "\"$n\"-এ যোগ হলো", Toast.LENGTH_SHORT).show()
                        }
                        newPlaylistName = ""
                        showNewPlaylistDialog = false
                    }) { Text("তৈরি ও যোগ করুন") }
                },
                dismissButton = { TextButton(onClick = { showNewPlaylistDialog = false }) { Text("বাতিল") } }
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasPrevious) {
                OutlinedButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                }
            }
            if (hasNext) {
                OutlinedButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
            }
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
            }
            Box {
                OutlinedButton(onClick = { saveMenuOpen = true }) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                }
                DropdownMenu(expanded = saveMenuOpen, onDismissRequest = { saveMenuOpen = false }) {
                    com.tubelite.app.data.PlaylistStore.getPlaylistNames(context).forEach { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            com.tubelite.app.data.PlaylistStore.addVideo(context, name, video)
                            saveMenuOpen = false
                            Toast.makeText(context, "\"$name\"-এ যোগ হলো", Toast.LENGTH_SHORT).show()
                        })
                    }
                    Divider()
                    DropdownMenuItem(text = { Text("+ নতুন প্লে-লিস্ট") }, onClick = {
                        saveMenuOpen = false
                        showNewPlaylistDialog = true
                    })
                }
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
private fun SettingsAccordionRow(
    label: String,
    value: String,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onHeaderClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(value, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column { content() }
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
            modifier = Modifier.width(140.dp).height(80.dp)
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
