package com.vscode.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.vscode.android.core.editor.EditorEngine
import com.vscode.android.core.editor.EditorTab
import com.vscode.android.core.editor.SyntaxHighlighter
import com.vscode.android.core.editor.SyntaxToken
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var editorEngine: EditorEngine
    private lateinit var syntaxHighlighter: SyntaxHighlighter
    private var currentProjectPath: String? = null
    private var isHighlighting = false

    // UI references
    private lateinit var toolbarTitle: TextView
    private lateinit var tabContainer: LinearLayout
    private lateinit var editorContent: FrameLayout
    private lateinit var welcomeScreen: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var terminalOutput: TextView
    private lateinit var terminalInput: EditText
    private lateinit var terminalScroll: ScrollView
    private lateinit var panelTabLabel: TextView
    private lateinit var bottomBar: LinearLayout

    private val editorEditText: EditText by lazy {
        val editText = EditText(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = null
            setTextColor(Color.parseColor("#D4D4D4"))
            setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 14f
            setPadding(dp(16), dp(8), dp(16), dp(8))
            gravity = Gravity.TOP or Gravity.START
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            inputType = EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
                    EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    EditorInfo.TYPE_CLASS_TEXT
            setHorizontallyScrolling(true)
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
        editText
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editorEngine = EditorEngine(this)
        syntaxHighlighter = SyntaxHighlighter()

        initViews()
        setupListeners()
        setupEditorEngineListener()
        editorEngine.initialize()
        updateUI()
    }

    private fun initViews() {
        toolbarTitle = findViewById(R.id.toolbar_title)
        tabContainer = findViewById(R.id.tab_container)
        editorContent = findViewById(R.id.editor_content)
        welcomeScreen = findViewById(R.id.welcome_screen)
        bottomPanel = findViewById(R.id.bottom_panel)
        terminalOutput = findViewById(R.id.terminal_output)
        terminalInput = findViewById(R.id.terminal_input)
        terminalScroll = findViewById(R.id.terminal_scroll)
        panelTabLabel = findViewById(R.id.panel_tab_label)
        bottomBar = findViewById(R.id.bottom_bar)
    }

    @SuppressLint("SetTextI18n")
    private fun setupListeners() {
        // Toolbar
        findViewById<ImageButton>(R.id.toolbar_menu).setOnClickListener {
            showFileExplorerDialog()
        }
        findViewById<ImageButton>(R.id.toolbar_save).setOnClickListener {
            saveCurrentFile()
        }
        findViewById<ImageButton>(R.id.toolbar_run).setOnClickListener {
            runCurrentFile()
        }
        findViewById<ImageButton>(R.id.toolbar_search).setOnClickListener {
            showSearchDialog()
        }
        findViewById<ImageButton>(R.id.toolbar_more).setOnClickListener {
            showMoreMenu()
        }

        // Welcome screen
        findViewById<TextView>(R.id.welcome_open_file).setOnClickListener {
            openFile()
        }
        findViewById<TextView>(R.id.welcome_open_folder).setOnClickListener {
            openFolder()
        }
        findViewById<TextView>(R.id.welcome_new_file).setOnClickListener {
            createNewFile()
        }

        // Bottom bar
        findViewById<LinearLayout>(R.id.bottom_files).setOnClickListener {
            showFileExplorerDialog()
        }
        findViewById<LinearLayout>(R.id.bottom_terminal).setOnClickListener {
            toggleTerminal()
        }
        findViewById<LinearLayout>(R.id.bottom_plugins).setOnClickListener {
            showExtensionsDialog()
        }
        findViewById<LinearLayout>(R.id.bottom_settings).setOnClickListener {
            showSettingsDialog()
        }

        // Panel
        findViewById<ImageButton>(R.id.panel_close).setOnClickListener {
            bottomPanel.visibility = View.GONE
        }
        findViewById<ImageButton>(R.id.panel_new_terminal).setOnClickListener {
            clearTerminal()
        }

        // Terminal input
        terminalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                executeTerminalCommand()
                true
            } else false
        }
    }

    private fun setupEditorEngineListener() {
        editorEngine.addListener(object : EditorEngine.EditorListener {
            override fun onTabOpened(tab: EditorTab) {
                runOnUiThread {
                    addTabToBar(tab)
                    switchToTab(tab)
                    updateUI()
                }
            }

            override fun onTabClosed(tab: EditorTab) {
                runOnUiThread {
                    removeTabFromBar(tab.id)
                    if (editorEngine.getOpenTabs().isEmpty()) {
                        showWelcome()
                    }
                    updateUI()
                }
            }

            override fun onTabActivated(tab: EditorTab) {
                runOnUiThread {
                    switchToTab(tab)
                    updateUI()
                }
            }

            override fun onTabModified(tab: EditorTab) {
                runOnUiThread {
                    updateTabLabel(tab)
                    updateUI()
                }
            }

            override fun onTabSaved(tab: EditorTab) {
                runOnUiThread {
                    updateTabLabel(tab)
                    updateUI()
                }
            }
        })
    }

    // ==================== File Operations ====================

    private fun openFile() {
        fileOpenLauncher.launch(arrayOf("*/*"))
    }

    private fun openFolder() {
        folderOpenLauncher.launch(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary"))
    }

    @SuppressLint("SetTextI18n")
    private fun createNewFile() {
        val input = EditText(this).apply {
            hint = "filename.ext"
            setTextColor(Color.WHITE)
            setText("Untitled.txt")
        }
        AlertDialog.Builder(this)
            .setTitle("New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                val tab = editorEngine.openUntitled()
                editorEngine.saveTabAs(tab.id, File(filesDir, name).absolutePath)
                showEditor()
                editorEditText.setText(tab.content)
                triggerHighlight()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveCurrentFile() {
        val tab = editorEngine.getActiveTab() ?: return
        if (tab.filePath != null) {
            val content = editorEditText.text.toString()
            editorEngine.updateTabContent(tab.id, content)
            editorEngine.saveTab(tab.id)
            Toast.makeText(this, "Saved: ${tab.title}", Toast.LENGTH_SHORT).show()
        } else {
            saveCurrentFileAs()
        }
    }

    private fun saveCurrentFileAs() {
        saveFileLauncher.launch(
            editorEngine.getActiveTab()?.title ?: "Untitled.txt")
    }

    private fun runCurrentFile() {
        val tab = editorEngine.getActiveTab() ?: return
        val content = editorEditText.text.toString()
        editorEngine.updateTabContent(tab.id, content)

        if (tab.languageId == "python" || tab.languageId == "shell") {
            showTerminal()
            val scriptPath = tab.filePath ?: return
            appendTerminalOutput("\n> Running: ${tab.title}\n")
            executeCommand(tab.languageId, scriptPath)
        } else {
            Toast.makeText(this, "Run support: Python, Shell scripts\nUse Termux for compiled languages", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== Tab Management ====================

    @SuppressLint("SetTextI18n")
    private fun addTabToBar(tab: EditorTab) {
        val tabView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = tab.id
            setPadding(dp(8), 0, dp(4), 0)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(if (tab.id == editorEngine.getActiveTab()?.id) "#1E1E1E" else "#2D2D2D"))
            minimumWidth = dp(100)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val label = TextView(this).apply {
            text = if (tab.isModified) "• ${tab.title}" else tab.title
            setTextColor(if (tab.id == editorEngine.getActiveTab()?.id)
                Color.parseColor("#CCCCCC") else Color.parseColor("#999999"))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(4), 0)
            tag = "label"
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#999999"))
            textSize = 14f
            setPadding(dp(6), 0, dp(2), 0)
            setOnClickListener {
                val t = editorEngine.getTabById(tab.id)
                if (t != null && t.isModified) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Unsaved changes")
                        .setMessage("Save changes to ${t.title}?")
                        .setPositiveButton("Save") { _, _ ->
                            editorEngine.updateTabContent(t.id, editorEditText.text.toString())
                            editorEngine.saveTab(t.id)
                            editorEngine.closeTab(t.id)
                        }
                        .setNegativeButton("Discard") { _, _ ->
                            editorEngine.closeTab(t.id)
                        }
                        .setNeutralButton("Cancel", null)
                        .show()
                } else {
                    editorEngine.closeTab(tab.id)
                }
            }
        }

        tabView.addView(label)
        tabView.addView(closeBtn)
        tabView.setOnClickListener {
            val t = editorEngine.getTabById(tab.id)
            if (t != null) editorEngine.setActiveTab(t.id)
        }

        tabContainer.addView(tabView)
    }

    private fun removeTabFromBar(tabId: String) {
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i)
            if (child.tag == tabId) {
                tabContainer.removeViewAt(i)
                break
            }
        }
    }

    private fun updateTabLabel(tab: EditorTab) {
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i) as? LinearLayout ?: continue
            if (child.tag == tab.id) {
                val label = child.findViewWithTag<TextView>("label")
                label?.text = if (tab.isModified) "• ${tab.title}" else tab.title
                break
            }
        }
    }

    private fun switchToTab(tab: EditorTab) {
        // Save current content
        val currentTab = editorEngine.getActiveTab()
        if (currentTab != null && currentTab.id != tab.id) {
            val content = editorEditText.text.toString()
            editorEngine.updateTabContent(currentTab.id, content)
        }

        // Update tab bar highlights
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i) as? LinearLayout ?: continue
            val isActive = child.tag == tab.id
            child.setBackgroundColor(Color.parseColor(if (isActive) "#1E1E1E" else "#2D2D2D"))
            val label = child.findViewWithTag<TextView>("label")
            label?.setTextColor(Color.parseColor(if (isActive) "#CCCCCC" else "#999999"))
        }

        // Update editor content
        editorEditText.setText(tab.content)
        editorEditText.isEnabled = !tab.isReadOnly
        toolbarTitle.text = if (tab.filePath != null) tab.title else tab.title
        triggerHighlight()
    }

    // ==================== Editor ====================

    private fun showEditor() {
        welcomeScreen.visibility = View.GONE
        if (editorEditText.parent == null) {
            editorContent.addView(editorEditText)
        }
        editorEditText.visibility = View.VISIBLE
    }

    private fun showWelcome() {
        welcomeScreen.visibility = View.VISIBLE
        editorEditText.visibility = View.GONE
        toolbarTitle.text = "Android VSCode"
    }

    private fun triggerHighlight() {
        if (isHighlighting) return
        isHighlighting = true
        editorEditText.postDelayed({
            doHighlight()
            isHighlighting = false
        }, 100)
    }

    private fun doHighlight() {
        val tab = editorEngine.getActiveTab() ?: return
        val content = editorEditText.text.toString()
        if (content.isEmpty()) return

        editorEngine.updateTabContent(tab.id, content)
        val tokens = syntaxHighlighter.tokenize(content, tab.languageId)
        if (tokens.isEmpty()) return

        val spannable = SpannableStringBuilder(content)
        for (token in tokens) {
            if (token.start >= content.length || token.end > content.length) continue
            val color = when (token.type) {
                VSCodeApp.SyntaxTokenTypeKEYWORD -> Color.parseColor("#569CD6")
                VSCodeApp.SyntaxTokenTypeSTRING -> Color.parseColor("#CE9178")
                VSCodeApp.SyntaxTokenTypeCOMMENT -> Color.parseColor("#6A9955")
                VSCodeApp.SyntaxTokenTypeNUMBER -> Color.parseColor("#B5CEA8")
                VSCodeApp.SyntaxTokenTypeTYPE -> Color.parseColor("#4EC9B0")
                VSCodeApp.SyntaxTokenTypeFUNCTION -> Color.parseColor("#DCDCAA")
                VSCodeApp.SyntaxTokenTypeVARIABLE -> Color.parseColor("#9CDCFE")
                VSCodeApp.SyntaxTokenTypeCONSTANT -> Color.parseColor("#569CD6")
                VSCodeApp.SyntaxTokenTypeOPERATOR -> Color.parseColor("#D4D4D4")
                VSCodeApp.SyntaxTokenTypeCLASS_NAME -> Color.parseColor("#4EC9B0")
                VSCodeApp.SyntaxTokenTypeTAG -> Color.parseColor("#569CD6")
                VSCodeApp.SyntaxTokenTypeATTRIBUTE_NAME -> Color.parseColor("#9CDCFE")
                VSCodeApp.SyntaxTokenTypeATTRIBUTE_VALUE -> Color.parseColor("#CE9178")
                VSCodeApp.SyntaxTokenTypePROPERTY -> Color.parseColor("#9CDCFE")
                else -> Color.parseColor("#D4D4D4")
            }
            try {
                spannable.setSpan(ForegroundColorSpan(color), token.start, token.end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } catch (_: Exception) {}
        }

        val selStart = editorEditText.selectionStart
        val selEnd = editorEditText.selectionEnd
        editorEditText.setText(spannable)
        editorEditText.setSelection(selStart.coerceAtMost(spannable.length), selEnd.coerceAtMost(spannable.length))

        // Re-add text watcher for highlighting
        editorEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                triggerHighlight()
            }
        })
    }

    // ==================== Terminal ====================

    private fun toggleTerminal() {
        if (bottomPanel.visibility == View.VISIBLE) {
            bottomPanel.visibility = View.GONE
        } else {
            showTerminal()
        }
    }

    private fun showTerminal() {
        bottomPanel.visibility = View.VISIBLE
        panelTabLabel.text = "TERMINAL"
        if (terminalOutput.text.isEmpty()) {
            appendTerminalOutput("Android VSCode Terminal\nType 'help' for available commands.\n\n")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun clearTerminal() {
        terminalOutput.text = ""
        appendTerminalOutput("Terminal cleared.\n\n")
    }

    private fun appendTerminalOutput(text: String) {
        terminalOutput.append(text)
        terminalScroll.post {
            terminalScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun executeTerminalCommand() {
        val cmd = terminalInput.text.toString().trim()
        if (cmd.isEmpty()) return

        appendTerminalOutput("$ $cmd\n")
        terminalInput.setText("")

        when {
            cmd == "clear" || cmd == "cls" -> clearTerminal()
            cmd == "help" -> appendTerminalOutput("""
                |Available commands:
                |  help     - Show this help
                |  clear    - Clear terminal
                |  ls       - List files
                |  pwd      - Print working directory
                |  cd <dir> - Change directory
                |  cat <f>  - Show file contents
                |  echo <t> - Print text
                |  python   - Run Python interpreter
                |  date     - Show current date/time
                |  whoami   - Show current user
                |  
                |For full language support, use Termux.
                |
            """.trimMargin())
            cmd.startsWith("cd ") -> {
                val dir = cmd.removePrefix("cd ").trim()
                appendTerminalOutput("cd: directory change not supported in system terminal\n")
            }
            else -> executeCommand("shell", cmd)
        }
    }

    private fun executeCommand(type: String, command: String) {
        Thread {
            try {
                val process = when (type) {
                    "python" -> {
                        val pb = ProcessBuilder("python3", command)
                        pb.redirectErrorStream(true)
                        pb.start()
                    }
                    "shell" -> {
                        val pb = ProcessBuilder("sh", "-c", command)
                        pb.redirectErrorStream(true)
                        pb.start()
                    }
                    else -> return@Thread
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                process.waitFor()
                reader.close()

                runOnUiThread {
                    appendTerminalOutput(output.toString())
                    if (process.exitValue() != 0) {
                        appendTerminalOutput("[Exit code: ${process.exitValue()}]\n")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendTerminalOutput("Error: ${e.message}\n")
                }
            }
        }.start()
    }

    // ==================== Dialogs ====================

    @SuppressLint("SetTextI18n")
    private fun showFileExplorerDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pathLabel = TextView(this).apply {
            text = currentProjectPath ?: "No folder opened"
            setTextColor(Color.parseColor("#999999"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(pathLabel)

        val btnOpen = Button(this).apply {
            text = "Open Folder"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#0E639C"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                openFolder()
            }
        }
        titleRow.addView(btnOpen)

        dialogView.addView(titleRow)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(400)
            )
        }

        val fileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Display files
        if (currentProjectPath != null) {
            val root = File(currentProjectPath!!)
            if (root.exists()) {
                addFileTreeItems(fileList, root, 0)
            }
        } else {
            val noFiles = TextView(this).apply {
                text = "No folder opened.\nOpen a folder to browse files."
                setTextColor(Color.parseColor("#666666"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(32), dp(16), dp(32))
            }
            fileList.addView(noFiles)
        }

        scrollView.addView(fileList)
        dialogView.addView(scrollView)

        val dialog = AlertDialog.Builder(this)
            .setTitle("File Explorer")
            .setView(dialogView)
            .setPositiveButton("New File") { _, _ -> createNewFile() }
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun addFileTreeItems(parent: LinearLayout, dir: File, depth: Int) {
        val files = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: return

        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                val folderIcon = if (depth == 0) "📁 " else "📂 "
                val item = TextView(this).apply {
                    text = "${"  ".repeat(depth)}$folderIcon${file.name}/"
                    setTextColor(Color.parseColor("#DCDCAA"))
                    textSize = 13f
                    setPadding(dp(8 + depth * 12), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        // Expand/collapse or select
                        currentProjectPath = file.absolutePath
                        showFileExplorerDialog()
                    }
                }
                parent.addView(item)
            } else {
                val ext = file.extension.lowercase()
                val icon = when (ext) {
                    "kt", "kts" -> "🟠"
                    "java" -> "☕"
                    "py" -> "🐍"
                    "cpp", "c", "h", "hpp" -> "🔵"
                    "js", "ts", "jsx", "tsx" -> "🟡"
                    "html", "htm" -> "🌐"
                    "css", "scss" -> "🎨"
                    "json" -> "📋"
                    "xml" -> "📄"
                    "md" -> "📝"
                    "sh", "bash" -> "💻"
                    "gradle" -> "🐘"
                    "yml", "yaml" -> "⚙️"
                    else -> "📄"
                }
                val item = TextView(this).apply {
                    text = "${"  ".repeat(depth)}$icon ${file.name}"
                    setTextColor(Color.parseColor("#CCCCCC"))
                    textSize = 13f
                    setPadding(dp(8 + depth * 12), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        openFileByPath(file.absolutePath)
                    }
                }
                parent.addView(item)
            }
        }
    }

    private fun openFileByPath(path: String) {
        try {
            val file = File(path)
            if (file.exists() && file.isFile) {
                val content = file.readText()
                val tab = editorEngine.openFile(path, content)
                showEditor()
                editorEditText.setText(tab.content)
                toolbarTitle.text = tab.title
                triggerHighlight()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Search..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666666"))
        }
        AlertDialog.Builder(this)
            .setTitle("Find")
            .setView(input)
            .setPositiveButton("Find") { _, _ ->
                val query = input.text.toString()
                val tab = editorEngine.getActiveTab()
                if (tab != null) {
                    val results = editorEngine.searchInFile(tab.id, query, caseSensitive = false)
                    if (results.isNotEmpty()) {
                        val text = editorEditText.text.toString()
                        val idx = text.indexOf(query, ignoreCase = true)
                        if (idx >= 0) {
                            editorEditText.setSelection(idx, idx + query.length)
                            editorEditText.requestFocus()
                        }
                        Toast.makeText(this, "Found ${results.size} matches", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No matches found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoreMenu() {
        val items = arrayOf(
            "Save As", "Undo", "Redo", "Format Document",
            "Change Language", "Close Tab", "Close All Tabs"
        )
        AlertDialog.Builder(this)
            .setTitle("More")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> saveCurrentFileAs()
                    1 -> {
                        val tab = editorEngine.getActiveTab()
                        if (tab != null) editorEngine.undo(tab.id)
                        editorEditText.setText(tab?.content)
                        triggerHighlight()
                    }
                    2 -> {
                        val tab = editorEngine.getActiveTab()
                        if (tab != null) editorEngine.redo(tab.id)
                        editorEditText.setText(tab?.content)
                        triggerHighlight()
                    }
                    3 -> {
                        val tab = editorEngine.getActiveTab()
                        if (tab != null) {
                            editorEngine.updateTabContent(tab.id, editorEditText.text.toString())
                            editorEngine.formatDocument(tab.id)
                            editorEditText.setText(tab.content)
                            triggerHighlight()
                        }
                    }
                    4 -> showLanguagePicker()
                    5 -> {
                        editorEngine.getActiveTab()?.let { editorEngine.closeTab(it.id) }
                    }
                    6 -> {
                        editorEngine.closeAllTabs()
                        showWelcome()
                    }
                }
            }
            .show()
    }

    private fun showLanguagePicker() {
        val languages = arrayOf(
            "Kotlin", "Java", "Python", "C++", "C", "JavaScript",
            "TypeScript", "HTML", "CSS", "JSON", "XML", "Markdown",
            "Shell", "SQL", "YAML", "Go", "Rust", "Plain Text"
        )
        val ids = arrayOf(
            "kotlin", "java", "python", "cpp", "c", "javascript",
            "typescript", "html", "css", "json", "xml", "markdown",
            "shell", "sql", "yaml", "go", "rust", "plaintext"
        )
        AlertDialog.Builder(this)
            .setTitle("Language")
            .setItems(languages) { _, which ->
                val tab = editorEngine.getActiveTab()
                if (tab != null) {
                    editorEngine.setLanguageId(tab.id, ids[which])
                    triggerHighlight()
                }
            }
            .show()
    }

    private fun showExtensionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Extensions")
            .setMessage("Default extensions loaded:\n\n" +
                    "• Python Support\n" +
                    "• Java Support\n" +
                    "• C++ Support\n" +
                    "• Kotlin Support\n" +
                    "• APK Builder\n" +
                    "• Syntax Highlighting\n" +
                    "• Code Completion\n\n" +
                    "Custom plugins can be installed from\n" +
                    "the plugin marketplace.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            "Language: ${getCurrentLanguage()}",
            "Theme: VS Code Dark",
            "Font Size: 14",
            "Tab Size: 4",
            "About Android VSCode"
        )
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(items) { _, which ->
                when (which) {
                    // Placeholder for future settings
                    4 -> {
                        AlertDialog.Builder(this)
                            .setTitle("About")
                            .setMessage("Android VSCode v1.0\n\n" +
                                    "A mobile code editor inspired by\n" +
                                    "Visual Studio Code.\n\n" +
                                    "Supports multiple languages,\n" +
                                    "syntax highlighting, and Termux\n" +
                                    "integration for full development.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun getCurrentLanguage(): String {
        return when (Locale.getDefault().language) {
            "zh" -> "中文"
            "ja" -> "日本語"
            else -> "English"
        }
    }

    // ==================== Activity Result Launchers ====================

    private val fileOpenLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()

                val fileName = getFileName(uri) ?: "Untitled"
                val cachePath = File(cacheDir, fileName).absolutePath
                File(cachePath).writeText(content)

                val tab = editorEngine.openFile(cachePath, content)
                showEditor()
                editorEditText.setText(tab.content)
                toolbarTitle.text = tab.title
                triggerHighlight()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val folderOpenLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            val path = getPathFromUri(it)
            currentProjectPath = path
            Toast.makeText(this, "Opened: ${path ?: "folder"}", Toast.LENGTH_SHORT).show()
        }
    }

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val content = editorEditText.text.toString()
                val outputStream = contentResolver.openOutputStream(it)
                outputStream?.write(content.toByteArray())
                outputStream?.close()

                val tab = editorEngine.getActiveTab()
                if (tab != null) {
                    editorEngine.updateTabContent(tab.id, content)
                    val filePath = getPathFromUri(it) ?: it.toString()
                    editorEngine.saveTabAs(tab.id, filePath)
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== Helpers ====================

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if (split.size >= 2) {
                "${Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
            } else {
                Environment.getExternalStorageDirectory().absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateUI() {
        val tab = editorEngine.getActiveTab()
        if (tab != null) {
            showEditor()
            toolbarTitle.text = if (tab.isModified) "• ${tab.title}" else tab.title
        } else if (editorEngine.getOpenTabs().isEmpty()) {
            showWelcome()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    // ==================== Lifecycle ====================

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle orientation changes
    }

    override fun onBackPressed() {
        if (bottomPanel.visibility == View.VISIBLE) {
            bottomPanel.visibility = View.GONE
            return
        }
        val unsavedTabs = editorEngine.getUnsavedTabs()
        if (unsavedTabs.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("You have ${unsavedTabs.size} unsaved file(s). Exit anyway?")
                .setPositiveButton("Exit") { _, _ -> super.onBackPressed() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        editorEngine.shutdown()
    }
}