package com.tubelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubelite.app.data.AppLanguageStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun ShortsScreen(
    onVideoSelected: (VideoResult) -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val listState = rememberLazyListState()
    var shorts by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadRound by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()

    suspend fun loadShorts(round: Int) {
        val requested = if (round == 1) 24 else 18
        val newItems = YoutubeRepository.getShorts(requested * round)
        val existing = shorts.mapTo(mutableSetOf()) { it.url }
        shorts = shorts + newItems.filterNot { it.url in existing }
    }

    LaunchedEffect(Unit) {
        try {
            loadShorts(1)
        } catch (e: Exception) {
            error = e.message ?: if (language == AppLanguageStore.ENGLISH) "Could not load Shorts" else "শর্টস লোড করা যায়নি"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 4
        }.collect { nearEnd ->
            if (nearEnd && !loading && !loadingMore && shorts.isNotEmpty()) {
                loadingMore = true
                try {
                    loadRound += 1
                    loadShorts(loadRound)
                } catch (_: Exception) {
                    // Keep the existing feed; a later scroll can retry.
                } finally {
                    loadingMore = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (language == AppLanguageStore.ENGLISH) "Shorts" else "শর্টস",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (language == AppLanguageStore.ENGLISH) "YouTube Shorts" else "YouTube Shorts ভিডিও",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                IconButton(onClick = {
                    if (!loadingMore) {
                        scope.launch {
                            loadingMore = true
                            try {
                                loadShorts(1)
                            } catch (e: Exception) {
                                error = e.message ?: if (language == AppLanguageStore.ENGLISH) "Could not refresh Shorts" else "শর্টস রিফ্রেশ করা যায়নি"
                            } finally {
                                loadingMore = false
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = if (language == AppLanguageStore.ENGLISH) "Refresh" else "রিফ্রেশ")
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (shorts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    error ?: if (language == AppLanguageStore.ENGLISH) "No Shorts found right now." else "এই মুহূর্তে কোনো Shorts পাওয়া যায়নি।",
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shorts, key = { it.url }) { video ->
                ShortsCard(video = video, onClick = { onVideoSelected(video) })
            }
            if (loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsCard(
    video: VideoResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            Box(
                Modifier
                    .width(126.dp)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                ) {
                    Text(
                        "SHORTS",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(video.title, maxLines = 4, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    video.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                )
            }
        }
    }
}
