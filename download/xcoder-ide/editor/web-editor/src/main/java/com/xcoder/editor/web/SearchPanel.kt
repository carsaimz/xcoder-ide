package com.xcoder.editor.web

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SearchMatch(
    val line: Int,
    val startCol: Int,
    val endCol: Int,
    val matchedText: String,
    val lineText: String,
    val contextBefore: String = "",
    val contextAfter: String = ""
) {
    val displayLine: Int get() = line + 1

    companion object {
        fun fromMatch(line: Int, startCol: Int, endCol: Int, lineText: String, contextRadius: Int = 20): SearchMatch {
            val safeEnd = endCol.coerceAtMost(lineText.length)
            val safeStart = startCol.coerceIn(0, lineText.length)
            val matchedText = lineText.substring(safeStart, safeEnd)
            val ctxStart = (startCol - contextRadius).coerceAtLeast(0)
            val ctxEnd = (endCol + contextRadius).coerceAtMost(lineText.length)
            return SearchMatch(
                line = line, startCol = safeStart, endCol = safeEnd,
                matchedText = matchedText, lineText = lineText,
                contextBefore = lineText.substring(ctxStart, safeStart),
                contextAfter = lineText.substring(safeEnd, ctxEnd)
            )
        }
    }
}

data class SearchOptions(
    val regex: Boolean = false,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false
)

data class SearchResult(
    val matches: List<SearchMatch>,
    val query: String,
    val elapsedTimeMs: Long = 0
) {
    val matchCount: Int get() = matches.size
    val hasMatches: Boolean get() = matches.isNotEmpty()
}

object SearchEngine {
    fun search(content: String, query: String, options: SearchOptions = SearchOptions(), maxResults: Int = 0): SearchResult {
        val startTime = System.currentTimeMillis()
        if (query.isBlank()) return SearchResult(emptyList(), query, 0)
        val lines = content.split('\n')
        val matches = mutableListOf<SearchMatch>()
        val pattern = when {
            options.regex -> query
            options.wholeWord -> "\\b${Regex.escape(query)}\\b"
            else -> Regex.escape(query)
        }
        val flags = if (options.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
        val regex: Regex?
        try { regex = Regex(pattern, flags) } catch (_: Exception) { return SearchResult(emptyList(), query, 0) }
        for (i in lines.indices) {
            val lineText = lines[i]
            for (match in regex!!.findAll(lineText)) {
                matches.add(SearchMatch.fromMatch(i, match.range.first, match.range.last + 1, lineText))
                if (maxResults > 0 && matches.size >= maxResults) break
            }
            if (maxResults > 0 && matches.size >= maxResults) break
        }
        return SearchResult(matches, query, System.currentTimeMillis() - startTime)
    }

    fun replaceOne(content: String, match: SearchMatch, replacement: String): String {
        val lines = content.split('\n').toMutableList()
        if (match.line !in lines.indices) return content
        val lineText = lines[match.line]
        val before = lineText.substring(0, match.startCol)
        val after = lineText.substring(match.endCol)
        lines[match.line] = before + replacement + after
        return lines.joinToString("\n")
    }

    fun replaceAll(content: String, query: String, replacement: String, options: SearchOptions = SearchOptions()): String {
        if (query.isBlank()) return content
        return try {
            val pattern = when {
                options.regex -> query
                options.wholeWord -> "\\b${Regex.escape(query)}\\b"
                else -> Regex.escape(query)
            }
            val flags = if (options.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
            Regex(pattern, flags).replace(content, replacement)
        } catch (_: Exception) { content }
    }
}

@Composable
fun SearchPanel(
    visible: Boolean,
    searchQuery: String,
    replaceText: String,
    options: SearchOptions,
    searchResult: SearchResult,
    selectedMatchIndex: Int,
    onSearchQueryChange: (String) -> Unit,
    onReplaceTextChange: (String) -> Unit,
    onOptionsChange: (SearchOptions) -> Unit,
    onSearch: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onMatchClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(animationSpec = tween(100)),
        exit = shrinkVertically() + fadeOut(animationSpec = tween(80)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { onSearchQueryChange(it); onSearch() },
                        modifier = Modifier.weight(1f).height(36.dp).focusRequester(focusRequester),
                        placeholder = { Text("Find...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true, textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Text(
                        text = when {
                            searchQuery.isBlank() -> ""
                            searchResult.hasMatches -> "${selectedMatchIndex + 1} of ${searchResult.matchCount}"
                            else -> "No results"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(80.dp)
                    )
                    IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Previous", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Next", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = replaceText, onValueChange = onReplaceTextChange,
                        modifier = Modifier.weight(1f).height(36.dp),
                        placeholder = { Text("Replace...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        singleLine = true, textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    TextButton(onClick = onReplace, enabled = searchResult.hasMatches && selectedMatchIndex >= 0) {
                        Text("Replace", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onReplaceAll, enabled = searchResult.hasMatches) {
                        Text("Replace All", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchToggleChip(".*", "Regular Expression", options.regex) { onOptionsChange(options.copy(regex = !options.regex)) }
                    SearchToggleChip("Aa", "Match Case", options.caseSensitive) { onOptionsChange(options.copy(caseSensitive = !options.caseSensitive)) }
                    SearchToggleChip("Ab", "Whole Word", options.wholeWord) { onOptionsChange(options.copy(wholeWord = !options.wholeWord)) }
                }
                if (searchResult.hasMatches) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(searchResult.matches, key = { i, _ -> i }) { index, match ->
                            SearchResultItem(match, index == selectedMatchIndex) { onMatchClick(index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchToggleChip(label: String, tooltip: String, isActive: Boolean, onClick: () -> Unit) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(4.dp), color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal),
            color = contentColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SearchResultItem(match: SearchMatch, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(backgroundColor).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${match.displayLine}:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.width(36.dp))
        val annotatedLine = buildAnnotatedString {
            if (match.contextBefore.isNotEmpty()) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) {
                    append(truncateEnd(match.contextBefore, 15))
                }
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
                append(truncateEnd(match.matchedText, 40))
            }
            if (match.contextAfter.isNotEmpty()) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) {
                    append(truncateEnd(match.contextAfter, 15))
                }
            }
        }
        Text(annotatedLine, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("col ${match.startCol + 1}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    }
}

private fun truncateEnd(text: String, maxLen: Int): String = if (text.length <= maxLen) text else text.substring(0, maxLen - 3) + "..."
