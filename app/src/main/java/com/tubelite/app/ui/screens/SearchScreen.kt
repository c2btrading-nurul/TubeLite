package com.tubelite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tubelite.app.data.SearchHistoryStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onVideoSelected: (VideoResult) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (query.isBlank()) return
        loading = true
        error = null
        scope.launch {
            try {
                results = YoutubeRepository.search(query)
                SearchHistoryStore.add(context, query)
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ভিডিও সার্চ করুন...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { runSearch() }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { video ->
                VideoRow(video) { onVideoSelected(video) }
            }
        }
    }
}

@Composable
private fun VideoRow(video: VideoResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        VideoThumbnail(
            video = video,
            modifier = Modifier
                .width(140.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onVideoSelected: (VideoResult) -> Unit,
    searchRequestToken: Int = 0,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(SearchHistoryStore.getRecent(context, 10)) }
    val scope = rememberCoroutineScope()

    fun runSearch(searchQuery: String = query) {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        onQueryChange(q)
        loading = true
        error = null
        scope.launch {
            try {
                results = YoutubeRepository.search(q)
                SearchHistoryStore.add(context, q)
                history = SearchHistoryStore.getRecent(context, 10)
            } catch (e: Exception) {
                error = e.message ?: "সার্চ করা যায়নি"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(searchRequestToken) {
        if (searchRequestToken > 0) runSearch()
    }

    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (query.isBlank() && results.isEmpty()) {
            if (history.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "সার্চ হিস্ট্রি",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        SearchHistoryStore.clear(context)
                        history = emptyList()
                    }) {
                        Text("সব মুছুন")
                    }
                }

                history.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { runSearch(item) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(item, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            SearchHistoryStore.remove(context, item)
                            history = SearchHistoryStore.getRecent(context, 10)
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "এই সার্চটি মুছুন")
                        }
                    }
                }
            } else {
                Text(
                    "আপনার সাম্প্রতিক সার্চ এখানে দেখা যাবে",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (loading) {
            Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }

        if (results.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results) { video ->
                    VideoRow(video) {
                        onVideoSelected(video)
                        onClose()
                    }
                }
            }
        }
    }
}
