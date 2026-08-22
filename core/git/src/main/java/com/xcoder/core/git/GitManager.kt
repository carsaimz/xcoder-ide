package com.xcoder.core.git

import android.util.Log
import com.xcoder.core.git.GitResult.GitError
import com.xcoder.core.git.GitResult.GitSuccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class GitResult<out T> {
    data class GitSuccess<T>(val data: T) : GitResult<T>()
    data class GitError(val message: String, val exception: Throwable? = null) : GitResult<Nothing>()
}

data class GitStatusInfo(
    val added: List<String>,
    val modified: List<String>,
    val deleted: List<String>,
    val untracked: List<String>,
    val conflicted: List<String>,
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val isClean: Boolean
)

data class CommitInfo(
    val hash: String,
    val shortHash: String,
    val authorName: String,
    val authorEmail: String,
    val message: String,
    val fullMessage: String,
    val commitTime: Long
)

data class DiffInfo(
    val oldPath: String,
    val newPath: String,
    val changeType: String,
    val additions: Int,
    val deletions: Int,
    val patch: String
)

data class BranchInfo(
    val name: String,
    val isCurrent: Boolean,
    val isRemote: Boolean,
    val ahead: Int = 0,
    val behind: Int = 0,
    val trackingBranch: String? = null
)

data class RemoteInfo(
    val name: String,
    val url: String,
    val fetchUrl: String?,
    val pushUrl: String?
)

data class StashEntry(
    val index: Int,
    val message: String,
    val authorName: String,
    val commitTime: Long
)

data class GitLogResult(
    val commits: List<CommitInfo>,
    val totalCount: Int
)

enum class GitEventType {
    REPO_INITIALIZED, BRANCH_CHANGED, COMMIT_CREATED, PUSH_COMPLETED,
    PULL_COMPLETED, MERGE_COMPLETED, CONFLICT_DETECTED, STASH_APPLIED
}

data class GitEvent(val type: GitEventType, val message: String, val timestamp: Long)

