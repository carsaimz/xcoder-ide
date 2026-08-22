package com.xcoder.core.terminal

import java.io.File
import javax.inject.Inject

/**
 * Result of a shell command execution.
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isFailure: Boolean = exitCode != 0
)

/**
 * Executes shell commands on the local device using [ProcessBuilder].
 *
 * This is the Android-side implementation used by AI copilot tools and
 * the build engine. It does NOT use Termux sessions — it spawns a
 * short-lived JVM process for each invocation.
 */
class ShellExecutor @Inject constructor() {

    /**
     * Execute a command.
     *
     * @param workDir  Working directory for the process.
     * @param command  Command parts (e.g. `listOf("git", "status")`).
     * @param timeoutMs Maximum wall-clock time in milliseconds.
     */
    fun execute(workDir: File, command: List<String>, timeoutMs: Long = 30_000L): CommandResult {
        val process = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(false)
            .start()

        var stdout: String
        var stderr: String
        val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

        if (!finished) {
            process.destroyForcibly()
            return CommandResult(-1, "", "Command timed out after ${timeoutMs}ms", isFailure = true)
        }

        stdout = process.inputStream.bufferedReader().readText()
        stderr = process.errorStream.bufferedReader().readText()

        return CommandResult(process.exitValue(), stdout, stderr)
    }
}
