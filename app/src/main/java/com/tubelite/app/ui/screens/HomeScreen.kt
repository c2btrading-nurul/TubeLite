package com.tubelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubelite.app.data.SearchHistoryStore
import com.tubelite.app.data.SubscriptionStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import kotlinx.coroutines.flow.collect

@Composable
fun HomeScreen(onVideoSelected: (VideoResult) -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var recommended by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var trending by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var subscriptionVideos by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadRound by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()

    suspend fun loadFeeds(round: Int) {
        val history = SearchHistoryStore.getRecent(context, 5)
        val personalized = mutableListOf<VideoResult>()
        for (q in history) {
            try {
                personalized += YoutubeRepository.search(q, maxItems = 20 * round)
            } catch (_: Exception) { }
        }

        val trendingList = YoutubeRepository.getTrending(maxItems = 40 * round)
        val subscribed = mutableListOf<VideoResult>()
        SubscriptionStore.getAll(context).forEach { sub ->
            try {
                subscribed += YoutubeRepository.getChannelVideos(sub.channelUrl, maxItems = 8 * round)
            } catch (_: Exception) { }
        }

        val newRecommended = personalized.distinctBy { it.url }
        val newSubscriptions = subscribed.distinctBy { it.url }
        val newTrending = trendingList.distinctBy { it.url }

        if (round == 1) {
            recommended = newRecommended
            subscriptionVideos = newSubscriptions
            trending = newTrending
        } else {
            val existingRecommended = recommended.mapTo(mutableSetOf()) { it.url }
            val existingSubscriptions = subscriptionVideos.mapTo(mutableSetOf()) { it.url }
            val existingTrending = trending.mapTo(mutableSetOf()) { it.url }

            recommended = recommended + newRecommended.filterNot { it.url in existingRecommended }
            subscriptionVideos = subscriptionVideos + newSubscriptions.filterNot { it.url in existingSubscriptions }
            trending = trending + newTrending.filterNot { it.url in existingTrending }
        }

        val subscriptionUrls = subscriptionVideos.mapTo(mutableSetOf()) { it.url }
        val recommendedUrls = recommended.mapTo(mutableSetOf()) { it.url }
        trending = trending
            .filterNot { it.url in recommendedUrls }
            .filterNot { it.url in subscriptionUrls }
            .distinctBy { it.url }
    }

    LaunchedEffect(Unit) {
        try {
            loadFeeds(1)
        } catch (e: Exception) {
            error = e.message ?: "লোড করা যায়নি"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 5
        }.collect { nearEnd ->
            if (nearEnd && !loading && !loadingMore) {
                loadingMore = true
                try {
                    loadRound += 1
                    loadFeeds(loadRound)
                } catch (_: Exception) {
                    // আগের ভিডিওগুলো রেখেই পরেরবার আবার চেষ্টা করা যাবে
                } finally {
                    loadingMore = false
                }
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        if (subscriptionVideos.isNotEmpty()) {
            item {
                Text(
                    "আপনার সাবস্ক্রিপশন থেকে",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            items(subscriptionVideos, key = { "subscription_${it.url}" }) { video ->
                HomeFeedCard(video) { onVideoSelected(video) }
            }
        }

        if (recommended.isNotEmpty()) {
            item {
                Text(
                    "আপনার আগ্রহ অনুযায়ী",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            items(recommended, key = { "recommended_${it.url}" }) { video ->
                HomeFeedCard(video) { onVideoSelected(video) }
            }
        }
        item {
            Text(
                "ট্রেন্ডিং",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
            )
        }
        items(trending, key = { "trending_${it.url}" }) { video ->
            HomeFeedCard(video) { onVideoSelected(video) }
        }
        if (loadingMore) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
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
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            VideoThumbnail(
                video = video,
                modifier = Modifier.fillMaxSize()
            )
        }
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

