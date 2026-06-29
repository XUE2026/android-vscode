package com.vscode.android.core.plugin

import android.content.Context
import com.vscode.android.VSCodeApp
import com.vscode.android.core.project.FileInfo
import com.vscode.android.core.project.FileTreeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class EditorState(
    val filePath: String?,
    val content: String,
    val language: String,
    val cursorPosition: Int,
    val selectionStart: Int?,
    val selectionEnd: Int?,
    val isModified: Boolean,
    val isReadOnly: Boolean
)

data class StatusBarItem(
    val id: String,
    var text: String,
    var tooltip: String = "",
    var command: String = "",
    var alignment: StatusBarAlignment = StatusBarAlignment.LEFT,
    var priority: Int = 0,
    var visible: Boolean = true
)

enum class StatusBarAlignment { LEFT, RIGHT }

data class NotificationMessage(
    val id: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val actions: List<NotificationAction> = emptyList(),
    val duration: Long = 5000L
)

enum class NotificationType { INFO, WARNING, ERROR, SUCCESS }

data class NotificationAction(
    val title: String,
    val action: suspend () -> Unit
)

data class QuickPickItem(
    val label: String,
    val description: String = "",
    val detail: String = "",
    val picked: Boolean = false
)

data class InputBoxOptions(
    val prompt: String = "",
    val value: String = "",
    val placeHolder: String = "",
    val password: Boolean = false,
    val validateInput: ((String) -> String?)? = null
)

data class WorkspaceConfiguration(
    val settings: Map<String, Any> = emptyMap()
)

interface PluginAPI {
    val editor: EditorAPI
    val fileSystem: FileSystemAPI
    val terminal: TerminalAPI
    val build: BuildAPI
    val commands: CommandAPI
    val window: WindowAPI
    val workspace: WorkspaceAPI
}

class PluginAPIImpl(private val context: Context) : PluginAPI {

    override val editor: EditorAPI = EditorAPIImpl(context)
    override val fileSystem: FileSystemAPI = FileSystemAPIImpl(context)
    override val terminal: TerminalAPI = TerminalAPIImpl(context)
    override val build: BuildAPI = BuildAPIImpl(context)
    override val commands: CommandAPI = CommandAPIImpl()
    override val window: WindowAPI = WindowAPIImpl()
    override val workspace: WorkspaceAPI = WorkspaceAPIImpl(context)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun shutdown() {
        // Cleanup resources
    }
}

class EditorAPIImpl(private val context: Context) : EditorAPI {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val openEditors = ConcurrentHashMap<String, EditorState>()

    private var activeEditorId: String? = null

    override suspend fun openFile(filePath: String): EditorState = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val content = if (file.exists()) file.readText() else ""
        val language = detectLanguage(filePath)
        val state = EditorState(
            filePath = filePath,
            content = content,
            language = language,
            cursorPosition = 0,
            selectionStart = null,
            selectionEnd = null,
            isModified = false,
            isReadOnly = !file.canWrite() && file.exists()
        )
        openEditors[filePath] = state
        activeEditorId = filePath
        state
    }

    override suspend fun closeFile(filePath: String): Boolean {
        openEditors.remove(filePath)
        if (activeEditorId == filePath) {
            activeEditorId = openEditors.keys.firstOrNull()
        }
        return true
    }

    override suspend fun saveFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val state = openEditors[filePath] ?: return@withContext false
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(state.content)
            openEditors[filePath] = state.copy(isModified = false)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getActiveEditor(): EditorState? {
        return activeEditorId?.let { openEditors[it] }
    }

    override suspend fun getOpenEditors(): List<EditorState> {
        return openEditors.values.toList()
    }

    override suspend fun insertText(filePath: String, text: String, position: Int?): Boolean {
        val state = openEditors[filePath] ?: return false
        val pos = position ?: state.cursorPosition
        val newContent = state.content.substring(0, pos) + text + state.content.substring(pos)
        openEditors[filePath] = state.copy(
            content = newContent,
            cursorPosition = pos + text.length,
            isModified = true
        )
        return true
    }

    override suspend fun getSelection(filePath: String): Pair<Int, Int>? {
        val state = openEditors[filePath] ?: return null
        val start = state.selectionStart ?: return null
        val end = state.selectionEnd ?: return null
        return Pair(start, end)
    }

    override suspend fun setSelection(filePath: String, start: Int, end: Int): Boolean {
        val state = openEditors[filePath] ?: return false
        val clampedStart = start.coerceIn(0, state.content.length)
        val clampedEnd = end.coerceIn(0, state.content.length)
        openEditors[filePath] = state.copy(
            selectionStart = clampedStart,
            selectionEnd = clampedEnd,
            cursorPosition = clampedEnd
        )
        return true
    }

    override suspend fun getCursorPosition(filePath: String): Int? {
        return openEditors[filePath]?.cursorPosition
    }

    override suspend fun setCursorPosition(filePath: String, position: Int): Boolean {
        val state = openEditors[filePath] ?: return false
        val clampedPos = position.coerceIn(0, state.content.length)
        openEditors[filePath] = state.copy(cursorPosition = clampedPos)
        return true
    }

    override suspend fun undo(filePath: String): Boolean {
        val state = openEditors[filePath] ?: return false
        // In a real implementation, this would interact with the undo stack
        return state.isModified
    }

    override suspend fun redo(filePath: String): Boolean {
        val state = openEditors[filePath] ?: return false
        return state.isModified
    }

    override suspend fun getLineCount(filePath: String): Int {
        val state = openEditors[filePath] ?: return 0
        return state.content.lines().size
    }

    override suspend fun getText(filePath: String): String? {
        return openEditors[filePath]?.content
    }

    override suspend fun getLanguage(filePath: String): String? {
        return openEditors[filePath]?.language
    }

    private fun detectLanguage(filePath: String): String {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py", "pyw", "pyi" -> "python"
            "js", "jsx", "mjs", "cjs" -> "javascript"
            "ts", "tsx", "mts", "cts" -> "typescript"
            "cpp", "cc", "cxx", "c++", "hpp", "hxx" -> "cpp"
            "c", "h" -> "c"
            "html", "htm", "shtml" -> "html"
            "css", "scss", "less" -> "css"
            "json", "jsonc", "json5" -> "json"
            "xml", "xsl", "xsd", "plist", "svg" -> "xml"
            "md", "markdown", "mdown", "mkd" -> "markdown"
            "yml", "yaml" -> "yaml"
            "gradle" -> "groovy"
            "sh", "bash", "zsh" -> "shell"
            "sql" -> "sql"
            "go" -> "go"
            "rs" -> "rust"
            "swift" -> "swift"
            "dart" -> "dart"
            "rb" -> "ruby"
            "php" -> "php"
            else -> "plaintext"
        }
    }
}

