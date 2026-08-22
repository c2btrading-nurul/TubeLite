package com.tubelite.app.ui.screens

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tubelite.app.data.NowPlayingStore
import com.tubelite.app.data.SearchHistoryStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.playback.TubeMediaSourceFactory
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SHORT_MAX_SECONDS = 60L
private const val INITIAL_SHORTS = 24
private const val EXTRA_SHORTS = 18
private const val SWIPE_THRESHOLD_PX = 120f

/**
 * Shorts-only fullscreen player.
 *
 * Behaviour:
 * - Opening the Shorts tab immediately opens the first Short.
 * - Tap video = play/pause.
 * - Swipe up = next Short.
 * - Swipe down = previous Short.
 * - Video end = next Short automatically.
 * - Refresh button stays floating at the top-right.
 * - Back closes the Shorts player instead of exiting the app.
 */
@Composable
fun ShortsScreen(
    controller: MediaController?,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shorts by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var selectedVideo by remember { mutableStateOf<VideoResult?>(null) }
    var channelAvatarUrl by remember { mutableStateOf<String?>(null) }
    var streamTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var playerLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var loadRound by remember { mutableIntStateOf(1) }

    suspend fun loadShorts(round: Int, replace: Boolean = false) {
        val target = if (round == 1) INITIAL_SHORTS else EXTRA_SHORTS * round

        // Build a lightweight personalized feed from the user's recent searches,
        // then mix in YouTube's current trending feed. Only <=60 second videos remain.
        val queries = SearchHistoryStore.getRecent(context, limit = 5)
        val candidates = mutableListOf<VideoResult>()

        for (query in queries) {
            try {
                candidates += YoutubeRepository.search(query)
            } catch (_: Exception) {
                // One failed search must not prevent the rest of Shorts from loading.
            }
        }

        try {
            candidates += YoutubeRepository.getTrending()
        } catch (_: Exception) {
            // Personalized search results can still be used if trending fails.
        }

        val filtered = candidates
            .asSequence()
            .filter { it.durationSeconds in 1..SHORT_MAX_SECONDS }
            .distinctBy { it.url }
            .take(target)
            .toList()

        if (replace) {
            shorts = filtered
        } else {
            val existing = shorts.asSequence().map { it.url }.toSet()
            shorts = (shorts + filtered.filterNot { it.url in existing })
                .distinctBy { it.url }
        }
    }

    suspend fun prepareShort(video: VideoResult, autoPlay: Boolean = true) {
        val c = controller ?: run {
            error = "Player is not ready yet."
            return
        }

        playerLoading = true
        error = null

        try {
            val playable = YoutubeRepository.getPlayableStream(video.url)
            val q = playable.default
            val streamUrl = q.progressiveUrl ?: q.hlsUrl ?: q.videoOnlyUrl
                ?: error("No playable stream found.")

            val extras = Bundle().apply {
                q.progressiveUrl?.let { putString(TubeMediaSourceFactory.KEY_PROGRESSIVE, it) }
                q.videoOnlyUrl?.let { putString(TubeMediaSourceFactory.KEY_VIDEO_ONLY, it) }
                q.audioOnlyUrl?.let { putString(TubeMediaSourceFactory.KEY_AUDIO_ONLY, it) }
                q.hlsUrl?.let { putString(TubeMediaSourceFactory.KEY_HLS, it) }
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(playable.title.ifBlank { video.title })
                .setArtist(video.uploaderName)
                .apply {
                    playable.thumbnailUrl?.let { setArtworkUri(Uri.parse(it)) }
                }
                .build()

            val builder = MediaItem.Builder()
                .setMediaId(video.url)
                .setUri(streamUrl)
                .setMediaMetadata(metadata)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setExtras(extras)
                        .build()
                )

            playable.subtitleOptions.firstOrNull()?.let { subtitle ->
                builder.setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                            .setMimeType(subtitle.mimeType)
                            .setLanguage(subtitle.label)
                            .build()
                    )
                )
            }

            streamTitle = playable.title.ifBlank { video.title }
            channelAvatarUrl = playable.channelAvatarUrl
            selectedVideo = video

            c.setMediaItem(builder.build())
            c.prepare()
            c.playWhenReady = autoPlay
            if (autoPlay) c.play()

            NowPlayingStore.save(context, video)
        } catch (e: Exception) {
            error = e.message ?: "Could not play this Short."
        } finally {
            playerLoading = false
        }
    }

    fun openShort(index: Int) {
        if (index !in shorts.indices || controller == null) return
        selectedIndex = index
        selectedVideo = shorts[index]
        onFullscreenChange(true)
        scope.launch { prepareShort(shorts[index]) }
    }

    fun goToNextShort() {
        if (playerLoading || loadingMore) return

        val next = selectedIndex + 1
        if (next < shorts.size) {
            selectedIndex = next
            scope.launch { prepareShort(shorts[next]) }
            return
        }

        scope.launch {
            loadingMore = true
            try {
                loadRound += 1
                val oldSize = shorts.size
                loadShorts(loadRound)
                val nextIndex = selectedIndex + 1
                if (nextIndex < shorts.size) {
                    selectedIndex = nextIndex
                    prepareShort(shorts[nextIndex])
                } else if (shorts.size > oldSize) {
                    selectedIndex = oldSize
                    prepareShort(shorts[oldSize])
                } else {
                    error = "No more Shorts found right now."
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load the next Short."
            } finally {
                loadingMore = false
            }
        }
    }

    fun goToPreviousShort() {
        if (playerLoading) return
        val previous = selectedIndex - 1
        if (previous < 0) return
        selectedIndex = previous
        scope.launch { prepareShort(shorts[previous]) }
    }

    fun refreshShorts() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            error = null
            try {
                val currentUrl = selectedVideo?.url
                loadRound = 1
                loadShorts(1, replace = true)
                if (shorts.isEmpty()) {
                    selectedIndex = -1
                    selectedVideo = null
                    onFullscreenChange(false)
                } else if (selectedIndex >= 0) {
                    val newIndex = currentUrl?.let { url -> shorts.indexOfFirst { it.url == url } } ?: 0
                    selectedIndex = if (newIndex >= 0) newIndex else 0
                    prepareShort(shorts[selectedIndex])
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not refresh Shorts."
            } finally {
                refreshing = false
                loading = false
            }
        }
    }

    fun closeShortsPlayer() {
        controller?.pause()
        selectedIndex = -1
        selectedVideo = null
        channelAvatarUrl = null
        streamTitle = ""
        onFullscreenChange(false)
    }

    LaunchedEffect(Unit) {
        try {
            loadShorts(1, replace = true)
        } catch (e: Exception) {
            error = e.message ?: "Could not load Shorts."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(controller, shorts) {
        if (controller != null && shorts.isNotEmpty() && selectedIndex < 0) {
            openShort(0)
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && selectedIndex >= 0) {
                    goToNextShort()
                }
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    DisposableEffect(selectedIndex) {
        val activity = context as? Activity
        if (selectedIndex >= 0) {
            activity?.window?.let { window ->
                val insets = WindowCompat.getInsetsController(window, window.decorView)
                insets.hide(WindowInsetsCompat.Type.systemBars())
                insets.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = selectedIndex >= 0) {
        closeShortsPlayer()
    }

    if (selectedIndex >= 0 && selectedVideo != null) {
        ShortsFullscreenPlayer(
            video = selectedVideo!!,
            controller = controller,
            title = streamTitle,
            channelAvatarUrl = channelAvatarUrl,
            isPlaying = isPlaying,
            loading = playerLoading || loadingMore,
            refreshing = refreshing,
            onTogglePlay = {
                if (controller?.isPlaying == true) controller.pause() else controller?.play()
            },
            onPrevious = ::goToPreviousShort,
            onNext = ::goToNextShort,
            onClose = ::closeShortsPlayer,
            onRefresh = ::refreshShorts
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Text(error ?: "No Shorts found right now.")
        }
    }
}

@Composable
private fun ShortsFullscreenPlayer(
    video: VideoResult,
    controller: MediaController?,
    title: String,
    channelAvatarUrl: String?,
    isPlaying: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.player = controller
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        )

        // Gesture layer is intentionally above the video but below the buttons.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(video.url) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        var endY = startY
                        var moved = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            endY = change.position.y
                            if (abs(endY - startY) > 20f) moved = true
                            if (!change.pressed) break
                        }

                        val delta = endY - startY
                        when {
                            moved && delta < -SWIPE_THRESHOLD_PX -> onNext()
                            moved && delta > SWIPE_THRESHOLD_PX -> onPrevious()
                            !moved -> onTogglePlay()
                        }
                    }
                }
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 18.dp, start = 8.dp)
                .size(48.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 10.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh Shorts",
                    tint = Color.White
                )
            }
        }

        if (!isPlaying && !loading && !refreshing) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(76.dp)
                    .background(Color.Black.copy(alpha = 0.30f), CircleShape)
                    .padding(14.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 70.dp, bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!channelAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channelAvatarUrl,
                        contentDescription = "Channel",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            video.uploaderName.take(1).uppercase(),
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    video.uploaderName.ifBlank { "Unknown channel" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                title.ifBlank { video.title },
                color = Color.White,
                maxLines = 4,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
