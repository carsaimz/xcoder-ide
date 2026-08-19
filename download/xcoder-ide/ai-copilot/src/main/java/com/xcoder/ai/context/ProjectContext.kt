package com.xcoder.ai.context

import java.io.File

data class ProjectContext(
    val structure: String = "",
    val openFiles: List<String> = emptyList(),
    val recentChanges: List<FileChange> = emptyList(),
    val buildErrors: List<String> = emptyList(),
    val gitBranch: String = "main",
    val selectedCode: String? = null
)