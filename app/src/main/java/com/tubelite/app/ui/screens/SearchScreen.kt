package com.tubelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var history by remember { mutableStateOf(SearchHistoryStore.getRecent(context, 15)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runSearch(value: String = query) {
        val q = value.trim()
        if (q.isEmpty()) return
        query = q
        loading = true
        error = null
        scope.launch {
            try {
                results = YoutubeRepository.search(q)
                SearchHistoryStore.add(context, q)
                history = SearchHistoryStore.getRecent(context, 15)
            } catch (e: Exception) {
                error = e.message ?: "সার্চ করা যায়নি"
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("ভিডিও সার্চ করুন...") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { runSearch() }
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { runSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (query.isBlank() && results.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "সার্চ হিস্ট্রি",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (history.isNotEmpty()) {
                            TextButton(onClick = {
                                SearchHistoryStore.clear(context)
                                history = emptyList()
                            }) {
                                Text("সব মুছুন")
                            }
                        }
                    }
                }

                if (history.isEmpty()) {
                    item {
                        Text(
                            "আপনার সাম্প্রতিক সার্চ এখানে দেখা যাবে",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                } else {
                    items(history, key = { "history_$it" }) { item ->
                        SearchHistoryRow(
                            query = item,
                            onSelect = { runSearch(item) },
                            onDelete = {
                                SearchHistoryStore.remove(context, item)
                                history = SearchHistoryStore.getRecent(context, 15)
                            }
                        )
                    }
                }
            }

            if (loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (results.isNotEmpty()) {
                item {
                    Text(
                        "সার্চ রেজাল্ট",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                items(results, key = { it.url }) { video ->
                    VideoRow(video) { onVideoSelected(video) }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(query, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "এই সার্চটি মুছুন")
        }
    }
}

@Composable
private fun VideoRow(video: VideoResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        VideoThumbnail(
            video = video,
            modifier = Modifier
                .width(148.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(video.title, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                video.uploaderName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
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
    var history by remember { mutableStateOf(SearchHistoryStore.getRecent(context, 15)) }
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
                history = SearchHistoryStore.getRecent(context, 15)
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
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (query.isBlank() && results.isEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("সার্চ হিস্ট্রি", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    TextButton(onClick = { SearchHistoryStore.clear(context); history = emptyList() }) { Text("সব মুছুন") }
                }
            }
            history.forEach { item ->
                SearchHistoryRow(
                    query = item,
                    onSelect = { runSearch(item) },
                    onDelete = {
                        SearchHistoryStore.remove(context, item)
                        history = SearchHistoryStore.getRecent(context, 15)
                    }
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        if (results.isNotEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.url }) { video ->
                    VideoRow(video) {
                        onVideoSelected(video)
                        onClose()
                    }
                }
            }
        }
    }
}