@Singleton
class GitManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val repositoryProvider: RepositoryProvider,
    private val credentials: GitCredentials
) {
    private val _events = MutableSharedFlow<GitEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GitEvent> = _events.asSharedFlow()

    suspend fun init(directory: String): GitResult<String> = withContext(Dispatchers.IO) {
        try {
            val repoDir = File(directory)
            if (!repoDir.exists()) {
                repoDir.mkdirs()
            }
            val git = Git.init().setDirectory(repoDir).call()
            repositoryProvider.setGit(git, directory)
            val branch = git.repository.branch
            emitEvent(GitEventType.REPO_INITIALIZED, "Repository initialized at $directory")
            GitSuccess(branch)
        } catch (e: Exception) {
            GitError("Failed to initialize repository: ${e.message}", e)
        }
    }

    suspend fun clone(
        remoteUrl: String,
        destination: String,
        branch: String? = null
    ): GitResult<String> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destination)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            val cloneCommand = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(destDir)
                .setCloneAllBranches(true)
                .setCredentialsProvider(credentials.getCredentialProvider())
            branch?.let { cloneCommand.setBranch(it) }
            val git = cloneCommand.call()
            repositoryProvider.setGit(git, destination)
            val currentBranch = git.repository.branch
            emitEvent(GitEventType.REPO_INITIALIZED, "Cloned $remoteUrl to $destination")
            GitSuccess(currentBranch)
        } catch (e: Exception) {
            GitError("Failed to clone repository: ${e.message}", e)
        }
    }

    suspend fun add(filePatterns: List<String>): GitResult<Unit> = withGit { git ->
        val addCommand = git.add()
        for (pattern in filePatterns) {
            addCommand.addFilepattern(pattern)
        }
        addCommand.call()
        GitSuccess(Unit)
    }

    suspend fun addAll(): GitResult<Unit> = withGit { git ->
        git.add().addFilepattern(".").call()
        GitSuccess(Unit)
    }

    suspend fun commit(message: String): GitResult<CommitInfo> = withGit { git ->
        val revCommit = git.commit()
            .setMessage(message)
            .call()
        val info = revCommit.toCommitInfo()
        emitEvent(GitEventType.COMMIT_CREATED, "Commit: ${info.shortHash} - $message")
        GitSuccess(info)
    }

    suspend fun commit(message: String, authorName: String, authorEmail: String): GitResult<CommitInfo> =
        withGit { git ->
            val revCommit = git.commit()
                .setMessage(message)
                .setAuthor(authorName, authorEmail)
                .call()
            val info = revCommit.toCommitInfo()
            emitEvent(GitEventType.COMMIT_CREATED, "Commit: ${info.shortHash} - $message")
            GitSuccess(info)
        }

    suspend fun push(remote: String = "origin", branch: String? = null, force: Boolean = false): GitResult<Unit> =
        withGit { git ->
            val pushCommand = git.push()
                .setRemote(remote)
                .setCredentialsProvider(credentials.getCredentialProvider())
                .setForce(force)
            branch?.let {
                pushCommand.setRefSpecs(org.eclipse.jgit.transport.RefSpec("refs/heads/$it:refs/heads/$it"))
            }
            pushCommand.call()
            emitEvent(GitEventType.PUSH_COMPLETED, "Pushed to $remote")
            GitSuccess(Unit)
        }

    suspend fun pull(remote: String = "origin", branch: String? = null): GitResult<Unit> =
        withGit { git ->
            val pullCommand = git.pull()
                .setRemote(remote)
                .setCredentialsProvider(credentials.getCredentialProvider())
            branch?.let { pullCommand.setRemoteBranchName(it) }
            val result = pullCommand.call()
            if (result.mergeResult.mergeStatus.isSuccessful) {
                emitEvent(GitEventType.PULL_COMPLETED, "Pulled from $remote")
                GitSuccess(Unit)
            } else {
                val conflicts = result.mergeResult.conflicts
                if (conflicts != null) {
                    emitEvent(GitEventType.CONFLICT_DETECTED, "Merge conflicts detected during pull")
                    GitError("Merge conflicts detected: ${conflicts.size} files")
                } else {
                    emitEvent(GitEventType.PULL_COMPLETED, "Pulled from $remote (fast-forward)")
                    GitSuccess(Unit)
                }
            }
        }

    suspend fun status(): GitResult<GitStatusInfo> = withGit { git ->
        val status = git.status().call()
        val currentBranch = git.repository.branch ?: "HEAD"
        var ahead = 0
        var behind = 0
        try {
            val trackingStatus = BranchTrackingStatus.of(git.repository, currentBranch)
            if (trackingStatus != null) {
                ahead = trackingStatus.aheadCount
                behind = trackingStatus.behindCount
            }
        } catch (_: Exception) {
        }
        val info = GitStatusInfo(
            added = status.added.toList(),
            modified = status.modified.toList(),
            deleted = status.removed.toList(),
            untracked = status.untracked.toList(),
            conflicted = status.conflicting.toList(),
            branch = currentBranch,
            ahead = ahead,
            behind = behind,
            isClean = status.isClean
        )
        GitSuccess(info)
    }

    suspend fun log(maxCount: Int = 50, skip: Int = 0): GitResult<GitLogResult> = withGit { git ->
        val logCommand = git.log()
            .setMaxCount(maxCount)
            .setSkip(skip)
        val commits = logCommand.call().map { it.toCommitInfo() }
        GitSuccess(GitLogResult(commits, commits.size))
    }

    suspend fun logForFile(path: String, maxCount: Int = 20): GitResult<List<CommitInfo>> =
        withGit { git ->
            val commits = git.log()
                .addPath(path)
                .setMaxCount(maxCount)
                .call()
                .map { it.toCommitInfo() }
            GitSuccess(commits)
        }

    suspend fun diff(): GitResult<List<DiffInfo>> = withGit { git ->
        val diffs = mutableListOf<DiffInfo>()
            val entries = git.diff().call()
            for (entry in entries) {
                val baos = java.io.ByteArrayOutputStream()
                val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
                formatter.setRepository(git.repository)
                formatter.format(entry)
                val patch = baos.toString("UTF-8")
                formatter.close()
                val additions = patch.lines().count { it.startsWith("+") && !it.startsWith("+++") }
                val deletions = patch.lines().count { it.startsWith("-") && !it.startsWith("---") }
                diffs.add(
                    DiffInfo(
                        oldPath = entry.oldPath,
                        newPath = entry.newPath,
                        changeType = entry.changeType.name,
                        additions = additions,
                        deletions = deletions,
                        patch = patch
                    )
                )
            }
            GitSuccess(diffs)
        }

    suspend fun diffStaged(): GitResult<List<DiffInfo>> = withGit { git ->
        val diffs = mutableListOf<DiffInfo>()
        val entries = git.diff().setCached(true).call()
        for (entry in entries) {
            val baos = java.io.ByteArrayOutputStream()
            val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
            formatter.setRepository(git.repository)
            formatter.format(entry)
            val patch = baos.toString("UTF-8")
            formatter.close()
            val additions = patch.lines().count { it.startsWith("+") && !it.startsWith("+++") }
            val deletions = patch.lines().count { it.startsWith("-") && !it.startsWith("---") }
            diffs.add(
                DiffInfo(
                    oldPath = entry.oldPath,
                    newPath = entry.newPath,
                    changeType = entry.changeType.name,
                    additions = additions,
                    deletions = deletions,
                    patch = patch
                )
            )
        }
        GitSuccess(diffs)
    }

    suspend fun diffFile(oldPath: String, newPath: String): GitResult<DiffInfo> = withGit { git ->
        val baos = java.io.ByteArrayOutputStream()
        val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
        formatter.setRepository(git.repository)
        val oldTreeIter = if (oldPath == "/dev/null") {
            org.eclipse.jgit.treewalk.EmptyTreeIterator()
        } else {
            val headTree = git.repository.resolve("HEAD^{tree}")
            org.eclipse.jgit.treewalk.CanonicalTreeParser().apply {
                reset(git.repository.newObjectReader(), headTree)
            }
        }
        val newTreeIter = org.eclipse.jgit.treewalk.FileTreeIterator(git.repository)
        formatter.format(oldTreeIter, newTreeIter)
        val patch = baos.toString("UTF-8")
        formatter.close()
        val additions = patch.lines().count { it.startsWith("+") && !it.startsWith("+++") }
        val deletions = patch.lines().count { it.startsWith("-") && !it.startsWith("---") }
        GitSuccess(
            DiffInfo(
                oldPath = oldPath,
                newPath = newPath,
                changeType = "MODIFY",
                additions = additions,
                deletions = deletions,
                patch = patch
            )
        )
    }

    suspend fun branch(): GitResult<List<BranchInfo>> = withGit { git ->
        val branches = mutableListOf<BranchInfo>()
        val currentBranch = try { git.repository.branch } catch (_: Exception) { null }
        git.branchList().call().forEach { ref ->
            val name = org.eclipse.jgit.lib.Repository.shortenRefName(ref.name)
            branches.add(
                BranchInfo(
                    name = name,
                    isCurrent = name == currentBranch,
                    isRemote = false
                )
            )
        }
        try {
            git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call()
                .forEach { ref ->
                    val name = org.eclipse.jgit.lib.Repository.shortenRefName(ref.name)
                    branches.add(
                        BranchInfo(
                            name = name,
                            isCurrent = false,
                            isRemote = true
                        )
                    )
                }
        } catch (_: Exception) {
        }
        GitSuccess(branches)
    }

    suspend fun createBranch(branchName: String): GitResult<String> = withGit { git ->
        git.branchCreate()
            .setName(branchName)
            .call()
        GitSuccess(branchName)
    }

    suspend fun deleteBranch(branchName: String, force: Boolean = false): GitResult<Unit> =
        withGit { git ->
            git.branchDelete()
                .setBranchNames(branchName)
                .setForce(force)
                .call()
            GitSuccess(Unit)
        }

    suspend fun checkout(branchName: String): GitResult<String> = withGit { git ->
        git.checkout()
            .setName(branchName)
            .call()
        val currentBranch = git.repository.branch
        repositoryProvider.updateCurrentBranch(currentBranch)
        emitEvent(GitEventType.BRANCH_CHANGED, "Switched to branch: $currentBranch")
        GitSuccess(currentBranch)
    }

    suspend fun checkoutNewBranch(branchName: String): GitResult<String> = withGit { git ->
        git.checkout()
            .setCreateBranch(true)
            .setName(branchName)
            .call()
        val currentBranch = git.repository.branch
        repositoryProvider.updateCurrentBranch(currentBranch)
        emitEvent(GitEventType.BRANCH_CHANGED, "Created and switched to branch: $currentBranch")
        GitSuccess(currentBranch)
    }

    suspend fun remotes(): GitResult<List<RemoteInfo>> = withGit { git ->
        val remotes = git.remoteList().call().map { config ->
            RemoteInfo(
                name = config.name,
                url = config.urIs.firstOrNull()?.toString() ?: "",
                fetchUrl = config.urIs.firstOrNull()?.toString(),
                pushUrl = config.pushURIs.firstOrNull()?.toString()
            )
        }
        GitSuccess(remotes)
    }

    suspend fun addRemote(name: String, url: String): GitResult<Unit> = withGit { git ->
        git.remoteAdd()
            .setName(name)
            .setUri(URIish(url))
            .call()
        GitSuccess(Unit)
    }

    suspend fun removeRemote(name: String): GitResult<Unit> = withGit { git ->
        git.remoteRemove()
            .setRemoteName(name)
            .call()
        GitSuccess(Unit)
    }

    suspend fun fetch(remote: String = "origin"): GitResult<Unit> = withGit { git ->
        git.fetch()
            .setRemote(remote)
            .setCredentialsProvider(credentials.getCredentialProvider())
            .call()
        GitSuccess(Unit)
    }

    suspend fun stash(message: String? = null): GitResult<Unit> = withGit { git ->
        val stashCommand = git.stashCreate()
        if (message != null) stashCommand.setWorkingDirectoryMessage(message)
        stashCommand.call()
        GitSuccess(Unit)
    }

    suspend fun stashList(): GitResult<List<StashEntry>> = withGit { git ->
        val entries = mutableListOf<StashEntry>()
        try {
            val stashRef = git.repository.findRef("refs/stash")
            if (stashRef != null) {
                val walk = org.eclipse.jgit.revwalk.RevWalk(git.repository)
                var ref = stashRef
                var index = 0
                while (ref != null) {
                    val commit = walk.parseCommit(ref.objectId)
                    entries.add(
                        StashEntry(
                            index = index,
                            message = commit.shortMessage,
                            authorName = commit.authorIdent.name,
                            commitTime = commit.commitTime * 1000L
                        )
                    )
                    ref = ref.objectId?.let { walk.parseCommit(it).parents?.getOrNull(0)?.let { parent -> git.repository.findRef(parent.name) } }
                    if (ref == null) {
                        try {
                            val nextId = commit.parents?.getOrNull(0)?.id ?: break
                            val nextRef = git.repository.findRef("refs/stash@{$index}")
                            ref = if (nextRef != null && nextRef.objectId != stashRef.objectId) nextRef else null
                        } catch (_: Exception) {
                            break
                        }
                    }
                    index++
                }
                walk.dispose()
            }
        } catch (_: Exception) {
        }
        GitSuccess(entries)
    }

    suspend fun stashApply(index: Int = 0): GitResult<Unit> = withGit { git ->
        git.stashApply().setStashRef("refs/stash@{$index}").call()
        emitEvent(GitEventType.STASH_APPLIED, "Applied stash $index")
        GitSuccess(Unit)
    }

    suspend fun stashPop(index: Int = 0): GitResult<Unit> = withGit { git ->
        git.stashApply().setStashRef("refs/stash@{$index}").call()
        try {
            val refName = "refs/stash@{$index}"
            val refUpdate = git.repository.updateRef(refName)
            refUpdate.setForceUpdate(true)
            val stashRef = git.repository.findRef(refName)
            if (stashRef != null) {
                val walk = org.eclipse.jgit.revwalk.RevWalk(git.repository)
                val commit = walk.parseCommit(stashRef.objectId)
                refUpdate.setNewObjectId(commit.parents?.getOrNull(0)?.id ?: stashRef.objectId)
                refUpdate.delete()
                walk.dispose()
            }
        } catch (_: Exception) {
        }
        emitEvent(GitEventType.STASH_APPLIED, "Popped stash $index")
        GitSuccess(Unit)
    }

    suspend fun stashDrop(index: Int = 0): GitResult<Unit> = withGit { git ->
        try {
            val refName = "refs/stash@{$index}"
            val refUpdate = git.repository.updateRef(refName)
            refUpdate.setForceUpdate(true)
            refUpdate.delete()
        } catch (e: Exception) {
            return@withGit GitError("Failed to drop stash: ${e.message}", e)
        }
        GitSuccess(Unit)
    }

    suspend fun stashClear(): GitResult<Unit> = withGit { git ->
        try {
            val refUpdate = git.repository.updateRef("refs/stash")
            refUpdate.setForceUpdate(true)
            refUpdate.delete()
        } catch (e: Exception) {
            return@withGit GitError("Failed to clear stash: ${e.message}", e)
        }
        GitSuccess(Unit)
    }

    suspend fun merge(branchName: String, ff: FastForwardMode = FastForwardMode.FF): GitResult<String> =
        withGit { git ->
            val result = git.merge()
                .include(git.repository.findRef(branchName))
                .setFastForward(ff)
                .call()
            val status = result.mergeStatus
            if (status.isSuccessful) {
                emitEvent(GitEventType.MERGE_COMPLETED, "Merged $branchName successfully")
                GitSuccess(status.name)
            } else {
                val conflicts = result.conflicts
                if (conflicts != null) {
                    emitEvent(GitEventType.CONFLICT_DETECTED, "Merge conflicts with $branchName")
                    GitError("Merge conflicts in ${conflicts.size} files")
                } else {
                    GitError("Merge failed: ${status.name}")
                }
            }
        }

    suspend fun reset(mode: ResetType = ResetType.MIXED, ref: String = "HEAD"): GitResult<Unit> =
        withGit { git ->
            git.reset().setMode(mode).setRef(ref).call()
            GitSuccess(Unit)
        }

    suspend fun revert(commitHash: String): GitResult<CommitInfo> = withGit { git ->
        val revCommit = git.revert()
            .include(ObjectId.fromString(commitHash))
            .call()
        GitSuccess(revCommit.toCommitInfo())
    }

    suspend fun isRepository(): GitResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val git = repositoryProvider.getGit()
            GitSuccess(git != null && git.repository.objectDatabase.exists())
        } catch (_: Exception) {
            GitSuccess(false)
        }
    }

    suspend fun getCurrentBranch(): GitResult<String> = withContext(Dispatchers.IO) {
        try {
            val git = repositoryProvider.getGit()
                ?: return@withContext GitError("No repository open")
            GitSuccess(git.repository.branch ?: "HEAD")
        } catch (_: Exception) {
            GitError("Failed to get current branch")
        }
    }

    suspend fun getHeadCommit(): GitResult<CommitInfo> = withGit { git ->
        val head = git.repository.resolve("HEAD")
            ?: return@withGit GitError("Cannot resolve HEAD")
        val walk = org.eclipse.jgit.revwalk.RevWalk(git.repository)
        val commit = walk.parseCommit(head)
        val info = commit.toCommitInfo()
        walk.dispose()
        GitSuccess(info)
    }

    suspend fun openRepository(directory: String): GitResult<String> = withContext(Dispatchers.IO) {
        try {
            val repoDir = File(directory)
            if (!org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                    .setGitDir(File(repoDir, ".git"))
                    .readEnvironment()
                    .findGitDir(repoDir)
                    .build()
                    .objectDatabase.exists()
            ) {
                return@withContext GitError("Not a git repository: $directory")
            }
            val git = Git.open(repoDir)
            repositoryProvider.setGit(git, directory)
            GitSuccess(git.repository.branch)
        } catch (e: Exception) {
            GitError("Failed to open repository: ${e.message}", e)
        }
    }

    private suspend fun <T> withGit(block: suspend (Git) -> GitResult<T>): GitResult<T> {
        return withContext(Dispatchers.IO) {
            val git = repositoryProvider.getGit()
                ?: return@withContext GitError("No repository is open")
            try {
                block(git)
            } catch (e: GitAPIException) {
                GitError("Git operation failed: ${e.message}", e)
            } catch (e: Exception) {
                GitError("Operation failed: ${e.message}", e)
            }
        }
    }

    private suspend fun emitEvent(type: GitEventType, message: String) {
        _events.emit(GitEvent(type, message, System.currentTimeMillis()))
    }

    private fun RevCommit.toCommitInfo(): CommitInfo {
        return CommitInfo(
            hash = this.name,
            shortHash = this.name.substring(0, minOf(7, this.name.length)),
            authorName = this.authorIdent.name,
            authorEmail = this.authorIdent.emailAddress,
            message = this.shortMessage,
            fullMessage = this.fullMessage,
            commitTime = this.commitTime.toLong() * 1000L
        )
    }

    companion object {
        private const val TAG = "GitManager"
    }
}