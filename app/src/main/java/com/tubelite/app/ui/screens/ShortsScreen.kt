package com.tubelite.app.ui.screens

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.tubelite.app.data.AppLanguageStore
import com.tubelite.app.data.NowPlayingStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.playback.TubeMediaSourceFactory
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ShortsScreen(
    controller: MediaController?,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val scope = rememberCoroutineScope()

    val english = language == AppLanguageStore.ENGLISH

    var shorts by remember {
        mutableStateOf<List<VideoResult>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var loadingMore by remember {
        mutableStateOf(false)
    }

    var refreshing by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var loadRound by remember {
        mutableIntStateOf(1)
    }

    var selectedIndex by remember {
        mutableIntStateOf(-1)
    }

    var selectedVideo by remember {
        mutableStateOf<VideoResult?>(null)
    }

    var channelAvatarUrl by remember {
        mutableStateOf<String?>(null)
    }

    var streamTitle by remember {
        mutableStateOf("")
    }

    var playerLoading by remember {
        mutableStateOf(false)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    /*
     * ------------------------------------------------------------
     * LOAD SHORTS
     * ------------------------------------------------------------
     */

    suspend fun loadShorts(
        round: Int,
        replace: Boolean = false
    ) {
        val requested =
            if (round == 1) {
                24
            } else {
                18
            }

        val newItems =
            YoutubeRepository.getPersonalizedShorts(
                context = context,
                maxItems = requested * round
            )

        if (replace) {
            shorts =
                newItems
                    .distinctBy { it.url }
        } else {
            val existing =
                shorts
                    .map { it.url }
                    .toSet()

            shorts =
                (
                    shorts +
                        newItems.filterNot {
                            it.url in existing
                        }
                    )
                    .distinctBy { it.url }
        }
    }

    /*
     * ------------------------------------------------------------
     * PREPARE VIDEO
     * ------------------------------------------------------------
     */

    suspend fun prepareShort(
        video: VideoResult,
        autoPlay: Boolean = true
    ) {
        val c =
            controller
                ?: throw IllegalStateException(
                    if (english) {
                        "Player is not ready yet."
                    } else {
                        "প্লেয়ার এখনো প্রস্তুত নয়।"
                    }
                )

        playerLoading = true
        error = null

        try {
            val playable =
                YoutubeRepository.getPlayableStream(
                    video.url
                )

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

            val extras =
                Bundle().apply {

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

            val metadata =
                MediaMetadata.Builder()
                    .setTitle(
                        playable.title.ifBlank {
                            video.title
                        }
                    )
                    .setArtist(
                        video.uploaderName
                    )
                    .apply {
                        playable.thumbnailUrl?.let {
                            setArtworkUri(
                                Uri.parse(it)
                            )
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

                    mediaItemBuilder
                        .setSubtitleConfigurations(
                            listOf(
                                MediaItem.SubtitleConfiguration
                                    .Builder(
                                        Uri.parse(
                                            subtitle.url
                                        )
                                    )
                                    .setMimeType(
                                        subtitle.mimeType
                                    )
                                    .setLanguage(
                                        subtitle.label
                                    )
                                    .build()
                            )
                        )
                }

            val mediaItem =
                mediaItemBuilder.build()

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

    /*
     * ------------------------------------------------------------
     * OPEN FIRST / SELECTED SHORT
     * ------------------------------------------------------------
     */

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

    /*
     * ------------------------------------------------------------
     * NEXT SHORT
     * ------------------------------------------------------------
     */

    fun goToNextShort() {

        if (playerLoading) {
            return
        }

        val nextIndex =
            selectedIndex + 1

        if (nextIndex < shorts.size) {

            selectedIndex = nextIndex

            scope.launch {
                prepareShort(
                    shorts[nextIndex],
                    true
                )
            }

            return
        }

        /*
         * Current list শেষ।
         * নতুন personalized Shorts এনে
         * তারপর next video play করবে।
         */

        scope.launch {

            if (loadingMore) {
                return@launch
            }

            loadingMore = true

            try {

                loadRound += 1

                val oldSize =
                    shorts.size

                loadShorts(
                    round = loadRound
                )

                val newIndex =
                    selectedIndex + 1

                if (newIndex < shorts.size) {

                    selectedIndex =
                        newIndex

                    prepareShort(
                        shorts[newIndex],
                        true
                    )

                } else if (shorts.size > oldSize) {

                    val fallbackIndex =
                        oldSize

                    selectedIndex =
                        fallbackIndex

                    prepareShort(
                        shorts[fallbackIndex],
                        true
                    )
                }

            } catch (e: Exception) {

                error =
                    e.message
                        ?: if (english) {
                            "Could not load the next Short."
                        } else {
                            "পরের Shorts লোড করা যায়নি।"
                        }

            } finally {

                loadingMore = false
            }
        }
    }

    /*
     * ------------------------------------------------------------
     * PREVIOUS SHORT
     * ------------------------------------------------------------
     */

    fun goToPreviousShort() {

        if (playerLoading) {
            return
        }

        val previousIndex =
            selectedIndex - 1

        if (previousIndex < 0) {
            return
        }

        selectedIndex =
            previousIndex

        scope.launch {
            prepareShort(
                shorts[previousIndex],
                true
            )
        }
    }

    /*
     * ------------------------------------------------------------
     * CLOSE PLAYER
     * ------------------------------------------------------------
     */

    fun closeShortsPlayer() {

        controller?.pause()

        selectedIndex = -1
        selectedVideo = null

        channelAvatarUrl = null
        streamTitle = ""

        onFullscreenChange(false)
    }

    /*
     * ------------------------------------------------------------
     * REFRESH
     * ------------------------------------------------------------
     */

    fun refreshShorts() {

        if (refreshing) {
            return
        }

        scope.launch {

            refreshing = true
            error = null

            try {

                val currentUrl =
                    selectedVideo?.url

                loadRound = 1

                loadShorts(
                    round = 1,
                    replace = true
                )

                if (selectedIndex >= 0) {

                    val newIndex =
                        if (currentUrl != null) {
                            shorts.indexOfFirst {
                                it.url == currentUrl
                            }
                        } else {
                            0
                        }

                    if (newIndex >= 0) {

                        selectedIndex =
                            newIndex

                        prepareShort(
                            shorts[newIndex],
                            true
                        )

                    } else if (shorts.isNotEmpty()) {

                        selectedIndex = 0

                        prepareShort(
                            shorts[0],
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

    /*
     * ------------------------------------------------------------
     * INITIAL LOAD
     *
     * গুরুত্বপূর্ণ:
     * Shorts menu চাপলে আর list screen দেখাবে না।
     * প্রথম Shorts পাওয়া মাত্র সরাসরি fullscreen player।
     * ------------------------------------------------------------
     */

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

    /*
     * প্রথমবার Shorts data এবং MediaController
     * দুটোই ready হলে সরাসরি প্রথম Shorts play।
     */

    LaunchedEffect(
        controller,
        shorts
    ) {

        if (
            controller != null &&
            shorts.isNotEmpty() &&
            selectedIndex < 0
        ) {
            openShort(0)
        }
    }

    /*
     * ------------------------------------------------------------
     * MEDIA PLAYER LISTENER
     *
     * ভিডিও শেষ হলে automatically next Short।
     * ------------------------------------------------------------
     */

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

                        goToNextShort()
                    }
                }
            }

        controller?.addListener(listener)

        onDispose {
            controller?.removeListener(listener)
        }
    }

    /*
     * ------------------------------------------------------------
     * FULLSCREEN SYSTEM BARS
     * ------------------------------------------------------------
     */

    DisposableEffect(selectedIndex) {

        val activity =
            context as? android.app.Activity

        if (selectedIndex >= 0) {

            activity
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

            activity
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

    /*
     * Android back button
     */

    BackHandler(
        enabled = selectedIndex >= 0
    ) {
        closeShortsPlayer()
    }

    /*
     * ------------------------------------------------------------
     * FULLSCREEN PLAYER
     * ------------------------------------------------------------
     *
     * এখানে selectedIndex >= 0 হলেই player।
     * কোনো Shorts list UI আর দেখানো হবে না।
     */

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
            loading = playerLoading || loadingMore,
            refreshing = refreshing,
            language = language,

            onTogglePlay = {

                if (controller?.isPlaying == true) {
                    controller.pause()
                } else {
                    controller?.play()
                }
            },

            onPrevious = {
                goToPreviousShort()
            },

            onNext = {
                goToNextShort()
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

    /*
     * ------------------------------------------------------------
     * EMPTY / INITIAL LOADING STATE
     *
     * সাধারণত খুব অল্প সময়ের জন্য দেখা যাবে।
     * ------------------------------------------------------------
     */

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        if (loading) {

            CircularProgressIndicator()

        } else {

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
    }
}


/*
 * ================================================================
 * FULLSCREEN SHORTS PLAYER
 * ================================================================
 */

@Composable
private fun ShortsFullscreenPlayer(
    video: VideoResult,
    controller: MediaController?,
    title: String,
    channelAvatarUrl: String?,
    isPlaying: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    language: String,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {

    val english =
        language == AppLanguageStore.ENGLISH

    /*
     * Gesture layer-এর জন্য minimum swipe distance।
     */

    val swipeThreshold =
        120f

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {

        /*
         * --------------------------------------------------------
         * VIDEO
         * --------------------------------------------------------
         */

        AndroidView(
            modifier =
                Modifier.fillMaxSize(),

            factory = { ctx ->

                PlayerView(ctx).apply {

                    player = controller

                    /*
                     * YouTube Shorts-এর মতো custom UI।
                     */
                    useController = false

                    keepScreenOn = true

                    resizeMode =
                        AspectRatioFrameLayout
                            .RESIZE_MODE_ZOOM

                    setShutterBackgroundColor(
                        android.graphics.Color.BLACK
                    )
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


        /*
         * --------------------------------------------------------
         * GESTURE + TAP LAYER
         *
         * Tap:
         *      Play / Pause
         *
         * Swipe UP:
         *      Next
         *
         * Swipe DOWN:
         *      Previous
         * --------------------------------------------------------
         */

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(
                        video.url,
                        selectedKey = Unit
                    ) {

                        awaitEachGesture {

                            val down =
                                awaitFirstDown(
                                    requireUnconsumed = false
                                )

                            val startY =
                                down.position.y

                            var endY =
                                startY

                            var moved = false

                            while (true) {

                                val event =
                                    awaitPointerEvent()

                                val change =
                                    event.changes
                                        .firstOrNull()
                                        ?: break

                                endY =
                                    change.position.y

                                if (
                                    abs(
                                        endY - startY
                                    ) > 20f
                                ) {
                                    moved = true
                                }

                                if (
                                    change.changedToUp()
                                ) {
                                    break
                                }

                                if (
                                    change.changedToCanceled()
                                ) {
                                    return@awaitEachGesture
                                }
                            }

                            val delta =
                                endY - startY

                            /*
                             * Swipe down
                             * = previous
                             */

                            if (
                                moved &&
                                delta >
                                    swipeThreshold
                            ) {

                                onPrevious()

                            }

                            /*
                             * Swipe up
                             * = next
                             */

                            else if (
                                moved &&
                                delta <
                                    -swipeThreshold
                            ) {

                                onNext()

                            }

                            /*
                             * Small/no movement
                             * = tap
                             */

                            else if (!moved) {

                                onTogglePlay()
                            }
                        }
                    }
        )


        /*
         * --------------------------------------------------------
         * LOADING INDICATOR
         * --------------------------------------------------------
         */

        if (loading) {

            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(
                            Alignment.Center
                        )
                        .size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }


        /*
         * --------------------------------------------------------
         * BACK BUTTON
         * --------------------------------------------------------
         */

        IconButton(
            onClick = onClose,

            modifier =
                Modifier
                    .align(
                        Alignment.TopStart
                    )
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


        /*
         * --------------------------------------------------------
         * FLOATING REFRESH BUTTON
         *
         * আগের নির্ধারিত position-এই থাকবে।
         * --------------------------------------------------------
         */

        IconButton(
            onClick = onRefresh,
            enabled = !refreshing,

            modifier =
                Modifier
                    .align(
                        Alignment.TopEnd
                    )
                    .padding(
                        top = 18.dp,
                        end = 10.dp
                    )
                    .size(48.dp)
                    .background(
                        Color.Black.copy(
                            alpha = 0.35f
                        ),
                        CircleShape
                    )
        ) {

            if (refreshing) {

                CircularProgressIndicator(
                    modifier =
                        Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )

            } else {

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
        }


        /*
         * --------------------------------------------------------
         * CENTER PLAY ICON
         * --------------------------------------------------------
         *
         * Video paused থাকলে শুধু indication।
         * Tap করলে আবার play হবে।
         */

        if (
            !isPlaying &&
            !loading &&
            !refreshing
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
                        .align(
                            Alignment.Center
                        )
                        .size(76.dp)
                        .background(
                            Color.Black.copy(
                                alpha = 0.30f
                            ),
                            CircleShape
                        )
                        .padding(14.dp)
            )
        }


        /*
         * --------------------------------------------------------
         * CHANNEL + TITLE
         *
         * নিচে YouTube Shorts-এর মতো।
         * --------------------------------------------------------
         */

        Column(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomStart
                    )
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 70.dp,
                        bottom = 28.dp
                    )
        ) {

            /*
             * Channel row
             */

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                if (
                    !channelAvatarUrl
                        .isNullOrBlank()
                ) {

                    AsyncImage(
                        model =
                            channelAvatarUrl,

                        contentDescription =
                            if (english) {
                                "Channel"
                            } else {
                                "চ্যানেল"
                            },

                        modifier =
                            Modifier
                                .size(46.dp)
                                .clip(
                                    CircleShape
                                ),

                        contentScale =
                            ContentScale.Crop
                    )

                } else {

                    Box(
                        modifier =
                            Modifier
                                .size(46.dp)
                                .clip(
                                    CircleShape
                                )
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
                    video.uploaderName
                        .ifBlank {

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


            /*
             * Video title
             */

            Text(
                title.ifBlank {
                    video.title
                },

                color = Color.White,

                maxLines = 4,

                style =
                    MaterialTheme.typography
                        .bodyLarge
            )
        }
    }
}
