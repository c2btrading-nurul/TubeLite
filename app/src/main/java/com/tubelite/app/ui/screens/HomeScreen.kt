package com.tubelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tubelite.app.data.SearchHistoryStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onVideoSelected: (VideoResult) -> Unit) {
    val context = LocalContext.current
    var recommended by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var trending by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val history = SearchHistoryStore.getRecent(context, 5)
                val personalized = mutableListOf<VideoResult>()
                for (q in history) {
                    try {
                        personalized += YoutubeRepository.search(q, maxItems = 20)
                    } catch (_: Exception) { /* skip a failed query */ }
                }
                val trendingList = YoutubeRepository.getTrending(maxItems = 40)
                recommended = personalized.distinctBy { it.url }
                trending = trendingList.filterNot { t -> recommended.any { it.url == t.url } }
            } catch (e: Exception) {
                error = e.message ?: "লোড করা যায়নি"
            } finally {
                loading = false
            }
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    error?.let {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (recommended.isNotEmpty()) {
            item {
                Text(
                    "আপনার আগ্রহ অনুযায়ী",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            items(recommended) { video -> HomeFeedCard(video) { onVideoSelected(video) } }
        }
        item {
            Text(
                "ট্রেন্ডিং",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
            )
        }
        items(trending) { video -> HomeFeedCard(video) { onVideoSelected(video) } }
    }
}

@Composable
private fun HomeFeedCard(video: VideoResult, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 16.dp)
    ) {
        VideoThumbnail(
            video = video,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(video.title, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    video.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