class FileSystemAPIImpl(private val context: Context) : FileSystemAPI {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileWatchers = ConcurrentHashMap<String, (String) -> Unit>()

    override suspend fun readFile(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            File(filePath).readText()
        } catch (e: Exception) {
            throw RuntimeException("Failed to read file: $filePath", e)
        }
    }

    override suspend fun writeFile(filePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun deleteFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false
            file.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun createDirectory(dirPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(dirPath).mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun listDirectory(dirPath: String): List<FileInfo> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles()?.map { file ->
            FileInfo(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
                isHidden = file.isHidden,
                isReadable = file.canRead(),
                isWritable = file.canWrite(),
                isExecutable = file.canExecute(),
                extension = if (file.isFile) file.extension else ""
            )
        }?.sortedWith(compareBy<FileInfo> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    override suspend fun exists(filePath: String): Boolean = withContext(Dispatchers.IO) {
        File(filePath).exists()
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(oldPath)
            val newFile = File(newPath)
            if (!oldFile.exists()) return@withContext false
            newFile.parentFile?.mkdirs()
            oldFile.renameTo(newFile)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun copy(sourcePath: String, destinationPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            if (!source.exists()) return@withContext false
            val dest = File(destinationPath)
            dest.parentFile?.mkdirs()
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getFileTree(dirPath: String): FileTreeNode = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        buildFileTree(dir)
    }

    private fun buildFileTree(dir: File): FileTreeNode {
        val node = FileTreeNode(
            name = dir.name,
            path = dir.absolutePath,
            isDirectory = dir.isDirectory,
            parentPath = dir.parent
        )
        if (dir.isDirectory) {
            val children = dir.listFiles()?.filter { !it.isHidden } ?: emptyList()
            node.children = children
                .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .map { buildFileTree(it) }
                .toMutableList()
            node.childCount = children.size
            node.isExpandable = children.any { it.isDirectory }
        }
        return node
    }

    override suspend fun watchFile(filePath: String, callback: (String) -> Unit) {
        fileWatchers[filePath] = callback
    }

    override suspend fun unwatchFile(filePath: String) {
        fileWatchers.remove(filePath)
    }
}

class TerminalAPIImpl(private val context: Context) : TerminalAPI {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val terminals = ConcurrentHashMap<String, TerminalAPIState>()

    data class TerminalAPIState(
        val id: String,
        val name: String,
        val output: StringBuilder = StringBuilder(),
        var process: Process? = null
    )

    override suspend fun createTerminal(name: String): String = withContext(Dispatchers.Main) {
        val id = "terminal_${System.currentTimeMillis()}"
        val state = TerminalAPIState(id, name)
        terminals[id] = state
        id
    }

    override suspend fun sendCommand(terminalId: String, command: String): String = withContext(Dispatchers.IO) {
        val state = terminals[terminalId] ?: return@withContext ""
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            state.process = process
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            val result = if (output.isNotEmpty()) output else error
            state.output.append("$ $command\n").append(result)
            if (!result.endsWith("\n")) state.output.append("\n")
            result
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            state.output.append("$ $command\n").append(errorMsg).append("\n")
            errorMsg
        }
    }

    override suspend fun getOutput(terminalId: String): String {
        return terminals[terminalId]?.output?.toString() ?: ""
    }

    override suspend fun killTerminal(terminalId: String): Boolean {
        val state = terminals.remove(terminalId) ?: return false
        try {
            state.process?.destroy()
        } catch (_: Exception) {}
        return true
    }

    override suspend fun getTerminals(): List<String> {
        return terminals.keys.toList()
    }
}

class BuildAPIImpl(private val context: Context) : BuildAPI {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buildTasks = mutableListOf<BuildTask>()
    private val buildConfigs = mutableListOf<BuildConfig>()

    data class BuildTask(
        val id: String,
        val name: String,
        val command: String,
        val workingDir: String = "",
        val env: Map<String, String> = emptyMap()
    )

    data class BuildConfig(
        val id: String,
        val name: String,
        val type: String,
        val command: String,
        val args: List<String> = emptyList()
    )

    override suspend fun build(config: Map<String, Any>?): String = withContext(Dispatchers.IO) {
        val command = config?.get("command") as? String ?: "./gradlew assembleDebug"
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Build failed: ${e.message}"
        }
    }

    override suspend fun run(config: Map<String, Any>?): String = withContext(Dispatchers.IO) {
        val command = config?.get("command") as? String ?: "echo 'No run command specified'"
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.inputStream.bufferedReader().readText()
            process.waitFor()
            "Run completed"
        } catch (e: Exception) {
            "Run failed: ${e.message}"
        }
    }

    override suspend fun debug(config: Map<String, Any>?): String = withContext(Dispatchers.IO) {
        "Debug mode not fully supported in this version"
    }

    override suspend fun getBuildTasks(): List<Map<String, Any>> {
        return buildTasks.map { task ->
            mapOf(
                "id" to task.id,
                "name" to task.name,
                "command" to task.command,
                "workingDir" to task.workingDir,
                "env" to task.env
            )
        }
    }

    override suspend fun getBuildConfig(): List<Map<String, Any>> {
        return buildConfigs.map { config ->
            mapOf(
                "id" to config.id,
                "name" to config.name,
                "type" to config.type,
                "command" to config.command,
                "args" to config.args
            )
        }
    }

    fun registerBuildTask(task: BuildTask) {
        buildTasks.add(task)
    }

    fun registerBuildConfig(config: BuildConfig) {
        buildConfigs.add(config)
    }
}

