package com.vscode.android.core.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class TerminalManager(private val context: Context) {

    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val sessionOrder = CopyOnWriteArrayList<String>()
    private var activeSessionId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = CopyOnWriteArrayList<TerminalSessionListener>()

    private var termuxBridgeReady = false
    private var globalEnvVars = mutableMapOf<String, String>()

    fun initialize() {
        sessions.clear()
        sessionOrder.clear()
        activeSessionId = null
        globalEnvVars.putAll(System.getenv())
        globalEnvVars["TERM"] = "xterm-256color"
        globalEnvVars["LANG"] = "en_US.UTF-8"
    }

    fun createSession(name: String = "Terminal", workingDir: String? = null, shellType: String? = null): TerminalSession {
        val id = UUID.randomUUID().toString()
        val shell = shellType ?: detectShell()
        val cwd = workingDir ?: context.filesDir.absolutePath
        val session = TerminalSession(
            id = id,
            name = name,
            workingDir = cwd,
            shell = shell
        )

        sessions[id] = session
        sessionOrder.add(id)
        activeSessionId = id

        scope.launch(Dispatchers.IO) {
            session.startShell()
        }

        return session
    }

    fun killSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        sessionOrder.remove(sessionId)
        session.kill()

        if (activeSessionId == sessionId) {
            activeSessionId = sessionOrder.lastOrNull()
        }
    }

    fun killAllSessions() {
        sessionOrder.forEach { id ->
            sessions[id]?.kill()
        }
        sessions.clear()
        sessionOrder.clear()
        activeSessionId = null
    }

    fun getSession(sessionId: String): TerminalSession? = sessions[sessionId]

    fun getActiveSession(): TerminalSession? = activeSessionId?.let { sessions[it] }

    fun setActiveSession(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            activeSessionId = sessionId
        }
    }

    fun listSessions(): List<TerminalSession> = sessionOrder.mapNotNull { sessions[it] }

    fun sendCommand(sessionId: String, command: String) {
        val session = sessions[sessionId] ?: return
        session.sendCommand(command)
    }

    fun readOutput(sessionId: String): String {
        return sessions[sessionId]?.getOutput() ?: ""
    }

    fun getOutputLines(sessionId: String): List<String> {
        return sessions[sessionId]?.getOutputLines() ?: emptyList()
    }

    fun setWorkingDirectory(sessionId: String, dir: String) {
        sessions[sessionId]?.workingDir = dir
    }

    fun getWorkingDirectory(sessionId: String): String {
        return sessions[sessionId]?.workingDir ?: context.filesDir.absolutePath
    }

    fun setTerminalSize(sessionId: String, rows: Int, columns: Int) {
        sessions[sessionId]?.let { session ->
            session.rows = rows
            session.columns = columns
            session.sendCommand("stty rows $rows columns $columns")
        }
    }

    fun setEnvironmentVariable(sessionId: String, key: String, value: String) {
        sessions[sessionId]?.let { session ->
            session.envVars[key] = value
            session.sendCommand("export $key=\"$value\"")
        }
    }

    fun getEnvironmentVariable(sessionId: String, key: String): String? {
        return sessions[sessionId]?.envVars?.get(key)
    }

    fun setGlobalEnvironmentVariable(key: String, value: String) {
        globalEnvVars[key] = value
    }

    fun getGlobalEnvironmentVariable(key: String): String? {
        return globalEnvVars[key]
    }

    fun getAllGlobalEnvironmentVariables(): Map<String, String> {
        return globalEnvVars.toMap()
    }

    fun getCommandHistory(sessionId: String): List<String> {
        return sessions[sessionId]?.getCommandHistory() ?: emptyList()
    }

    fun addListener(listener: TerminalSessionListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: TerminalSessionListener) {
        listeners.remove(listener)
    }

    fun onTermuxBridgeReady() {
        termuxBridgeReady = true
        sessionOrder.forEach { id ->
            sessions[id]?.onBridgeReady()
        }
    }

    fun isTermuxBridgeReady(): Boolean = termuxBridgeReady

    fun shutdown() {
        scope.launch {
            killAllSessions()
            listeners.clear()
        }
    }

    private fun detectShell(): String {
        val shells = listOf("/data/data/com.termux/files/usr/bin/bash", "/system/bin/bash", "/data/data/com.termux/files/usr/bin/zsh", "/system/bin/zsh", "/system/bin/sh")
        for (shell in shells) {
            if (File(shell).exists() || File(shell).canExecute()) {
                return shell
            }
        }
        return "/system/bin/sh"
    }

    private fun notifyOutput(session: TerminalSession, text: String) {
        listeners.forEach { it.onOutput(session, text) }
    }

    private fun notifyExit(session: TerminalSession, exitCode: Int) {
        listeners.forEach { it.onExit(session, exitCode) }
    }

    private fun notifyError(session: TerminalSession, error: String) {
        listeners.forEach { it.onError(session, error) }
    }

    data class TerminalSession(
        val id: String,
        val name: String,
        var workingDir: String = "/",
        val shell: String = "/system/bin/sh",
        var rows: Int = 24,
        var columns: Int = 80
    ) {
        private var process: Process? = null
        private var outputStream: OutputStream? = null
        private var inputStream: InputStream? = null
        private var errorStream: InputStream? = null
        @Volatile var isRunning: Boolean = false
            private set

        private val outputBuffer = StringBuilder()
        private val outputLines = mutableListOf<String>()
        private val commandHistory = mutableListOf<String>()
        private var historyIndex = -1
        val envVars = mutableMapOf<String, String>()
        private val ansiParser = AnsiParser()
        private var outputJob: Job? = null
        private var errorJob: Job? = null
        private var bridgeReady = false

        companion object {
            const val MAX_OUTPUT_LINES = 10000
            const val MAX_HISTORY_ENTRIES = 1000
        }

        fun startShell() {
            if (isRunning) return
            try {
                val env = mutableListOf<String>()
                envVars.forEach { (k, v) -> env.add("$k=$v") }
                val envArray = if (env.isEmpty()) null else env.toTypedArray()

                val processBuilder = ProcessBuilder(shell, "-i")
                    .directory(File(workingDir))
                    .redirectErrorStream(false)

                if (envArray != null) {
                    processBuilder.environment().putAll(envVars)
                }

                process = processBuilder.start()
                outputStream = process?.outputStream
                inputStream = process?.inputStream
                errorStream = process?.errorStream
                isRunning = true

                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                outputJob = scope.launch {
                    readStream(inputStream, isError = false)
                }
                errorJob = scope.launch {
                    readStream(errorStream, isError = true)
                }
            } catch (e: Exception) {
                isRunning = false
                appendOutput("\u001B[31mFailed to start shell: ${e.message}\u001B[0m\n")
            }
        }

        fun sendCommand(command: String) {
            if (!isRunning && !bridgeReady) {
                appendOutput("$ $command\n")
                appendOutput("\u001B[31mTerminal session is not running\u001B[0m\n")
                return
            }

            addToHistory(command)
            appendOutput("$ $command\n")

            try {
                outputStream?.let { os ->
                    os.write((command + "\n").toByteArray())
                    os.flush()
                }
            } catch (e: Exception) {
                appendOutput("\u001B[31mError sending command: ${e.message}\u001B[0m\n")
            }
        }

        fun getOutput(): String = outputBuffer.toString()

        fun getOutputLines(): List<String> = outputLines.toList()

        fun clearOutput() {
            outputBuffer.clear()
            outputLines.clear()
        }

        fun getCommandHistory(): List<String> = commandHistory.toList()

        fun getPreviousCommand(currentInput: String): String? {
            if (commandHistory.isEmpty()) return null
            if (historyIndex == -1) {
                historyIndex = commandHistory.size - 1
            } else if (historyIndex > 0) {
                historyIndex--
            }
            return commandHistory.getOrNull(historyIndex)
        }

        fun getNextCommand(): String? {
            if (historyIndex >= commandHistory.size - 1) {
                historyIndex = -1
                return null
            }
            historyIndex++
            return commandHistory.getOrNull(historyIndex)
        }

        fun resetHistoryNavigation() {
            historyIndex = -1
        }

        fun kill() {
            isRunning = false
            outputJob?.cancel()
            errorJob?.cancel()
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                inputStream?.close()
            } catch (_: Exception) {}
            try {
                errorStream?.close()
            } catch (_: Exception) {}
            try {
                process?.destroy()
            } catch (_: Exception) {}
            try {
                process?.destroyForcibly()
            } catch (_: Exception) {}
            process = null
            outputStream = null
            inputStream = null
            errorStream = null
        }

        fun onBridgeReady() {
            bridgeReady = true
        }

        private fun readStream(stream: InputStream?, isError: Boolean) {
            if (stream == null) return
            try {
                val reader = stream.bufferedReader()
                val buffer = CharArray(4096)
                var charsRead: Int
                while (isRunning && CoroutineScope(Dispatchers.IO).isActive) {
                    charsRead = reader.read(buffer)
                    if (charsRead == -1) break
                    val text = String(buffer, 0, charsRead)
                    appendOutput(text)
                }
            } catch (_: Exception) {
                // Stream closed or interrupted
            }
        }

        private fun appendOutput(text: String) {
            val parsed = ansiParser.parse(text)
            outputBuffer.append(parsed)

            val lines = parsed.toString().lines()
            for (line in lines) {
                if (line.isNotEmpty()) {
                    outputLines.add(line)
                }
            }

            while (outputLines.size > MAX_OUTPUT_LINES) {
                outputLines.removeAt(0)
            }

            if (outputBuffer.length > MAX_OUTPUT_LINES * 200) {
                val excess = outputBuffer.length - (MAX_OUTPUT_LINES * 200)
                if (excess > 0) {
                    outputBuffer.delete(0, excess)
                }
            }
        }

        private fun addToHistory(command: String) {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) return
            if (commandHistory.isNotEmpty() && commandHistory.last() == trimmed) return
            commandHistory.add(trimmed)
            while (commandHistory.size > MAX_HISTORY_ENTRIES) {
                commandHistory.removeAt(0)
            }
            resetHistoryNavigation()
        }

        private class AnsiParser {
            private val ansiPattern = Regex("\u001B\\[[0-9;]*[a-zA-Z]")
            private var currentForeground = 37
            private var currentBackground = 40
            private var bold = false
            private var dim = false
            private var italic = false
            private var underline = false
            private var blink = false
            private var reverse = false
            private var hidden = false
            private var strikethrough = false

            fun parse(text: String): String {
                if (!text.contains("\u001B")) return text

                val result = StringBuilder()
                var i = 0
                while (i < text.length) {
                    if (text[i] == '\u001B' && i + 1 < text.length && text[i + 1] == '[') {
                        val match = ansiPattern.find(text, i)
                        if (match != null) {
                            val code = match.value
                            processCode(code.substring(2, code.length - 1))
                            i = match.range.last + 1
                            continue
                        }
                    }
                    result.append(text[i])
                    i++
                }
                return result.toString()
            }

            private fun processCode(code: String) {
                if (code.isEmpty()) {
                    resetAll()
                    return
                }
                val parts = code.split(";")
                for (part in parts) {
                    val n = part.toIntOrNull() ?: continue
                    when (n) {
                        0 -> resetAll()
                        1 -> bold = true
                        2 -> dim = true
                        3 -> italic = true
                        4 -> underline = true
                        5 -> blink = true
                        7 -> reverse = true
                        8 -> hidden = true
                        9 -> strikethrough = true
                        21 -> bold = false
                        22 -> { bold = false; dim = false }
                        23 -> italic = false
                        24 -> underline = false
                        25 -> blink = false
                        27 -> reverse = false
                        28 -> hidden = false
                        29 -> strikethrough = false
                        30 -> currentForeground = 30
                        31 -> currentForeground = 31
                        32 -> currentForeground = 32
                        33 -> currentForeground = 33
                        34 -> currentForeground = 34
                        35 -> currentForeground = 35
                        36 -> currentForeground = 36
                        37 -> currentForeground = 37
                        38 -> { /* Extended foreground, skip for basic parser */ }
                        39 -> currentForeground = 37
                        40 -> currentBackground = 40
                        41 -> currentBackground = 41
                        42 -> currentBackground = 42
                        43 -> currentBackground = 43
                        44 -> currentBackground = 44
                        45 -> currentBackground = 45
                        46 -> currentBackground = 46
                        47 -> currentBackground = 47
                        48 -> { /* Extended background, skip for basic parser */ }
                        49 -> currentBackground = 40
                        90 -> currentForeground = 90
                        91 -> currentForeground = 91
                        92 -> currentForeground = 92
                        93 -> currentForeground = 93
                        94 -> currentForeground = 94
                        95 -> currentForeground = 95
                        96 -> currentForeground = 96
                        97 -> currentForeground = 97
                        100 -> currentBackground = 100
                        101 -> currentBackground = 101
                        102 -> currentBackground = 102
                        103 -> currentBackground = 103
                        104 -> currentBackground = 104
                        105 -> currentBackground = 105
                        106 -> currentBackground = 106
                        107 -> currentBackground = 107
                    }
                }
            }

            private fun resetAll() {
                currentForeground = 37
                currentBackground = 40
                bold = false
                dim = false
                italic = false
                underline = false
                blink = false
                reverse = false
                hidden = false
                strikethrough = false
            }
        }
    }

    interface TerminalSessionListener {
        fun onOutput(session: TerminalSession, output: String) {}
        fun onExit(session: TerminalSession, exitCode: Int) {}
        fun onError(session: TerminalSession, error: String) {}
    }
}