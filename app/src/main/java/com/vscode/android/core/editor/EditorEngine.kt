package com.vscode.android.core.editor

import android.content.Context
import com.vscode.android.VSCodeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.util.UUID

class EditorEngine(private val context: Context) {

    private val tabs = mutableListOf<EditorTab>()
    private val undoStacks = mutableMapOf<String, UndoRedoStack>()
    private val searchResults = mutableListOf<SearchResult>()
    private val listeners = mutableListOf<EditorListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeTabId: String? = null
    private var nextUntitledCounter = 1

    private val syntaxHighlighter = SyntaxHighlighter()
    private val codeFormatter = CodeFormatter()
    private val suggestionProvider = SuggestionProvider()

    fun initialize() {
        tabs.clear()
        undoStacks.clear()
        searchResults.clear()
        activeTabId = null
    }

    fun openFile(filePath: String, content: String? = null): EditorTab {
        val existingTab = tabs.find { it.filePath == filePath }
        if (existingTab != null) {
            setActiveTab(existingTab.id)
            return existingTab
        }

        val resolvedContent = content ?: run {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val encoding = detectEncoding(file)
                    file.readText(Charset.forName(encoding))
                } else ""
            } catch (e: Exception) {
                ""
            }
        }

        val fileName = File(filePath).name
        val languageId = detectLanguage(filePath)

        val tab = EditorTab(
            id = UUID.randomUUID().toString(),
            title = fileName,
            filePath = filePath,
            content = resolvedContent,
            originalContent = resolvedContent,
            languageId = languageId,
            encoding = detectEncodingFromPath(filePath),
            isModified = false,
            isReadOnly = !File(filePath).canWrite()
        )

        tabs.add(tab)
        setActiveTab(tab.id)
        undoStacks[tab.id] = UndoRedoStack()
        notifyTabOpened(tab)
        return tab
    }

    fun openUntitled(content: String = ""): EditorTab {
        val tab = EditorTab(
            id = UUID.randomUUID().toString(),
            title = "Untitled-${nextUntitledCounter++}",
            filePath = null,
            content = content,
            originalContent = "",
            languageId = "plaintext",
            isModified = content.isNotEmpty()
        )
        tabs.add(tab)
        setActiveTab(tab.id)
        undoStacks[tab.id] = UndoRedoStack()
        notifyTabOpened(tab)
        return tab
    }

    fun closeTab(tabId: String): Boolean {
        val tab = tabs.find { it.id == tabId } ?: return false
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        undoStacks.remove(tabId)

        if (tabId == activeTabId) {
            if (tabs.isNotEmpty()) {
                val newIndex = index.coerceAtMost(tabs.size - 1)
                activeTabId = tabs[newIndex].id
                notifyTabActivated(tabs[newIndex])
            } else {
                activeTabId = null
            }
        }

        notifyTabClosed(tab)
        return true
    }

    fun closeAllTabs() {
        val allTabs = tabs.toList()
        tabs.clear()
        undoStacks.clear()
        activeTabId = null
        allTabs.forEach { notifyTabClosed(it) }
    }

    fun closeOtherTabs(tabId: String) {
        val keepTab = tabs.find { it.id == tabId } ?: return
        val others = tabs.filter { it.id != tabId }
        others.forEach { closeTab(it.id) }
        setActiveTab(tabId)
    }

    fun closeTabsToRight(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val toClose = tabs.drop(index + 1)
        toClose.forEach { closeTab(it.id) }
    }

    fun saveTab(tabId: String): Boolean {
        val tab = tabs.find { it.id == tabId } ?: return false
        val filePath = tab.filePath ?: return false

        return try {
            val file = File(filePath)
            val encoding = tab.encoding ?: "UTF-8"
            file.writeText(tab.content, Charset.forName(encoding))
            tab.originalContent = tab.content
            tab.isModified = false
            notifyTabSaved(tab)
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save file: $filePath", e)
            false
        }
    }

    fun saveAllTabs(): Int {
        var savedCount = 0
        for (tab in tabs) {
            if (tab.isModified && tab.filePath != null) {
                if (saveTab(tab.id)) savedCount++
            }
        }
        return savedCount
    }

    fun saveTabAs(tabId: String, newPath: String): Boolean {
        val tab = tabs.find { it.id == tabId } ?: return false
        return try {
            val file = File(newPath)
            val encoding = tab.encoding ?: "UTF-8"
            file.writeText(tab.content, Charset.forName(encoding))
            tab.filePath = newPath
            tab.title = file.name
            tab.originalContent = tab.content
            tab.isModified = false
            tab.languageId = detectLanguage(newPath)
            tab.encoding = detectEncodingFromPath(newPath)
            notifyTabSaved(tab)
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save file as: $newPath", e)
            false
        }
    }

    fun getActiveTab(): EditorTab? {
        return activeTabId?.let { tabs.find { tab -> tab.id == it } }
    }

    fun setActiveTab(tabId: String) {
        if (activeTabId != tabId) {
            activeTabId = tabId
            val tab = tabs.find { it.id == tabId }
            if (tab != null) {
                notifyTabActivated(tab)
            }
        }
    }

    fun getOpenTabs(): List<EditorTab> = tabs.toList()

    fun getUnsavedTabs(): List<EditorTab> = tabs.filter { it.isModified }

    fun getTabById(tabId: String): EditorTab? = tabs.find { it.id == tabId }

    fun updateTabContent(tabId: String, newContent: String) {
        val tab = tabs.find { it.id == tabId } ?: return
        if (tab.content != newContent) {
            val oldContent = tab.content
            tab.content = newContent
            tab.isModified = newContent != tab.originalContent
            undoStacks[tabId]?.push(UndoAction(oldContent, newContent, tab.cursorPosition))
            tab.cursorPosition = newContent.length
            notifyTabModified(tab)
        }
    }

    fun undo(tabId: String): Boolean {
        val tab = tabs.find { it.id == tabId } ?: return false
        val stack = undoStacks[tabId] ?: return false
        val action = stack.undo() ?: return false
        tab.content = action.oldContent
        tab.isModified = tab.content != tab.originalContent
        notifyTabModified(tab)
        return true
    }

    fun redo(tabId: String): Boolean {
        val tab = tabs.find { it.id == tabId } ?: return false
        val stack = undoStacks[tabId] ?: return false
        val action = stack.redo() ?: return false
        tab.content = action.newContent
        tab.isModified = tab.content != tab.originalContent
        notifyTabModified(tab)
        return true
    }

    fun canUndo(tabId: String): Boolean {
        return undoStacks[tabId]?.canUndo() ?: false
    }

    fun canRedo(tabId: String): Boolean {
        return undoStacks[tabId]?.canRedo() ?: false
    }

    fun searchInFile(tabId: String, query: String, caseSensitive: Boolean = false, wholeWord: Boolean = false, regex: Boolean = false): List<FileSearchResult> {
        val tab = tabs.find { it.id == tabId } ?: return emptyList()
        val results = mutableListOf<FileSearchResult>()

        try {
            val pattern = when {
                regex -> Regex(query, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                wholeWord -> Regex("\\b${Regex.escape(query)}\\b", if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                else -> Regex(Regex.escape(query), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            }

            val lines = tab.content.lines()
            for ((lineIndex, line) in lines.withIndex()) {
                pattern.findAll(line).forEach { match ->
                    results.add(FileSearchResult(
                        filePath = tab.filePath ?: tab.title,
                        lineNumber = lineIndex + 1,
                        column = match.range.first + 1,
                        lineContent = line,
                        matchStart = match.range.first,
                        matchEnd = match.range.last + 1
                    ))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Search error", e)
        }

        return results
    }

    fun searchInFiles(
        rootPath: String,
        query: String,
        filePattern: String = "*",
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        regex: Boolean = false
    ): List<FileSearchResult> {
        val results = mutableListOf<FileSearchResult>()
        searchResults.clear()

        try {
            val pattern = when {
                regex -> Regex(query, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                wholeWord -> Regex("\\b${Regex.escape(query)}\\b", if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                else -> Regex(Regex.escape(query), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            }

            val rootDir = File(rootPath)
            if (!rootDir.exists()) return emptyList()

            val files = findFiles(rootDir, filePattern)
            for (file in files) {
                try {
                    val lines = file.readLines()
                    for ((lineIndex, line) in lines.withIndex()) {
                        pattern.findAll(line).forEach { match ->
                            val result = FileSearchResult(
                                filePath = file.absolutePath,
                                lineNumber = lineIndex + 1,
                                column = match.range.first + 1,
                                lineContent = line,
                                matchStart = match.range.first,
                                matchEnd = match.range.last + 1
                            )
                            results.add(result)
                        }
                    }
                } catch (_: Exception) {
                    // Skip files that can't be read
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Search in files error", e)
        }

        searchResults.addAll(results.map { result ->
            SearchResult(
                filePath = result.filePath,
                lineNumber = result.lineNumber,
                column = result.column,
                lineContent = result.lineContent,
                matchStart = result.matchStart,
                matchEnd = result.matchEnd
            )
        })

        return results
    }

    fun replaceInFile(
        tabId: String,
        searchQuery: String,
        replacement: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        regex: Boolean = false
    ): Int {
        val tab = tabs.find { it.id == tabId } ?: return 0
        var replaceCount = 0

        try {
            val pattern = when {
                regex -> Regex(searchQuery, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                wholeWord -> Regex("\\b${Regex.escape(searchQuery)}\\b", if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                else -> Regex(Regex.escape(searchQuery), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            }

            val newContent = pattern.replace(tab.content, replacement)
            replaceCount = pattern.findAll(tab.content).count()
            tab.content = newContent
            tab.isModified = newContent != tab.originalContent
            notifyTabModified(tab)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Replace error", e)
        }

        return replaceCount
    }

    fun replaceAllInFiles(
        rootPath: String,
        searchQuery: String,
        replacement: String,
        filePattern: String = "*",
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        regex: Boolean = false
    ): Int {
        var totalReplacements = 0

        try {
            val pattern = when {
                regex -> Regex(searchQuery, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                wholeWord -> Regex("\\b${Regex.escape(searchQuery)}\\b", if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                else -> Regex(Regex.escape(searchQuery), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            }

            val rootDir = File(rootPath)
            if (!rootDir.exists()) return 0

            val files = findFiles(rootDir, filePattern)
            for (file in files) {
                try {
                    val content = file.readText()
                    val newContent = pattern.replace(content, replacement)
                    val replacements = pattern.findAll(content).count()
                    if (replacements > 0) {
                        file.writeText(newContent)
                        totalReplacements += replacements
                    }
                } catch (_: Exception) {
                    // Skip files that can't be modified
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Replace all error", e)
        }

        return totalReplacements
    }

    fun getSuggestions(tabId: String, cursorPosition: Int): List<Suggestion> {
        val tab = tabs.find { it.id == tabId } ?: return emptyList()
        return suggestionProvider.getSuggestions(tab, cursorPosition)
    }

    fun highlightSyntax(tabId: String): List<SyntaxToken> {
        val tab = tabs.find { it.id == tabId } ?: return emptyList()
        return syntaxHighlighter.tokenize(tab.content, tab.languageId)
    }

    fun formatDocument(tabId: String): Boolean {
        val tab = tabs.find { it.id == tabId } ?: return false
        val formatted = codeFormatter.format(tab.content, tab.languageId, tab.indentSize, tab.useTabs)
        if (formatted != tab.content) {
            tab.content = formatted
            tab.isModified = formatted != tab.originalContent
            notifyTabModified(tab)
            return true
        }
        return false
    }

    fun formatSelection(tabId: String, start: Int, end: Int): String? {
        val tab = tabs.find { it.id == tabId } ?: return null
        val selected = tab.content.substring(start, end)
        return codeFormatter.format(selected, tab.languageId, tab.indentSize, tab.useTabs)
    }

    fun getLanguageId(tabId: String): String {
        return tabs.find { it.id == tabId }?.languageId ?: "plaintext"
    }

    fun setLanguageId(tabId: String, languageId: String) {
        tabs.find { it.id == tabId }?.let { tab ->
            tab.languageId = languageId
            notifyTabModified(tab)
        }
    }

    fun addListener(listener: EditorListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: EditorListener) {
        listeners.remove(listener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    fun shutdown() {
        tabs.clear()
        undoStacks.clear()
        searchResults.clear()
        listeners.clear()
        activeTabId = null
    }

    private fun notifyTabOpened(tab: EditorTab) {
        listeners.forEach { it.onTabOpened(tab) }
    }

    private fun notifyTabClosed(tab: EditorTab) {
        listeners.forEach { it.onTabClosed(tab) }
    }

    private fun notifyTabActivated(tab: EditorTab) {
        listeners.forEach { it.onTabActivated(tab) }
    }

    private fun notifyTabModified(tab: EditorTab) {
        listeners.forEach { it.onTabModified(tab) }
    }

    private fun notifyTabSaved(tab: EditorTab) {
        listeners.forEach { it.onTabSaved(tab) }
    }

    private fun detectLanguage(filePath: String): String {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py", "pyw" -> "python"
            "js", "jsx", "mjs" -> "javascript"
            "ts", "tsx" -> "typescript"
            "cpp", "cc", "cxx", "c++" -> "cpp"
            "c", "h" -> "c"
            "html", "htm" -> "html"
            "css", "scss", "less" -> "css"
            "json" -> "json"
            "xml", "plist" -> "xml"
            "md", "markdown" -> "markdown"
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

    private fun detectEncoding(file: File): String {
        return try {
            FileInputStream(file).use { fis ->
                val bom = ByteArray(4)
                val read = fis.read(bom, 0, 4)
                when {
                    read >= 4 && bom[0] == 0x00.toByte() && bom[1] == 0x00.toByte() && bom[2] == 0xFE.toByte() && bom[3] == 0xFF.toByte() -> "UTF-32BE"
                    read >= 4 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() && bom[2] == 0x00.toByte() && bom[3] == 0x00.toByte() -> "UTF-32LE"
                    read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> "UTF-8"
                    read >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> "UTF-16BE"
                    read >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> "UTF-16LE"
                    read >= 2 && bom[0] == 0x00.toByte() && bom[1] == 0x00.toByte() && bom[2] == 0x00.toByte() -> "UTF-32BE"
                    else -> "UTF-8"
                }
            }
        } catch (e: Exception) {
            "UTF-8"
        }
    }

    private fun detectEncodingFromPath(filePath: String): String {
        return try {
            val file = File(filePath)
            if (file.exists()) detectEncoding(file) else "UTF-8"
        } catch (e: Exception) {
            "UTF-8"
        }
    }

    private fun findFiles(rootDir: File, pattern: String): List<File> {
        val result = mutableListOf<File>()
        val regex = globToRegex(pattern)

        fun walk(dir: File) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    if (!file.name.startsWith(".") && file.name != "node_modules" && file.name != ".git") {
                        walk(file)
                    }
                } else if (file.name.matches(regex)) {
                    result.add(file)
                }
            }
        }

        walk(rootDir)
        return result
    }

    private fun globToRegex(glob: String): Regex {
        val escaped = Regex.escape(glob)
            .replace("\\*", ".*")
            .replace("\\?", ".")
        return Regex("^$escaped$", RegexOption.IGNORE_CASE)
    }

    interface EditorListener {
        fun onTabOpened(tab: EditorTab) {}
        fun onTabClosed(tab: EditorTab) {}
        fun onTabActivated(tab: EditorTab) {}
        fun onTabModified(tab: EditorTab) {}
        fun onTabSaved(tab: EditorTab) {}
    }

    companion object {
        private const val TAG = "EditorEngine"
    }
}

data class EditorTab(
    val id: String,
    var title: String,
    var filePath: String?,
    var content: String,
    var originalContent: String,
    var languageId: String,
    var encoding: String? = "UTF-8",
    var isModified: Boolean = false,
    var isReadOnly: Boolean = false,
    var cursorPosition: Int = 0,
    var indentSize: Int = 4,
    var useTabs: Boolean = false
)

data class SyntaxToken(
    val start: Int,
    val end: Int,
    val type: com.vscode.android.VSCodeApp.SyntaxTokenType
)

data class Suggestion(
    val label: String,
    val insertText: String,
    val detail: String = "",
    val kind: SuggestionKind = SuggestionKind.TEXT
)

enum class SuggestionKind {
    TEXT, METHOD, FUNCTION, CONSTRUCTOR, FIELD, VARIABLE,
    CLASS, INTERFACE, MODULE, PROPERTY, UNIT, VALUE,
    ENUM, KEYWORD, SNIPPET, COLOR, FILE, REFERENCE, FOLDER
}

data class SearchResult(
    val filePath: String,
    val lineNumber: Int,
    val column: Int,
    val lineContent: String,
    val matchStart: Int,
    val matchEnd: Int
)

data class FileSearchResult(
    val filePath: String,
    val lineNumber: Int,
    val column: Int,
    val lineContent: String,
    val matchStart: Int,
    val matchEnd: Int
)

data class UndoAction(
    val oldContent: String,
    val newContent: String,
    val cursorPosition: Int
)

class UndoRedoStack {
    private val undoStack = mutableListOf<UndoAction>()
    private val redoStack = mutableListOf<UndoAction>()
    private val maxSize = 100

    fun push(action: UndoAction) {
        undoStack.add(action)
        if (undoStack.size > maxSize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(): UndoAction? {
        if (undoStack.isEmpty()) return null
        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)
        return action
    }

    fun redo(): UndoAction? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)
        return action
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}