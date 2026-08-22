package com.xcoder.core.git

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositoryProvider @Inject constructor() {

    private var gitInstance: Git? = null
    private var _workingDirectory: String = ""

    private val _currentBranch = MutableStateFlow("")
    val currentBranch: StateFlow<String> = _currentBranch.asStateFlow()

    private val _isRepositoryOpen = MutableStateFlow(false)
    val isRepositoryOpen: StateFlow<Boolean> = _isRepositoryOpen.asStateFlow()

    fun setGit(git: Git, workingDirectory: String) {
        close()
        gitInstance = git
        _workingDirectory = workingDirectory
        _currentBranch.value = try { git.repository.branch ?: "HEAD" } catch (_: Exception) { "HEAD" }
        _isRepositoryOpen.value = true
    }

    fun getGit(): Git? = gitInstance

    fun getRepository(): Repository? = gitInstance?.repository

    fun getWorkingDirectory(): String = _workingDirectory

    fun getGitDirectory(): String {
        return if (_workingDirectory.isNotBlank()) {
            File(_workingDirectory, ".git").absolutePath
        } else {
            ""
        }
    }

    fun updateCurrentBranch(branch: String) {
        _currentBranch.value = branch
    }

    fun refreshBranch() {
        gitInstance?.let { git ->
            try {
                _currentBranch.value = git.repository.branch ?: "HEAD"
            } catch (_: Exception) {
                _currentBranch.value = "HEAD"
            }
        }
    }

    fun close() {
        gitInstance?.repository?.close()
        gitInstance = null
        _workingDirectory = ""
        _currentBranch.value = ""
        _isRepositoryOpen.value = false
    }

    fun getRepositoryName(): String {
        return File(_workingDirectory).name
    }

    fun isClean(): Boolean {
        return try {
            val git = gitInstance ?: return false
            git.status().call().isClean
        } catch (_: Exception) {
            false
        }
    }

    fun getHeadRevision(): String? {
        return try {
            gitInstance?.repository?.resolve("HEAD")?.name
        } catch (_: Exception) {
            null
        }
    }
}
