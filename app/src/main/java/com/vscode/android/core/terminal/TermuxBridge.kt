package com.vscode.android.core.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TermuxBridge(private val context: Context) {

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_API_PACKAGE = "com.termux.api"
        const val TERMUX_BOOT_PACKAGE = "com.termux.boot"
        const val TERMUX_WIDGET_PACKAGE = "com.termux.widget"
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val PROOT_DISTRO_PATH = "/data/data/com.termux/files/usr/var/lib/proot-distro"

        private val KNOWN_COMPILERS = mapOf(
            "gcc" to "gcc",
            "g++" to "g++",
            "clang" to "clang",
            "clang++" to "clang++",
            "javac" to "javac",
            "kotlinc" to "kotlinc",
            "python3" to "python3",
            "python" to "python",
            "node" to "node",
            "make" to "make",
            "cmake" to "cmake",
            "gradle" to "gradle"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isConnected = AtomicBoolean(false)
    private val connectionListeners = mutableListOf<ConnectionListener>()
    private val availableCompilers = ConcurrentHashMap<String, Boolean>()
    private val termuxEnvCache = ConcurrentHashMap<String, String>()

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isTermuxRunning(): Boolean {
        return isConnected.get()
    }

    fun isTermuxApiInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isTermuxBootInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_BOOT_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isTermuxWidgetInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_WIDGET_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun connectToTermux(): ConnectionResult = withContext(Dispatchers.IO) {
        if (!isTermuxInstalled()) {
            return@withContext ConnectionResult(false, "Termux is not installed")
        }

        try {
            val result = runTermuxCommand("echo __TERMUX_READY__")
            if (result.contains("__TERMUX_READY__")) {
                isConnected.set(true)
                cacheTermuxEnvironment()
                notifyConnectionEstablished()
                return@withContext ConnectionResult(true, "Connected to Termux")
            } else {
                return@withContext ConnectionResult(false, "Failed to verify Termux connection")
            }
        } catch (e: Exception) {
            return@withContext ConnectionResult(false, "Connection failed: ${e.message}")
        }
    }

    fun disconnectFromTermux() {
        isConnected.set(false)
        termuxEnvCache.clear()
        availableCompilers.clear()
        notifyConnectionLost()
    }

    suspend fun executeInTermux(
        command: String,
        workingDir: String? = null,
        envVars: Map<String, String> = emptyMap(),
        callback: ((TermuxCommandResult) -> Unit)? = null
    ): TermuxCommandResult = withContext(Dispatchers.IO) {
        if (!isConnected.get()) {
            val result = TermuxCommandResult(
                success = false,
                output = "",
                error = "Termux is not connected",
                exitCode = -1
            )
            callback?.invoke(result)
            return@withContext result
        }

        try {
            val fullCommand = buildTermuxCommand(command, workingDir, envVars)
            val startTime = System.currentTimeMillis()
            val output = runTermuxCommand(fullCommand)
            val duration = System.currentTimeMillis() - startTime

            val result = TermuxCommandResult(
                success = true,
                output = output,
                error = "",
                exitCode = 0,
                duration = duration
            )
            callback?.invoke(result)
            result
        } catch (e: Exception) {
            val result = TermuxCommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
            callback?.invoke(result)
            result
        }
    }

    suspend fun installCompiler(language: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected.get()) return@withContext false

        val packageName = when (language.lowercase()) {
            "c", "cpp", "c++" -> "clang"
            "python", "python3" -> "python"
            "java" -> "openjdk-17"
            "kotlin" -> "kotlin"
            "javascript", "node", "nodejs" -> "nodejs"
            "go", "golang" -> "golang"
            "rust" -> "rust"
            "make" -> "make"
            "cmake" -> "cmake"
            "gradle" -> "gradle"
            else -> return@withContext false
        }

        try {
            val result = runTermuxCommand("pkg install -y $packageName")
            val success = result.contains("setting up") || result.contains("installed") ||
                result.contains("already") || result.contains("up to date")
            if (success) {
                availableCompilers[language] = true
            }
            return@withContext success
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun installPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected.get()) return@withContext false
        try {
            val result = runTermuxCommand("pkg install -y $packageName")
            return@withContext result.contains("setting up") || result.contains("installed") ||
                result.contains("already") || result.contains("up to date")
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun updatePackages(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected.get()) return@withContext false
        try {
            runTermuxCommand("pkg update -y")
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun checkCompilerAvailability(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (!isConnected.get()) {
            return@withContext KNOWN_COMPILERS.keys.associateWith { false }
        }

        for ((name, binary) in KNOWN_COMPILERS) {
            try {
                val result = runTermuxCommand("which $binary 2>/dev/null")
                availableCompilers[name] = result.isNotEmpty() && !result.contains("not found")
            } catch (_: Exception) {
                availableCompilers[name] = false
            }
        }
        return@withContext availableCompilers.toMap()
    }

    suspend fun installAllCompilers(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        val compilerPackages = mapOf(
            "gcc" to "clang",
            "g++" to "clang",
            "clang" to "clang",
            "clang++" to "clang",
            "python3" to "python",
            "python" to "python",
            "node" to "nodejs",
            "make" to "make",
            "cmake" to "cmake",
            "javac" to "openjdk-17",
            "kotlinc" to "kotlin",
            "gradle" to "gradle"
        )

        val uniquePackages = compilerPackages.values.toSet()
        for (pkg in uniquePackages) {
            try {
                val result = runTermuxCommand("pkg install -y $pkg")
                val success = result.contains("setting up") || result.contains("installed") ||
                    result.contains("already") || result.contains("up to date")
                compilerPackages.filter { it.value == pkg }.forEach { (name, _) ->
                    results[name] = success
                }
            } catch (_: Exception) {
                compilerPackages.filter { it.value == pkg }.forEach { (name, _) ->
                    results[name] = false
                }
            }
        }

        availableCompilers.putAll(results)
        return@withContext results
    }

    fun getTermuxPath(): String = TERMUX_PREFIX

    fun getTermuxHomePath(): String = TERMUX_HOME

    fun getAndroidPath(termuxPath: String): String {
        if (termuxPath.startsWith(TERMUX_PREFIX)) {
            return termuxPath.removePrefix(TERMUX_PREFIX).let {
                if (it.startsWith("/")) TERMUX_PREFIX + it else "$TERMUX_PREFIX/$it"
            }
        }
        if (termuxPath.startsWith("~")) {
            return termuxPath.replaceFirst("~", TERMUX_HOME)
        }
        return termuxPath
    }

    fun getTermuxAndroidPath(androidPath: String): String {
        return if (androidPath.startsWith(TERMUX_PREFIX)) {
            androidPath.removePrefix(TERMUX_PREFIX).let { if (it.isEmpty()) "/" else it }
        } else {
            androidPath
        }
    }

    suspend fun runPythonScript(scriptPath: String, args: List<String> = emptyList()): TermuxCommandResult {
        return executeInTermux("python3 \"$scriptPath\" ${args.joinToString(" ")}")
    }

    suspend fun runBashScript(scriptPath: String, args: List<String> = emptyList()): TermuxCommandResult {
        return executeInTermux("bash \"$scriptPath\" ${args.joinToString(" ")}")
    }

    suspend fun runNodeScript(scriptPath: String, args: List<String> = emptyList()): TermuxCommandResult {
        return executeInTermux("node \"$scriptPath\" ${args.joinToString(" ")}")
    }

    suspend fun setupProotDebian(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected.get()) return@withContext false
        try {
            runTermuxCommand("pkg install -y proot-distro")
            val result = runTermuxCommand("proot-distro install debian")
            return@withContext result.contains("success") || result.contains("already") ||
                result.contains("installed")
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun executeInProotDebian(command: String): TermuxCommandResult {
        return executeInTermux("proot-distro login debian -- $command")
    }

    suspend fun isProotDebianInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected.get()) return@withContext false
        try {
            val result = runTermuxCommand("proot-distro list 2>/dev/null")
            return@withContext result.contains("debian")
        } catch (_: Exception) {
            return@withContext false
        }
    }

    fun openTermuxApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("TermuxBridge", "Failed to open Termux", e)
        }
    }

    fun openTermuxInstallPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("TermuxBridge", "Failed to open Termux install page", e)
        }
    }

    fun sendTermuxIntent(action: String, extras: Map<String, String> = emptyMap()) {
        try {
            val intent = Intent(action).apply {
                setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.RunCommandService")
                extras.forEach { (key, value) -> putExtra(key, value) }
            }
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("TermuxBridge", "Failed to send intent to Termux", e)
        }
    }

    fun addConnectionListener(listener: ConnectionListener) {
        if (!connectionListeners.contains(listener)) {
            connectionListeners.add(listener)
        }
    }

    fun removeConnectionListener(listener: ConnectionListener) {
        connectionListeners.remove(listener)
    }

    private fun buildTermuxCommand(command: String, workingDir: String?, envVars: Map<String, String>): String {
        val sb = StringBuilder()
        if (envVars.isNotEmpty()) {
            envVars.forEach { (key, value) ->
                sb.append("export $key=\"$value\"; ")
            }
        }
        if (workingDir != null) {
            sb.append("cd \"$workingDir\" && ")
        }
        sb.append(command)
        return sb.toString()
    }

    private fun runTermuxCommand(command: String): String {
        val fullCommand = "su -c '$command'"
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", fullCommand))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (stderr.isNotEmpty()) {
                android.util.Log.w("TermuxBridge", "stderr: $stderr")
            }
            stdout
        } catch (e: Exception) {
            // Fallback: try without su
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val stdout = process.inputStream.bufferedReader().readText()
                process.waitFor()
                stdout
            } catch (e2: Exception) {
                throw e2
            }
        }
    }

    private fun cacheTermuxEnvironment() {
        scope.launch(Dispatchers.IO) {
            try {
                val envOutput = runTermuxCommand("env")
                envOutput.lines().forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        termuxEnvCache[parts[0]] = parts[1]
                    }
                }
            } catch (_: Exception) {
                // Ignore cache failures
            }
        }
    }

    fun getTermuxEnvironmentVariable(key: String): String? = termuxEnvCache[key]

    fun getAllTermuxEnvironmentVariables(): Map<String, String> = termuxEnvCache.toMap()

    private fun notifyConnectionEstablished() {
        connectionListeners.forEach { it.onConnected() }
    }

    private fun notifyConnectionLost() {
        connectionListeners.forEach { it.onDisconnected() }
    }

    data class ConnectionResult(
        val success: Boolean,
        val message: String
    )

    data class TermuxCommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int,
        val duration: Long = 0
    )

    interface ConnectionListener {
        fun onConnected() {}
        fun onDisconnected() {}
    }
}