class CommandAPIImpl : CommandAPI {

    private val registeredCommands = ConcurrentHashMap<String, suspend (Map<String, Any>?) -> Unit>()

    override suspend fun registerCommand(commandId: String, handler: suspend (Map<String, Any>?) -> Unit) {
        registeredCommands[commandId] = handler
    }

    override suspend fun executeCommand(commandId: String, args: Map<String, Any>?): Boolean {
        val handler = registeredCommands[commandId] ?: return false
        try {
            handler(args)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override suspend fun getCommands(): List<String> {
        return registeredCommands.keys.toList()
    }
}

class WindowAPIImpl : WindowAPI {

    private val statusBarItems = mutableListOf<StatusBarItem>()
    private val notifications = mutableListOf<NotificationMessage>()

    private var statusBarMessage: String = ""
    private val _activeNotifications = MutableStateFlow<List<NotificationMessage>>(emptyList())
    val activeNotifications: StateFlow<List<NotificationMessage>> = _activeNotifications

    override suspend fun showMessage(message: String, type: NotificationType) {
        val notification = NotificationMessage(
            id = "msg_${System.currentTimeMillis()}",
            message = message,
            type = type
        )
        notifications.add(notification)
        android.util.Log.d("WindowAPI", "[${type.name}] $message")
    }

    override suspend fun showInputBox(options: InputBoxOptions): String? {
        // In a real implementation, this would show a UI dialog
        return options.value
    }

    override suspend fun showQuickPick(items: List<QuickPickItem>): QuickPickItem? {
        // In a real implementation, this would show a quick pick UI
        return items.firstOrNull()
    }

    override suspend fun showNotification(notification: NotificationMessage) {
        val current = _activeNotifications.value.toMutableList()
        current.add(notification)
        _activeNotifications.value = current
    }

    override suspend fun setStatusBarMessage(message: String) {
        statusBarMessage = message
    }

    override suspend fun createStatusBarItem(item: StatusBarItem): StatusBarItem {
        statusBarItems.add(item)
        return item
    }

    fun getStatusBarItems(): List<StatusBarItem> = statusBarItems.toList()

    fun updateStatusBarItem(id: String, text: String) {
        statusBarItems.find { it.id == id }?.text = text
    }

    fun removeStatusBarItem(id: String) {
        statusBarItems.removeAll { it.id == id }
    }
}

class WorkspaceAPIImpl(private val context: Context) : WorkspaceAPI {

    private val workspaceFolders = mutableListOf<String>()
    private val configuration = ConcurrentHashMap<String, Any>()

    override suspend fun getWorkspaceFolder(): String? {
        return workspaceFolders.firstOrNull()
    }

    override suspend fun getWorkspaceFolders(): List<String> {
        return workspaceFolders.toList()
    }

    override suspend fun getConfiguration(section: String): Map<String, Any> {
        return configuration.filter { it.key.startsWith(section) }
    }

    override suspend fun updateConfiguration(section: String, values: Map<String, Any>) {
        for ((key, value) in values) {
            configuration["$section.$key"] = value
        }
    }

    fun addWorkspaceFolder(path: String) {
        if (!workspaceFolders.contains(path)) {
            workspaceFolders.add(path)
        }
    }

    fun removeWorkspaceFolder(path: String) {
        workspaceFolders.remove(path)
    }

    fun setConfiguration(key: String, value: Any) {
        configuration[key] = value
    }

    fun getConfigurationValue(key: String, defaultValue: Any? = null): Any? {
        return configuration[key] ?: defaultValue
    }
}

// API Interfaces

interface EditorAPI {
    suspend fun openFile(filePath: String): EditorState
    suspend fun closeFile(filePath: String): Boolean
    suspend fun saveFile(filePath: String): Boolean
    suspend fun getActiveEditor(): EditorState?
    suspend fun getOpenEditors(): List<EditorState>
    suspend fun insertText(filePath: String, text: String, position: Int? = null): Boolean
    suspend fun getSelection(filePath: String): Pair<Int, Int>?
    suspend fun setSelection(filePath: String, start: Int, end: Int): Boolean
    suspend fun getCursorPosition(filePath: String): Int?
    suspend fun setCursorPosition(filePath: String, position: Int): Boolean
    suspend fun undo(filePath: String): Boolean
    suspend fun redo(filePath: String): Boolean
    suspend fun getLineCount(filePath: String): Int
    suspend fun getText(filePath: String): String?
    suspend fun getLanguage(filePath: String): String?
}

interface FileSystemAPI {
    suspend fun readFile(filePath: String): String
    suspend fun writeFile(filePath: String, content: String): Boolean
    suspend fun deleteFile(filePath: String): Boolean
    suspend fun createDirectory(dirPath: String): Boolean
    suspend fun listDirectory(dirPath: String): List<FileInfo>
    suspend fun exists(filePath: String): Boolean
    suspend fun rename(oldPath: String, newPath: String): Boolean
    suspend fun copy(sourcePath: String, destinationPath: String): Boolean
    suspend fun getFileTree(dirPath: String): FileTreeNode
    suspend fun watchFile(filePath: String, callback: (String) -> Unit)
    suspend fun unwatchFile(filePath: String)
}

interface TerminalAPI {
    suspend fun createTerminal(name: String): String
    suspend fun sendCommand(terminalId: String, command: String): String
    suspend fun getOutput(terminalId: String): String
    suspend fun killTerminal(terminalId: String): Boolean
    suspend fun getTerminals(): List<String>
}

interface BuildAPI {
    suspend fun build(config: Map<String, Any>? = null): String
    suspend fun run(config: Map<String, Any>? = null): String
    suspend fun debug(config: Map<String, Any>? = null): String
    suspend fun getBuildTasks(): List<Map<String, Any>>
    suspend fun getBuildConfig(): List<Map<String, Any>>
}

interface CommandAPI {
    suspend fun registerCommand(commandId: String, handler: suspend (Map<String, Any>?) -> Unit)
    suspend fun executeCommand(commandId: String, args: Map<String, Any>? = null): Boolean
    suspend fun getCommands(): List<String>
}

interface WindowAPI {
    suspend fun showMessage(message: String, type: NotificationType = NotificationType.INFO)
    suspend fun showInputBox(options: InputBoxOptions): String?
    suspend fun showQuickPick(items: List<QuickPickItem>): QuickPickItem?
    suspend fun showNotification(notification: NotificationMessage)
    suspend fun setStatusBarMessage(message: String)
    suspend fun createStatusBarItem(item: StatusBarItem): StatusBarItem
}

interface WorkspaceAPI {
    suspend fun getWorkspaceFolder(): String?
    suspend fun getWorkspaceFolders(): List<String>
    suspend fun getConfiguration(section: String): Map<String, Any>
    suspend fun updateConfiguration(section: String, values: Map<String, Any>)
}