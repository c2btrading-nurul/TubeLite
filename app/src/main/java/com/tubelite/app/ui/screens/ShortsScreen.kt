package com.tubelite.app.ui.screens

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.tubelite.app.data.AppLanguageStore
import com.tubelite.app.data.NowPlayingStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.playback.TubeMediaSourceFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun ShortsScreen(
    controller: MediaController?,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var shorts by remember {
        mutableStateOf<List<VideoResult>>(emptyList())
    }

    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadRound by remember { mutableIntStateOf(1) }

    var selectedIndex by remember { mutableIntStateOf(-1) }
    var selectedVideo by remember { mutableStateOf<VideoResult?>(null) }

    var channelAvatarUrl by remember { mutableStateOf<String?>(null) }
    var streamTitle by remember { mutableStateOf("") }

    var playerLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    val english = language == AppLanguageStore.ENGLISH

    suspend fun loadShorts(
        round: Int,
        replace: Boolean = false
    ) {
        val requested = if (round == 1) 24 else 18

        val newItems = YoutubeRepository.getPersonalizedShorts(
            context = context,
            maxItems = requested * round
        )

        if (replace) {
            shorts = newItems.distinctBy { it.url }
        } else {
            val existing = shorts.map { it.url }.toSet()

            shorts = (
                shorts +
                    newItems.filterNot { it.url in existing }
                ).distinctBy { it.url }
        }
    }

    suspend fun prepareShort(
        video: VideoResult,
        autoPlay: Boolean = true
    ) {
        val c = controller ?: throw IllegalStateException(
            if (english) {
                "Player is not ready yet."
            } else {
                "প্লেয়ার এখনো প্রস্তুত নয়।"
            }
        )

        playerLoading = true
        error = null

        try {
            val playable = YoutubeRepository.getPlayableStream(video.url)
            val q = playable.default

            val streamUrl =
                q.progressiveUrl
                    ?: q.hlsUrl
                    ?: q.videoOnlyUrl

            if (streamUrl.isNullOrBlank()) {
                throw IllegalStateException(
                    if (english) {
                        "No playable stream found."
                    } else {
                        "কোনো প্লে করার স্ট্রিম পাওয়া যায়নি।"
                    }
                )
            }

            val extras = Bundle().apply {
                q.progressiveUrl?.let {
                    putString(
                        TubeMediaSourceFactory.KEY_PROGRESSIVE,
                        it
                    )
                }

                q.videoOnlyUrl?.let {
                    putString(
                        TubeMediaSourceFactory.KEY_VIDEO_ONLY,
                        it
                    )
                }

                q.audioOnlyUrl?.let {
                    putString(
                        TubeMediaSourceFactory.KEY_AUDIO_ONLY,
                        it
                    )
                }

                q.hlsUrl?.let {
                    putString(
                        TubeMediaSourceFactory.KEY_HLS,
                        it
                    )
                }
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(
                    playable.title.ifBlank {
                        video.title
                    }
                )
                .setArtist(video.uploaderName)
                .apply {
                    playable.thumbnailUrl?.let {
                        setArtworkUri(Uri.parse(it))
                    }
                }
                .build()

            val mediaItemBuilder =
                MediaItem.Builder()
                    .setMediaId(video.url)
                    .setUri(streamUrl)
                    .setMediaMetadata(metadata)
                    .setRequestMetadata(
                        MediaItem.RequestMetadata.Builder()
                            .setExtras(extras)
                            .build()
                    )

            playable.subtitleOptions
                .firstOrNull()
                ?.let { subtitle ->

                    mediaItemBuilder.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(
                                Uri.parse(subtitle.url)
                            )
                                .setMimeType(subtitle.mimeType)
                                .setLanguage(subtitle.label)
                                .build()
                        )
                    )
                }

            val mediaItem = mediaItemBuilder.build()

            streamTitle =
                playable.title.ifBlank {
                    video.title
                }

            channelAvatarUrl =
                playable.channelAvatarUrl

            selectedVideo = video

            c.setMediaItem(mediaItem)
            c.prepare()

            c.playWhenReady = autoPlay

            if (autoPlay) {
                c.play()
            }

            NowPlayingStore.save(
                context,
                video
            )

        } catch (e: Exception) {

            error =
                e.message
                    ?: if (english) {
                        "Could not play this Short."
                    } else {
                        "এই Shorts চালানো যায়নি।"
                    }

        } finally {
            playerLoading = false
        }
    }

    fun openShort(index: Int) {

        if (index !in shorts.indices) {
            return
        }

        selectedIndex = index
        selectedVideo = shorts[index]

        onFullscreenChange(true)

        scope.launch {
            prepareShort(
                shorts[index],
                true
            )
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

    fun refreshShorts() {

        if (refreshing) {
            return
        }

        scope.launch {

            refreshing = true

            try {

                val currentUrl =
                    selectedVideo?.url

                loadRound = 1

                loadShorts(
                    round = 1,
                    replace = true
                )

                if (currentUrl != null) {

                    val newIndex =
                        shorts.indexOfFirst {
                            it.url == currentUrl
                        }

                    if (newIndex >= 0) {

                        selectedIndex = newIndex

                        prepareShort(
                            shorts[newIndex],
                            true
                        )
                    }
                }

            } catch (e: Exception) {

                error =
                    e.message
                        ?: if (english) {
                            "Could not refresh Shorts."
                        } else {
                            "Shorts রিফ্রেশ করা যায়নি।"
                        }

            } finally {

                refreshing = false
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {

        try {

            loadShorts(
                round = 1,
                replace = true
            )

        } catch (e: Exception) {

            error =
                e.message
                    ?: if (english) {
                        "Could not load Shorts."
                    } else {
                        "Shorts লোড করা যায়নি।"
                    }

        } finally {

            loading = false
        }
    }

    LaunchedEffect(listState) {

        snapshotFlow {

            val last =
                listState.layoutInfo
                    .visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?: 0

            val total =
                listState.layoutInfo
                    .totalItemsCount

            total > 0 &&
                last >= total - 4

        }.collect { nearEnd ->

            if (
                nearEnd &&
                !loading &&
                !loadingMore &&
                shorts.isNotEmpty() &&
                selectedIndex < 0
            ) {

                loadingMore = true

                try {

                    loadRound += 1

                    loadShorts(
                        loadRound
                    )

                } catch (_: Exception) {

                } finally {

                    loadingMore = false
                }
            }
        }
    }

    DisposableEffect(controller) {

        val listener =
            object : Player.Listener {

                override fun onIsPlayingChanged(
                    playing: Boolean
                ) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {

                    if (
                        playbackState ==
                            Player.STATE_ENDED &&
                        selectedIndex >= 0
                    ) {

                        val nextIndex =
                            selectedIndex + 1

                        if (
                            nextIndex < shorts.size
                        ) {

                            selectedIndex =
                                nextIndex

                            scope.launch {
                                prepareShort(
                                    shorts[nextIndex],
                                    true
                                )
                            }

                        } else {

                            scope.launch {

                                try {

                                    loadingMore = true

                                    loadRound += 1

                                    loadShorts(
                                        loadRound
                                    )

                                    val newIndex =
                                        selectedIndex + 1

                                    if (
                                        newIndex < shorts.size
                                    ) {

                                        selectedIndex =
                                            newIndex

                                        prepareShort(
                                            shorts[newIndex],
                                            true
                                        )

                                    }

                                } catch (_: Exception) {

                                } finally {

                                    loadingMore = false
                                }
                            }
                        }
                    }
                }
            }

        controller?.addListener(listener)

        onDispose {
            controller?.removeListener(listener)
        }
    }

    DisposableEffect(selectedIndex) {

        if (selectedIndex >= 0) {

            context
                .let { it as? android.app.Activity }
                ?.window
                ?.let { window ->

                    val insetsController =
                        WindowCompat.getInsetsController(
                            window,
                            window.decorView
                        )

                    insetsController.hide(
                        WindowInsetsCompat.Type.systemBars()
                    )

                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
        }

        onDispose {

            if (selectedIndex >= 0) {

                context
                    .let { it as? android.app.Activity }
                    ?.window
                    ?.let { window ->

                        WindowCompat
                            .getInsetsController(
                                window,
                                window.decorView
                            )
                            .show(
                                WindowInsetsCompat.Type.systemBars()
                            )
                    }
            }
        }
    }

    BackHandler(
        enabled = selectedIndex >= 0
    ) {
        closeShortsPlayer()
    }

    if (
        selectedIndex >= 0 &&
        selectedVideo != null
    ) {

        ShortsFullscreenPlayer(
            video = selectedVideo!!,
            controller = controller,
            title = streamTitle,
            channelAvatarUrl = channelAvatarUrl,
            isPlaying = isPlaying,
            loading = playerLoading,
            language = language,
            onTogglePlay = {

                if (controller?.isPlaying == true) {
                    controller.pause()
                } else {
                    controller?.play()
                }
            },
            onClose = {
                closeShortsPlayer()
            },
            onRefresh = {
                refreshShorts()
            }
        )

        return
    }

    Column(
        Modifier.fillMaxSize()
    ) {

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        if (english) {
                            "Shorts"
                        } else {
                            "শর্টস"
                        },
                        style =
                            MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "YouTube Shorts",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurface
                                .copy(alpha = 0.65f)
                    )
                }

                IconButton(
                    onClick = {
                        refreshShorts()
                    },
                    enabled = !refreshing
                ) {

                    if (refreshing) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )

                    } else {

                        Icon(
                            Icons.Default.Refresh,
                            contentDescription =
                                if (english) {
                                    "Refresh"
                                } else {
                                    "রিফ্রেশ"
                                }
                        )
                    }
                }
            }
        }

        if (loading) {

            Box(
                Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center
            ) {

                CircularProgressIndicator()
            }

            return@Column
        }

        if (shorts.isEmpty()) {

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    error
                        ?: if (english) {
                            "No Shorts found right now."
                        } else {
                            "এই মুহূর্তে কোনো Shorts পাওয়া যায়নি।"
                        },
                    color =
                        if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                )
            }

            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            items(
                shorts,
                key = { it.url }
            ) { video ->

                ShortsCard(
                    video = video,
                    onClick = {

                        val index =
                            shorts.indexOfFirst {
                                it.url == video.url
                            }

                        openShort(index)
                    }
                )
            }

            if (loadingMore) {

                item {

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        CircularProgressIndicator(
                            Modifier.size(24.dp)
                        )
                    }
                }
            }
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
    language: String,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {

    val english =
        language == AppLanguageStore.ENGLISH

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AndroidView(

            modifier =
                Modifier.fillMaxSize(),

            factory = { ctx ->

                PlayerView(ctx).apply {

                    player = controller

                    useController = false

                    keepScreenOn = true

                    resizeMode =
                        AspectRatioFrameLayout
                            .RESIZE_MODE_ZOOM

                    setShutterBackgroundColor(
                        android.graphics.Color.BLACK
                    )

                    setOnClickListener {

                        onTogglePlay()
                    }
                }
            },

            update = { playerView ->

                playerView.player =
                    controller

                playerView.resizeMode =
                    AspectRatioFrameLayout
                        .RESIZE_MODE_ZOOM
            }
        )

        if (loading) {

            CircularProgressIndicator(
                modifier =
                    Modifier.align(
                        Alignment.Center
                    ),
                color = Color.White
            )
        }

        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = 18.dp,
                        start = 8.dp
                    )
                    .size(48.dp)
        ) {

            Icon(
                Icons.Default.ArrowBack,
                contentDescription =
                    if (english) {
                        "Back"
                    } else {
                        "পেছনে"
                    },
                tint = Color.White
            )
        }

        IconButton(
            onClick = onRefresh,
            enabled = !loading,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 18.dp,
                        end = 10.dp
                    )
                    .size(48.dp)
                    .background(
                        Color.Black.copy(
                            alpha = 0.30f
                        ),
                        CircleShape
                    )
        ) {

            Icon(
                Icons.Default.Refresh,
                contentDescription =
                    if (english) {
                        "Refresh Shorts"
                    } else {
                        "Shorts রিফ্রেশ"
                    },
                tint = Color.White
            )
        }

        if (
            !isPlaying &&
            !loading
        ) {

            Icon(
                Icons.Default.PlayArrow,
                contentDescription =
                    if (english) {
                        "Play"
                    } else {
                        "প্লে"
                    },
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(76.dp)
                        .background(
                            Color.Black.copy(
                                alpha = 0.28f
                            ),
                            CircleShape
                        )
                        .padding(14.dp)
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 70.dp,
                        bottom = 28.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                if (channelAvatarUrl != null) {

                    AsyncImage(
                        model = channelAvatarUrl,
                        contentDescription =
                            if (english) {
                                "Channel"
                            } else {
                                "চ্যানেল"
                            },
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                        contentScale =
                            ContentScale.Crop
                    )

                } else {

                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Color.White.copy(
                                    alpha = 0.20f
                                )
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            video.uploaderName
                                .take(1)
                                .uppercase(),
                            color = Color.White
                        )
                    }
                }

                Spacer(
                    Modifier.width(10.dp)
                )

                Text(
                    video.uploaderName.ifBlank {
                        if (english) {
                            "Unknown channel"
                        } else {
                            "অজানা চ্যানেল"
                        }
                    },
                    color = Color.White,
                    style =
                        MaterialTheme.typography
                            .titleMedium
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                title.ifBlank {
                    video.title
                },
                color = Color.White,
                maxLines = 3,
                style =
                    MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ShortsCard(
    video: VideoResult,
    onClick: () -> Unit
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape =
            RoundedCornerShape(18.dp)
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {

            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription =
                    video.title,
                modifier =
                    Modifier
                        .width(126.dp)
                        .aspectRatio(9f / 16f)
                        .clip(
                            RoundedCornerShape(14.dp)
                        ),
                contentScale =
                    ContentScale.Crop
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {

                Text(
                    video.title,
                    maxLines = 4,
                    style =
                        MaterialTheme.typography
                            .bodyLarge
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    video.uploaderName,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurface
                            .copy(alpha = 0.68f)
                )
            }
        }
    }
}
