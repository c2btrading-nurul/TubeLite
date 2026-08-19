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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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

/**
 * নেটওয়ার্ক-সম্পর্কিত সাময়িক (transient) এরর — যেমন ব্যাকগ্রাউন্ডে DNS/হোস্ট রিজলভ
 * ফেইল হওয়া ("Unable to resolve host ...") — হলে অল্প বিরতি দিয়ে কয়েকবার আবার চেষ্টা
 * করা হয়, যাতে ক্ষণস্থায়ী নেটওয়ার্ক থ্রটলিং/হ্যান্ডওভারে ভিডিও লোড না হওয়াটা এড়ানো যায়।
 */
private suspend fun <T> retryIO(
    times: Int = 3,
    initialDelayMs: Long = 1000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(times - 1) {
        try {
            return block()
        } catch (e: java.io.IOException) {
            // UnknownHostException সহ সব IOException — নেটওয়ার্ক ফিরে আসার অপেক্ষায় আবার চেষ্টা
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong()
    }
    return block() // শেষ চেষ্টা — এখনো ফেল করলে exception caller-এর কাছে যাবে
}

/** টাচ ইভেন্ট গিলে ফেলে না — শুধু সিঙ্গেল-ট্যাপ (confirmed, ডাবল-ট্যাপের অংশ না হলে) আর ডাবল-ট্যাপ শনাক্ত করে, বাকি সব স্বাভাবিকভাবে PlayerView-তে পৌঁছায় */
private class DoubleTapSeekOverlay(context: android.content.Context) : FrameLayout(context) {
    var onDoubleTapLeft: (() -> Unit)? = null
    var onDoubleTapRight: (() -> Unit)? = null
    var onSingleTapConfirmedAction: (() -> Unit)? = null

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        // এটা তখনই কল হয় যখন Android নিশ্চিত হয় যে এটা ডাবল-ট্যাপের অংশ না —
        // তাই ডাবল-ট্যাপ করলে এই মেথড আর কল হয় না, ফলে কন্ট্রোলার টগল হবে না
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTapConfirmedAction?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (e.x > width / 2f) onDoubleTapRight?.invoke() else onDoubleTapLeft?.invoke()
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev) // সবসময় নিচের PlayerView-তে পাস করে দেওয়া হয় (বাটন/সিকবার কাজ করার জন্য)
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
    hasPrevious: Boolean = false,
    onPrevious: () -> Unit = {},
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
    var retryTrigger by remember { mutableStateOf(0) }

    fun effectiveQuality(q: QualityOption): QualityOption =
        if (q.videoOnlyUrl != null && selectedAudioUrl != null) q.copy(audioOnlyUrl = selectedAudioUrl) else q

    fun currentMediaItem(q: QualityOption) =
        buildMediaItem(video.url, streamTitle, video.uploaderName, video.thumbnailUrl, effectiveQuality(q), selectedSubtitle)

    LaunchedEffect(video.url, controller, retryTrigger) {
        if (controller == null) return@LaunchedEffect

        if (alreadyPrepared) {
            // এই ভিডিওটা আমাদের নিজস্ব অ্যাপ-স্টেট অনুযায়ী already prepare করা —
            // playback/position স্পর্শ না করে শুধু UI সিঙ্ক করা হচ্ছে
            loading = false
            try {
                val playable = retryIO { YoutubeRepository.getPlayableStream(video.url) }
                qualities = playable.options
                audioOptions = playable.audioOptions
                subtitleOptions = playable.subtitleOptions
                selectedQuality = playable.options.firstOrNull { it.label == selectedQuality?.label } ?: playable.default
            } catch (_: Exception) { }
            try {
                related = YoutubeRepository.getRelated(video.url)
            } catch (_: Exception) { }
            return@LaunchedEffect
        }

        loading = true
        error = null
        selectedAudioUrl = null
        selectedSubtitle = null
        try {
            // "youtubei.googleapis.com" হোস্ট রিজলভ ফেইল করার মতো সাময়িক নেটওয়ার্ক এরর
            // (ব্যাকগ্রাউন্ড থ্রটলিং, Wi-Fi/ডেটা হ্যান্ডওভার) হলে এখানে কয়েকবার আবার
            // চেষ্টা করা হচ্ছে, যাতে পরের ভিডিও অটো-প্লে না হয়ে থেমে না যায়।
            val playable: PlayableStream = retryIO { YoutubeRepository.getPlayableStream(video.url) }
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

            com.tubelite.app.data.NowPlayingStore.save(context, video)
            onPrepared(video.url)
        } catch (e: Exception) {
            error = when (e) {
                is java.net.UnknownHostException ->
                    "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। সংযোগ ঠিক আছে কিনা দেখে আবার চেষ্টা করুন।"
                else -> "স্ট্রিম লোড করা যায়নি: ${e.message}"
            }
        } finally {
            loading = false
        }

        try {
            related = YoutubeRepository.getRelated(video.url)
        } catch (_: Exception) { }
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
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    controlsVisible = visibility == View.VISIBLE
                                }
                            )
                            // PlayerView-এর নিজস্ব বিল্ট-ইন ট্যাপ-টু-টগল ক্লিক লিসেনার বাতিল করা হচ্ছে,
                            // কারণ এটা ডাবল-ট্যাপের প্রতিটা ট্যাপকেও আলাদাভাবে টগল হিসেবে ধরে নেয়।
                            // এখন থেকে নিচের DoubleTapSeekOverlay-ই একমাত্র জায়গা যেখান থেকে
                            // কন্ট্রোলার show/hide নিয়ন্ত্রণ হবে (শুধু কনফার্মড সিঙ্গেল-ট্যাপে)।
                            setOnClickListener {}
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
                        container.onSingleTapConfirmedAction = {
                            if (controlsVisible) playerView.hideController() else playerView.showController()
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

            // উপরে-ডানে: ফুলস্ক্রিন + সাবটাইটেল (CC)
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

            // ডান পাশে-মাঝখানে: শুধু ফুলস্ক্রিন অবস্থায় — Fill/Fit to screen টগল
            if (isFullscreen) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    androidx.compose.animation.AnimatedVisibility(visible = controlsVisible) {
                        IconButton(
                            onClick = { zoomFill = !zoomFill },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                if (zoomFill) Icons.Default.FitScreen else Icons.Default.AspectRatio,
                                contentDescription = if (zoomFill) "Fit to screen" else "Fill screen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // নিচে-ডানে: সেটিংস (কোয়ালিটি, স্পিড, অডিও, স্লিপ টাইমার)
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { retryTrigger++ }) { Text("আবার চেষ্টা করুন") }
            }
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

            OutlinedButton(
                onClick = onPrevious,
                enabled = hasPrevious
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "পূর্ববর্তী ভিডিও")
            }

            OutlinedButton(
                onClick = { related.firstOrNull()?.let { onRelatedSelected(it) } },
                enabled = related.isNotEmpty()
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "পরবর্তী ভিডিও")
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
