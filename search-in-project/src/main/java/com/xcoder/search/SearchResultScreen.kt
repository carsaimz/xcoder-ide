package com.xcoder.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreen(
    projectUri: Uri,
    onOpenFile: (filePath: String, line: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchInProjectViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val replaceQuery by viewModel.replaceQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val extInput = remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(tonalElevation = 2.dp) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = viewModel::setQuery,
                        modifier = Modifier.weight(1f), placeholder = { Text("Search in project...") },
                        singleLine = true, trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    IconButton(onClick = { viewModel.search(projectUri) }, enabled = query.isNotBlank() && searchState != SearchState.SEARCHING) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(
                        value = replaceQuery, onValueChange = viewModel::setReplaceQuery,
                        modifier = Modifier.weight(1f), placeholder = { Text("Replace...") },
                        singleLine = true
                    )
                    TextButton(onClick = { viewModel.replaceAllInProject(projectUri) { viewModel.search(projectUri) } }, enabled = result.totalMatches > 0) {
                        Text("Replace All")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = extInput.value, onValueChange = { extInput.value = it },
                        modifier = Modifier.width(160.dp), placeholder = { Text("ext: kt,java") },
                        singleLine = true, textStyle = MaterialTheme.typography.labelSmall
                    )
                    Button(onClick = {
                        val exts = extInput.value.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                        viewModel.updateFilter { it.copy(includeExtensions = exts) }
                    }, modifier = Modifier.height(32.dp)) {
                        Text("Filter", style = MaterialTheme.typography.labelSmall)
                    }
                    FilterChip(selected = filter.caseSensitive, onClick = { viewModel.updateFilter { it.copy(caseSensitive = !it.caseSensitive) } }, label = { Text("Aa", style = MaterialTheme.typography.labelSmall) })
                    FilterChip(selected = filter.useRegex, onClick = { viewModel.updateFilter { it.copy(useRegex = !it.useRegex) } }, label = { Text(".*", style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
        when (searchState) {
            SearchState.SEARCHING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SearchState.ERROR -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            SearchState.DONE, SearchState.IDLE -> {
                if (result.isEmpty && searchState == SearchState.DONE) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (result.totalMatches > 0) {
                            item {
                                Text(
                                    "${result.totalMatches} results in ${result.totalFiles} files (${result.elapsedMs}ms)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        result.fileResults.forEach { (filePath, matches) ->
                            item {
                                val fileName = filePath.substringAfterLast('/')
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(fileName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${matches.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            items(matches, key = { "$filePath-${it.line}-${it.startCol}" }) { match ->
                                val annotatedLine = buildAnnotatedString {
                                    val before = match.lineText.substring(0, match.startCol.coerceAtMost(match.lineText.length))
                                    val matched = match.matchedText
                                    val after = match.lineText.substring(match.endCol.coerceAtMost(match.lineText.length))
                                    if (before.isNotEmpty()) {
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append(before) }
                                    }
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))) {
                                        append(matched)
                                    }
                                    if (after.isNotEmpty()) {
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append(after) }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenFile(filePath, match.line) }.padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${match.line}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.width(40.dp)
                                    )
                                    Text(
                                        annotatedLine, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
