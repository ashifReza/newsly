package com.example.newsly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.newsly.data.Article
import com.example.newsly.ui.components.Chip
import com.example.newsly.ui.components.GroupedRow
import com.example.newsly.ui.components.NewslyIcons
import com.example.newsly.ui.components.PrimaryButton
import com.example.newsly.ui.components.StoryActions
import com.example.newsly.ui.components.groupPosition
import com.example.newsly.ui.theme.Newsly

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    recentSearches: List<String>,
    savedUrls: Set<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onClearRecent: () -> Unit,
    storyActions: (Article) -> StoryActions,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val c = Newsly.colors

    fun submit(query: String) {
        keyboard?.hide()
        onSearch(query)
    }

    // Ready to type straight away, unless returning to earlier results.
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) focusRequester.requestFocus()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Search", style = MaterialTheme.typography.displaySmall, color = c.ink)

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(c.surface, RoundedCornerShape(999.dp))
                    .border(2.dp, c.ink, RoundedCornerShape(999.dp))
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NewslyIcons.Search, null, tint = c.ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) {
                        Text("Search any topic", style = MaterialTheme.typography.bodyLarge, color = c.muted)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(state.query) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { contentDescription = "Search news" },
                    )
                }
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onClear()
                            focusRequester.requestFocus()
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(Modifier.size(36.dp).background(c.soft, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(NewslyIcons.Close, "Clear search", tint = c.ink, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            if (recentSearches.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.muted,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp),
                    )
                    recentSearches.forEach { Chip(it, selected = it.equals(state.searchedQuery, ignoreCase = true), onClick = { submit(it) }, height = 36.dp) }
                    TextButton(onClick = onClearRecent, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium, color = c.accentText)
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
            when (val results = state.results) {
                null -> if (recentSearches.isEmpty()) {
                    Hint(
                        "Search any topic",
                        "Results as you type: stories from the past week across thousands of news sources.",
                    )
                }
                FeedState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.accent)
                is FeedState.Error -> Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(results.message, style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
                    PrimaryButton("Try again", onRetry, Modifier.fillMaxWidth(0.6f), trailing = null)
                }
                is FeedState.Success -> if (results.articles.isEmpty()) {
                    Hint("No stories found", "Nothing from the past week matches \"${state.searchedQuery}\". Try other words.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp)) {
                        item(key = "header") {
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                                Text("${results.articles.size} stories", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
                                Text("Past week · newest first", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), color = c.muted)
                            }
                        }
                        itemsIndexed(results.articles, key = { _, a -> a.url }) { i, article ->
                            GroupedRow(
                                article = article,
                                position = groupPosition(i, results.articles.size),
                                saved = article.url in savedUrls,
                                actions = storyActions(article),
                            )
                        }
                    }
                }
            }
            if (state.searching && state.results is FeedState.Success) {
                // Newer results are on the way; the old ones stay visible meanwhile.
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = c.accent, trackColor = c.soft)
            }
        }
    }
}

@Composable
private fun Hint(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Newsly.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Newsly.colors.muted, textAlign = TextAlign.Center)
    }
}
