package com.xcoder.core.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellExecutor @Inject constructor() {

    private val defaultEnvironment: Map<String, String>
        get() = System.getenv().toMap() + mapOf(
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "XCODER" to "1"
        )

    suspend fun execute(
        command: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000
    ): CommandResult = withContext(Dispatchers.IO) {
        val process = createProcess(command, workingDirectory, environment)
        try {
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Command timed out after ${timeoutMs}ms",
                    command = command,
                    timedOut = true
                )
            } else {
                val stdout = process.inputStream.bufferedReader().readText().trim()
                val stderr = process.errorStream.bufferedReader().readText().trim()
                CommandResult(
                    exitCode = process.exitValue(),
                    stdout = stdout,
                    stderr = stderr,
                    command = command,
                    timedOut = false
                )
            }
        } catch (e: TimeoutCancellationException) {
            process.destroyForcibly()
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Command timed out: ${e.message}",
                command = command,
                timedOut = true
            )
        } catch (e: Exception) {
            process.destroyForcibly()
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Execution failed: ${e.message}",
                command = command,
                timedOut = false
            )
        }
    }

    fun executeStreaming(
        command: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap()
    ): Flow<ShellOutputLine> = flow {
        val process = createProcess(command, workingDirectory, environment)
        try {
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
            val stdoutJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                stdoutReader.forEachLine { line ->
                    emit(ShellOutputLine(line, OutputType.STDOUT))
                }
            }
            val stderrJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                stderrReader.forEachLine { line ->
                    emit(ShellOutputLine(line, OutputType.STDERR))
                }
            }
            stdoutJob.join()
            stderrJob.join()
            val exitCode = process.waitFor()
            emit(ShellOutputLine("", OutputType.EXIT, exitCode))
        } catch (e: Exception) {
            emit(ShellOutputLine("Execution error: ${e.message}", OutputType.STDERR))
            emit(ShellOutputLine("", OutputType.EXIT, -1))
        } finally {
            process.destroyForcibly()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun executeWithOutputFlow(
        command: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000,
        onOutput: (String, Boolean) -> Unit
    ): CommandResult = withContext(Dispatchers.IO) {
        val process = createProcess(command, workingDirectory, environment)
        try {
            val stdoutThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onOutput(line!! + "\n", false)
                    }
                }
            }
            val stderrThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onOutput(line!! + "\n", true)
                    }
                }
            }
            stdoutThread.start()
            stderrThread.start()
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                stdoutThread.interrupt()
                stderrThread.interrupt()
                CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Command timed out after ${timeoutMs}ms",
                    command = command,
                    timedOut = true
                )
            } else {
                stdoutThread.join(5000)
                stderrThread.join(5000)
                CommandResult(
                    exitCode = process.exitValue(),
                    stdout = process.inputStream.bufferedReader().readText().trim(),
                    stderr = process.errorStream.bufferedReader().readText().trim(),
                    command = command,
                    timedOut = false
                )
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Execution failed: ${e.message}",
                command = command,
                timedOut = false
            )
        }
    }

    suspend fun executeShellScript(
        scriptContent: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000
    ): CommandResult = withContext(Dispatchers.IO) {
        val tmpScript = File.createTempFile("xcoder_script_", ".sh")
        try {
            tmpScript.writeText(scriptContent)
            tmpScript.setExecutable(true)
            val command = "sh ${tmpScript.absolutePath}"
            val mergedEnv = defaultEnvironment + environment
            val process = ProcessBuilder("sh", tmpScript.absolutePath())
                .directory(workingDirectory?.let { File(it) })
                .apply {
                    environment.clear()
                    mergedEnv.forEach { (k, v) -> this.environment[k] = v }
                }
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Script timed out after ${timeoutMs}ms",
                    command = command,
                    timedOut = true
                )
            } else {
                val output = process.inputStream.bufferedReader().readText().trim()
                CommandResult(
                    exitCode = process.exitValue(),
                    stdout = output,
                    stderr = "",
                    command = command,
                    timedOut = false
                )
            }
        } finally {
            tmpScript.delete()
        }
    }

    suspend fun checkCommandExists(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = execute("which $command", timeoutMs = 5000)
            result.exitCode == 0 && result.stdout.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getWorkingDirectory(): String = withContext(Dispatchers.IO) {
        try {
            val result = execute("pwd", timeoutMs = 3000)
            if (result.exitCode == 0) result.stdout.trim() else "/"
        } catch (_: Exception) {
            "/"
        }
    }

    suspend fun listDirectory(path: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = execute("ls -1a \"$path\"", timeoutMs = 5000)
            if (result.exitCode == 0) result.stdout.lines().filter { it.isNotBlank() } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun createProcess(
        command: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap()
    ): Process {
        val shell = "/system/bin/sh"
        val mergedEnv = defaultEnvironment + environment
        return ProcessBuilder(shell, "-c", command)
            .directory(workingDirectory?.let { File(it) })
            .apply {
                this.environment.clear()
                mergedEnv.forEach { (k, v) -> this.environment[k] = v }
            }
            .start()
    }
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val command: String,
    val timedOut: Boolean = false,
    val durationMs: Long = 0
) {
    val isSuccess: Boolean get() = exitCode == 0
    val isFailure: Boolean get() = exitCode != 0
    val combinedOutput: String get() = buildString {
        if (stdout.isNotBlank()) append(stdout)
        if (stderr.isNotBlank()) {
            if (isNotBlank()) append("\n")
            append(stderr)
        }
    }
}

data class ShellOutputLine(
    val text: String,
    val type: OutputType,
    val exitCode: Int? = null
)

enum class OutputType {
    STDOUT, STDERR, EXIT
}