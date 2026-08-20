package com.tubelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubelite.app.data.ChannelInfo
import com.tubelite.app.data.Subscription
import com.tubelite.app.data.SubscriptionStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository

@Composable
fun ChannelScreen(channelUrl: String, onVideoSelected: (VideoResult) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var channel by remember(channelUrl) { mutableStateOf<ChannelInfo?>(null) }
    var loading by remember(channelUrl) { mutableStateOf(true) }
    var error by remember(channelUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(channelUrl) {
        loading = true
        error = null
        try {
            channel = YoutubeRepository.getChannel(channelUrl)
        } catch (e: Exception) {
            error = "চ্যানেল লোড করা যায়নি: ${e.message}"
        } finally {
            loading = false
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    error?.let {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val c = channel ?: return

    var subscribed by remember(channelUrl) { mutableStateOf(SubscriptionStore.isSubscribed(context, channelUrl)) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                if (c.avatarUrl != null) {
                    AsyncImage(model = c.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = {
                    if (subscribed) {
                        SubscriptionStore.unsubscribe(context, channelUrl)
                    } else {
                        SubscriptionStore.subscribe(
                            context,
                            Subscription(channelUrl, c.name, c.avatarUrl)
                        )
                    }
                    subscribed = !subscribed
                }
            ) {
                Text(if (subscribed) "সাবস্ক্রাইব করা" else "সাবস্ক্রাইব")
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(c.videos) { v ->
                Row(Modifier.fillMaxWidth().clickable { onVideoSelected(v) }) {
                    AsyncImage(model = v.thumbnailUrl, contentDescription = null, modifier = Modifier.width(140.dp).height(80.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
