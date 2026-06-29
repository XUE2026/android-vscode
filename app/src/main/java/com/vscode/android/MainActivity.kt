package com.vscode.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.vscode.android.core.editor.CursorManager
import com.vscode.android.core.editor.EditorEngine
import com.vscode.android.core.editor.EditorTab
import com.vscode.android.core.project.ProjectManager
import com.vscode.android.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var editorEngine: EditorEngine
    private lateinit var projectManager: ProjectManager
    private lateinit var cursorManager: CursorManager
    private lateinit var app: VSCodeApp

    private var currentActivityBarSelection: ActivityBarItem = ActivityBarItem.EXPLORER
    private var currentPanelTab: PanelTab = PanelTab.TERMINAL
    private var isSidebarVisible = true
    private var isPanelVisible = true
    private var sidebarWidth = 280

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { handleFolderOpen(it) }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleFileOpen(it) }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { handleFileCreate(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = VSCodeApp.getInstance()
        editorEngine = app.editorEngine
        projectManager = app.projectManager
        cursorManager = CursorManager()

        setupEdgeToEdge()
        setupActivityBar()
        setupSidebar()
        setupTabBar()
        setupPanel()
        setupStatusBar()
        setupEditorListeners()

        requestStoragePermissions()

        handleIntent(intent)
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
    }

    private fun setupActivityBar() {
        val explorerBtn = binding.activityBarExplorer
        val searchBtn = binding.activityBarSearch
        val sourceControlBtn = binding.activityBarSourceControl
        val extensionsBtn = binding.activityBarExtensions
        val debugBtn = binding.activityBarDebug
        val settingsBtn = binding.activityBarSettings

        val buttons = mapOf(
            ActivityBarItem.EXPLORER to explorerBtn,
            ActivityBarItem.SEARCH to searchBtn,
            ActivityBarItem.SOURCE_CONTROL to sourceControlBtn,
            ActivityBarItem.EXTENSIONS to extensionsBtn,
            ActivityBarItem.DEBUG to debugBtn
        )

        buttons.forEach { (item, btn) ->
            btn.setOnClickListener { selectActivityBarItem(item) }
        }

        explorerBtn.setOnLongClickListener {
            showToast(app.i18nManager.getString("activity_bar_explorer"))
            true
        }
        searchBtn.setOnLongClickListener {
            showToast(app.i18nManager.getString("activity_bar_search"))
            true
        }
        sourceControlBtn.setOnLongClickListener {
            showToast(app.i18nManager.getString("activity_bar_source_control"))
            true
        }
        extensionsBtn.setOnLongClickListener {
            showToast(app.i18nManager.getString("activity_bar_extensions"))
            true
        }
        debugBtn.setOnLongClickListener {
            showToast(app.i18nManager.getString("activity_bar_debug"))
            true
        }

        settingsBtn.setOnClickListener {
            showSettingsDialog()
        }

        selectActivityBarItem(ActivityBarItem.EXPLORER)
    }

    private fun selectActivityBarItem(item: ActivityBarItem) {
        currentActivityBarSelection = item

        val activeColor = ContextCompat.getColor(this, com.vscode.android.R.color.activity_bar_active_foreground)
        val inactiveColor = ContextCompat.getColor(this, com.vscode.android.R.color.activity_bar_inactive_foreground)

        binding.activityBarExplorer.imageTintList = android.content.res.ColorStateList.valueOf(
            if (item == ActivityBarItem.EXPLORER) activeColor else inactiveColor
        )
        binding.activityBarSearch.imageTintList = android.content.res.ColorStateList.valueOf(
            if (item == ActivityBarItem.SEARCH) activeColor else inactiveColor
        )
        binding.activityBarSourceControl.imageTintList = android.content.res.ColorStateList.valueOf(
            if (item == ActivityBarItem.SOURCE_CONTROL) activeColor else inactiveColor
        )
        binding.activityBarExtensions.imageTintList = android.content.res.ColorStateList.valueOf(
            if (item == ActivityBarItem.EXTENSIONS) activeColor else inactiveColor
        )
        binding.activityBarDebug.imageTintList = android.content.res.ColorStateList.valueOf(
            if (item == ActivityBarItem.DEBUG) activeColor else inactiveColor
        )

        updateSidebarForItem(item)

        if (item != ActivityBarItem.EXPLORER && !isSidebarVisible) {
            toggleSidebar(show = true)
        }
    }

    private fun updateSidebarForItem(item: ActivityBarItem) {
        val titleText = when (item) {
            ActivityBarItem.EXPLORER -> app.i18nManager.getString("activity_bar_explorer")
            ActivityBarItem.SEARCH -> app.i18nManager.getString("activity_bar_search")
            ActivityBarItem.SOURCE_CONTROL -> app.i18nManager.getString("activity_bar_source_control")
            ActivityBarItem.EXTENSIONS -> app.i18nManager.getString("activity_bar_extensions")
            ActivityBarItem.DEBUG -> app.i18nManager.getString("activity_bar_debug")
        }
        binding.sidebarTitle.text = titleText.uppercase()

        binding.sidebarContent.removeAllViews()
        when (item) {
            ActivityBarItem.EXPLORER -> showExplorerSidebar()
            ActivityBarItem.SEARCH -> showSearchSidebar()
            ActivityBarItem.SOURCE_CONTROL -> showSourceControlSidebar()
            ActivityBarItem.EXTENSIONS -> showExtensionsSidebar()
            ActivityBarItem.DEBUG -> showDebugSidebar()
        }
    }

    private fun showExplorerSidebar() {
        val content = binding.sidebarContent
        val projects = projectManager.getOpenProjects()
        if (projects.isEmpty()) {
            val placeholder = TextView(this).apply {
                text = app.i18nManager.getString("sidebar_no_folder_open")
                setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
                textSize = 13f
                setPadding(32, 32, 32, 0)
            }
            val openBtn = TextView(this).apply {
                text = app.i18nManager.getString("sidebar_open_folder_button")
                setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_link))
                textSize = 13f
                setPadding(32, 16, 32, 0)
                setOnClickListener { openFolder() }
            }
            content.addView(placeholder)
            content.addView(openBtn)
        } else {
            val scrollView = android.widget.ScrollView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val fileTreeContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            for (project in projects) {
                val projectHeader = buildFileTreeItem(
                    project.name,
                    project.rootPath,
                    isFolder = true,
                    depth = 0,
                    isExpanded = true
                )
                fileTreeContainer.addView(projectHeader)
            }
            scrollView.addView(fileTreeContainer)
            content.addView(scrollView)
        }
    }

    private fun buildFileTreeItem(
        name: String,
        path: String,
        isFolder: Boolean,
        depth: Int,
        isExpanded: Boolean = false
    ): View {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8 + depth * 16, 4, 8, 4)
            setOnClickListener {
                if (isFolder) {
                    val childrenContainer = getChildAt(1) as? ViewGroup
                    childrenContainer?.let {
                        it.visibility = if (it.visibility == View.GONE) View.VISIBLE else View.GONE
                    }
                } else {
                    openFileInEditor(File(path))
                }
            }
        }

        val icon = TextView(this).apply {
            text = if (isFolder) "\uD83D\uDCC1" else getFileIcon(name)
            textSize = 14f
            setPadding(0, 0, 8, 0)
        }
        val label = TextView(this).apply {
            text = name
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_primary))
            textSize = 13f
        }

        headerRow.addView(icon)
        headerRow.addView(label)
        itemLayout.addView(headerRow)

        if (isFolder && isExpanded) {
            val childrenContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            try {
                val dir = File(path)
                val children = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
                children?.forEach { child ->
                    val childView = buildFileTreeItem(
                        child.name,
                        child.absolutePath,
                        child.isDirectory,
                        depth + 1
                    )
                    childrenContainer.addView(childView)
                }
            } catch (_: Exception) {}
            itemLayout.addView(childrenContainer)
        }

        return itemLayout
    }

    private fun getFileIcon(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt" -> "\uD83D\uDFE2"
            "java" -> "\uD83D\uDFE0"
            "py" -> "\uD83D\uDFE1"
            "js" -> "\uD83D\uDFE8"
            "ts" -> "\uD83D\uDFE6"
            "html" -> "\uD83D\uDFE7"
            "css" -> "\uD83D\uDFE6"
            "json" -> "\uD83D\uDCCB"
            "xml" -> "\uD83D\uDCC4"
            "md" -> "\uD83D\uDCDD"
            "cpp", "c", "h", "hpp" -> "\uD83D\uDFE9"
            "gradle" -> "\uD83D\uDEE0\uFE0F"
            "png", "jpg", "jpeg", "gif" -> "\uD83D\uDDBC\uFE0F"
            "yml", "yaml" -> "\u2699\uFE0F"
            "sh" -> "\uD83D\uDCBB"
            "gitignore" -> "\uD83D\uDCE6"
            else -> "\uD83D\uDCC4"
        }
    }

    private fun showSearchSidebar() {
        val content = binding.sidebarContent
        val searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }
        val searchInput = android.widget.EditText(this).apply {
            hint = app.i18nManager.getString("search_find")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.input_background))
            textSize = 13f
            setPadding(12, 8, 12, 8)
        }
        val replaceInput = android.widget.EditText(this).apply {
            hint = app.i18nManager.getString("search_replace")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.input_background))
            textSize = 13f
            setPadding(12, 8, 12, 8)
            visibility = View.GONE
        }
        val optionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        val matchCaseCb = android.widget.CheckBox(this).apply {
            text = app.i18nManager.getString("search_match_case")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 11f
        }
        val wholeWordCb = android.widget.CheckBox(this).apply {
            text = app.i18nManager.getString("search_whole_word")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 11f
        }
        optionsRow.addView(matchCaseCb)
        optionsRow.addView(wholeWordCb)

        val resultsLabel = TextView(this).apply {
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 12f
            setPadding(0, 12, 0, 0)
            visibility = View.GONE
        }

        searchLayout.addView(searchInput)
        searchLayout.addView(replaceInput)
        searchLayout.addView(optionsRow)
        searchLayout.addView(resultsLabel)
        content.addView(searchLayout)
    }

    private fun showSourceControlSidebar() {
        val content = binding.sidebarContent
        val placeholder = TextView(this).apply {
            text = app.i18nManager.getString("scm_no_changes")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 13f
            setPadding(32, 32, 32, 0)
        }
        content.addView(placeholder)
    }

    private fun showExtensionsSidebar() {
        val content = binding.sidebarContent
        val searchInput = android.widget.EditText(this).apply {
            hint = app.i18nManager.getString("extensions_search")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.input_background))
            textSize = 13f
            setPadding(12, 8, 12, 8)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(12, 12, 12, 0) }
        }
        content.addView(searchInput)

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(0, 48, 0, 0) }
        }
        val extList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val extensions = app.pluginManager.getAllPlugins()
        for (ext in extensions) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 8, 16, 8)
            }
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameView = TextView(this).apply {
                text = ext.name
                setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_primary))
                textSize = 13f
            }
            val descView = TextView(this).apply {
                text = ext.description
                setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
                textSize = 11f
            }
            info.addView(nameView)
            info.addView(descView)
            val toggleBtn = TextView(this).apply {
                text = if (app.pluginManager.isPluginEnabled(ext.id))
                    app.i18nManager.getString("extensions_disable")
                else app.i18nManager.getString("extensions_enable")
                setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.accent_blue))
                textSize = 12f
                setOnClickListener {
                    if (app.pluginManager.isPluginEnabled(ext.id)) {
                        app.pluginManager.disablePlugin(ext.id)
                        text = app.i18nManager.getString("extensions_enable")
                    } else {
                        app.pluginManager.enablePlugin(ext.id)
                        text = app.i18nManager.getString("extensions_disable")
                    }
                }
            }
            row.addView(info)
            row.addView(toggleBtn)
            extList.addView(row)
        }
        scrollView.addView(extList)
        content.addView(scrollView)
    }

    private fun showDebugSidebar() {
        val content = binding.sidebarContent
        val placeholder = TextView(this).apply {
            text = app.i18nManager.getString("debug_no_configuration")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 13f
            setPadding(32, 32, 32, 0)
        }
        val createBtn = TextView(this).apply {
            text = app.i18nManager.getString("debug_create_launch_json")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_link))
            textSize = 13f
            setPadding(32, 16, 32, 0)
        }
        content.addView(placeholder)
        content.addView(createBtn)
    }

    private fun setupSidebar() {
        binding.sidebarCollapseButton.setOnClickListener {
            toggleSidebar()
        }
    }

    private fun toggleSidebar(show: Boolean? = null) {
        isSidebarVisible = show ?: !isSidebarVisible
        binding.sidebarPanel.visibility = if (isSidebarVisible) View.VISIBLE else View.GONE
        binding.sidebarResizeHandle.visibility = if (isSidebarVisible) View.VISIBLE else View.GONE
    }

    private fun setupTabBar() {
        binding.tabMore.setOnClickListener {
            showTabContextMenu()
        }
    }

    private fun showTabContextMenu() {
        val tabs = editorEngine.getOpenTabs()
        val tabNames = tabs.map { it.title }.toTypedArray()
        if (tabNames.isEmpty()) return

        AlertDialog.Builder(this)
            .setItems(tabNames) { _, which ->
                if (which < tabs.size) {
                    editorEngine.setActiveTab(tabs[which].id)
                    refreshTabBar()
                }
            }
            .show()
    }

    private fun refreshTabBar() {
        val tabs = editorEngine.getOpenTabs()
        val activeTab = editorEngine.getActiveTab()
        val container = binding.tabContainer
        container.removeAllViews()

        for (tab in tabs) {
            val tabView = createTabView(tab, tab.id == activeTab?.id)
            container.addView(tabView)
        }

        if (activeTab != null) {
            binding.tabScroll.post {
                val index = tabs.indexOfFirst { it.id == activeTab.id }
                if (index >= 0) {
                    val child = container.getChildAt(index)
                    child?.let {
                        val scrollX = it.left - (binding.tabScroll.width - it.width) / 2
                        binding.tabScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
                    }
                }
            }
        }
    }

    private fun createTabView(tab: EditorTab, isActive: Boolean): View {
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 4, 0)
            minimumWidth = 120
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isActive) com.vscode.android.R.color.tab_active_background
                    else com.vscode.android.R.color.tab_inactive_background
                )
            )
            setOnClickListener {
                editorEngine.setActiveTab(tab.id)
                refreshTabBar()
                updateEditorContent()
            }
        }

        val titleText = TextView(this).apply {
            text = tab.title + if (tab.isModified) " \u25CF" else ""
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isActive) com.vscode.android.R.color.tab_active_foreground
                    else com.vscode.android.R.color.tab_inactive_foreground
                )
            )
            textSize = 13f
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = 200
        }

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@MainActivity,
                    com.vscode.android.R.color.activity_bar_inactive_foreground
                )
            )
            layoutParams = LinearLayout.LayoutParams(24, 24)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(4, 4, 4, 4)
            setOnClickListener {
                editorEngine.closeTab(tab.id)
                refreshTabBar()
                updateEditorContent()
            }
        }

        tabLayout.addView(titleText)
        tabLayout.addView(closeBtn)
        return tabLayout
    }

    private fun updateEditorContent() {
        val activeTab = editorEngine.getActiveTab()
        val content = binding.editorContent
        content.removeAllViews()

        if (activeTab != null) {
            val editorView = createEditorView(activeTab)
            content.addView(editorView)
            updateStatusBarForFile(activeTab)
        } else {
            val welcomeView = createWelcomeView()
            content.addView(welcomeView)
            resetStatusBar()
        }
    }

    private fun createEditorView(tab: EditorTab): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val lineNumberGutter = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                48.dpToPx(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.editor_gutter_background))
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.editor_line_number))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.END
            setPadding(0, 4, 8, 4)
            setHorizontallyScrolling(false)
            tag = "line_numbers"
        }

        val editorEditText = android.widget.EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.editor_background))
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.editor_foreground))
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setPadding(8, 4, 8, 4)
            setHorizontallyScrolling(true)
            isVerticalScrollBarEnabled = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION
            setText(tab.content)
            setSelection(tab.cursorPosition)

            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val content = s?.toString() ?: ""
                    if (content != tab.content) {
                        editorEngine.updateTabContent(tab.id, content)
                        refreshTabBar()
                    }
                    updateLineNumbers(lineNumberGutter, content)
                    updateCursorPosition()
                }
            })

            setOnKeyListener { _, _, _ ->
                updateCursorPosition()
                false
            }

            setOnTouchListener { _, _ ->
                updateCursorPosition()
                false
            }
        }

        val editorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        editorRow.addView(lineNumberGutter)
        editorRow.addView(editorEditText)

        container.addView(editorRow)

        val lineNumbers = tab.content.lines().size
        lineNumberGutter.text = buildLineNumbers(lineNumbers)

        container.post {
            editorEditText.requestFocus()
            editorEditText.setSelection(tab.cursorPosition)
        }

        container.tag = "editor_container"
        return container
    }

    private fun updateLineNumbers(gutter: TextView, content: String) {
        val lineCount = content.lines().size
        val currentText = gutter.text.toString()
        val currentLineCount = if (currentText.isBlank()) 0 else currentText.trim().lines().size
        if (lineCount != currentLineCount) {
            gutter.text = buildLineNumbers(lineCount)
        }
    }

    private fun buildLineNumbers(count: Int): String {
        val sb = StringBuilder()
        for (i in 1..count) {
            if (i > 1) sb.append("\n")
            sb.append(i.toString())
        }
        return sb.toString()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun createWelcomeView(): View {
        return TextView(this).apply {
            text = app.i18nManager.getString("editor_welcome")
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun setupPanel() {
        binding.panelTabProblems.setOnClickListener { selectPanelTab(PanelTab.PROBLEMS) }
        binding.panelTabOutput.setOnClickListener { selectPanelTab(PanelTab.OUTPUT) }
        binding.panelTabTerminal.setOnClickListener { selectPanelTab(PanelTab.TERMINAL) }
        binding.panelTabDebugConsole.setOnClickListener { selectPanelTab(PanelTab.DEBUG_CONSOLE) }
        binding.panelCloseButton.setOnClickListener { togglePanel() }

        selectPanelTab(PanelTab.TERMINAL)
    }

    private fun selectPanelTab(tab: PanelTab) {
        currentPanelTab = tab

        val activeColor = ContextCompat.getColor(this, com.vscode.android.R.color.panel_title_active_foreground)
        val inactiveColor = ContextCompat.getColor(this, com.vscode.android.R.color.panel_title_inactive_foreground)

        binding.panelTabProblems.setTextColor(if (tab == PanelTab.PROBLEMS) activeColor else inactiveColor)
        binding.panelTabOutput.setTextColor(if (tab == PanelTab.OUTPUT) activeColor else inactiveColor)
        binding.panelTabTerminal.setTextColor(if (tab == PanelTab.TERMINAL) activeColor else inactiveColor)
        binding.panelTabDebugConsole.setTextColor(if (tab == PanelTab.DEBUG_CONSOLE) activeColor else inactiveColor)

        updatePanelContent(tab)
    }

    private fun updatePanelContent(tab: PanelTab) {
        val content = binding.panelContent
        content.removeAllViews()

        when (tab) {
            PanelTab.PROBLEMS -> showProblemsPanel(content)
            PanelTab.OUTPUT -> showOutputPanel(content)
            PanelTab.TERMINAL -> showTerminalPanel(content)
            PanelTab.DEBUG_CONSOLE -> showDebugConsolePanel(content)
        }
    }

    private fun showProblemsPanel(container: FrameLayout) {
        val placeholder = TextView(this).apply {
            text = "No problems detected in workspace."
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 13f
            setPadding(16, 16, 16, 16)
        }
        container.addView(placeholder)
    }

    private fun showOutputPanel(container: FrameLayout) {
        val outputText = TextView(this).apply {
            text = "Output panel"
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.terminal_foreground))
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.terminal_background))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(12, 12, 12, 12)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(outputText)
    }

    private fun showTerminalPanel(container: FrameLayout) {
        val terminal = app.terminalManager.getActiveTerminal()
        if (terminal == null) {
            app.terminalManager.createTerminal("Terminal")
        }

        val terminalLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val terminalHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.tab_background))
            setPadding(4, 0, 4, 0)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                28.dpToPx()
            )
        }

        val terminalSelect = TextView(this).apply {
            text = "Terminal ▼"
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_primary))
            textSize = 12f
            setPadding(8, 0, 8, 0)
        }
        val newTerminalBtn = TextView(this).apply {
            text = "+"
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_primary))
            textSize = 16f
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                app.terminalManager.createTerminal("Terminal ${app.terminalManager.getAllTerminals().size + 1}")
                updatePanelContent(PanelTab.TERMINAL)
            }
        }
        val killTerminalBtn = TextView(this).apply {
            text = "\u2715"
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.accent_red))
            textSize = 14f
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                val active = app.terminalManager.getActiveTerminal()
                if (active != null) {
                    app.terminalManager.killTerminal(active.id)
                    updatePanelContent(PanelTab.TERMINAL)
                }
            }
        }
        terminalHeader.addView(terminalSelect)
        terminalHeader.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        terminalHeader.addView(newTerminalBtn)
        terminalHeader.addView(killTerminalBtn)

        val terminalInput = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.terminal_background))
        }

        val promptLabel = TextView(this).apply {
            text = "$ "
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.accent_green))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(8, 4, 0, 4)
        }
        val commandInput = android.widget.EditText(this).apply {
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.terminal_foreground))
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.terminal_background))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(4, 4, 8, 4)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    val command = text.toString()
                    if (command.isNotBlank()) {
                        executeTerminalCommand(command)
                        setText("")
                    }
                    true
                } else false
            }
        }
        terminalInput.addView(promptLabel)
        terminalInput.addView(commandInput)

        val terminalOutput = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(ContextCompat.getColor(context, com.vscode.android.R.color.terminal_background))
        }
        val outputText = TextView(this).apply {
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.terminal_foreground))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(12, 4, 12, 4)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val active = app.terminalManager.getActiveTerminal()
            if (active != null) {
                text = active.getOutput()
            }
            tag = "terminal_output"
            setHorizontallyScrolling(true)
        }
        terminalOutput.addView(outputText)
        terminalOutput.setOnTouchListener { _, _ ->
            commandInput.clearFocus()
            false
        }

        terminalLayout.addView(terminalHeader)
        terminalLayout.addView(terminalOutput)
        terminalLayout.addView(terminalInput)
        container.addView(terminalLayout)
    }

    private fun executeTerminalCommand(command: String) {
        val terminal = app.terminalManager.getActiveTerminal() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = terminal.executeCommand(command)
            withContext(Dispatchers.Main) {
                val outputText = binding.panelContent.findViewWithTag<TextView>("terminal_output")
                outputText?.let {
                    it.text = terminal.getOutput()
                    val scrollView = it.parent as? android.widget.ScrollView
                    scrollView?.post {
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    private fun showDebugConsolePanel(container: FrameLayout) {
        val placeholder = TextView(this).apply {
            text = "Debug console"
            setTextColor(ContextCompat.getColor(context, com.vscode.android.R.color.text_secondary))
            textSize = 13f
            setPadding(16, 16, 16, 16)
        }
        container.addView(placeholder)
    }

    private fun togglePanel() {
        isPanelVisible = !isPanelVisible
        binding.panelArea.visibility = if (isPanelVisible) View.VISIBLE else View.GONE
    }

    private fun setupStatusBar() {
        resetStatusBar()

        binding.statusBarFeedback.setOnClickListener {
            Toast.makeText(this, "Feedback", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusBarForFile(tab: EditorTab) {
        val ext = tab.filePath?.substringAfterLast('.', "")?.lowercase() ?: ""
        val language = getLanguageName(ext)
        binding.statusBarLanguage.text = language
        binding.statusBarEncoding.text = tab.encoding ?: "UTF-8"
        updateCursorPosition()
    }

    private fun resetStatusBar() {
        binding.statusBarLanguage.text = app.i18nManager.getString("status_bar_language")
        binding.statusBarEncoding.text = app.i18nManager.getString("status_bar_encoding")
        binding.statusBarPosition.text = "Ln 1, Col 1"
        binding.statusBarIndent.text = "Spaces: 4"
        binding.statusBarLineEndings.text = "LF"
    }

    private fun updateCursorPosition() {
        val container = binding.editorContent.findViewWithTag<View>("editor_container") as? LinearLayout ?: return
        val editText = container.getChildAt(0)?.let { row ->
            (row as? LinearLayout)?.getChildAt(1) as? android.widget.EditText
        } ?: return

        val layout = editText.layout ?: return
        val selStart = editText.selectionStart
        val line = layout.getLineForOffset(selStart)
        val col = selStart - layout.getLineStart(line)
        binding.statusBarPosition.text = "Ln ${line + 1}, Col ${col + 1}"
    }

    private fun getLanguageName(ext: String): String {
        return when (ext) {
            "kt" -> "Kotlin"
            "java" -> "Java"
            "py" -> "Python"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "html" -> "HTML"
            "css" -> "CSS"
            "json" -> "JSON"
            "xml" -> "XML"
            "md" -> "Markdown"
            "cpp", "c", "h" -> "C/C++"
            "gradle" -> "Gradle"
            "sh" -> "Shell Script"
            "yml", "yaml" -> "YAML"
            else -> "Plain Text"
        }
    }

    private fun setupEditorListeners() {
        editorEngine.addListener(object : EditorEngine.EditorListener {
            override fun onTabOpened(tab: EditorTab) {
                runOnUiThread {
                    refreshTabBar()
                    updateEditorContent()
                }
            }

            override fun onTabClosed(tab: EditorTab) {
                runOnUiThread {
                    refreshTabBar()
                    updateEditorContent()
                }
            }

            override fun onTabActivated(tab: EditorTab) {
                runOnUiThread {
                    refreshTabBar()
                    updateEditorContent()
                }
            }

            override fun onTabModified(tab: EditorTab) {
                runOnUiThread {
                    refreshTabBar()
                }
            }

            override fun onTabSaved(tab: EditorTab) {
                runOnUiThread {
                    refreshTabBar()
                    updateStatusBarForFile(tab)
                }
            }
        })
    }

    private fun openFileInEditor(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = file.readText()
                withContext(Dispatchers.Main) {
                    editorEngine.openFile(file.absolutePath, content)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to open file: ${e.message}")
                }
            }
        }
    }

    private fun openFolder() {
        try {
            openDocumentTreeLauncher.launch(null)
        } catch (e: Exception) {
            showToast("Failed to open folder picker: ${e.message}")
        }
    }

    private fun handleFolderOpen(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val path = getPathFromUri(uri) ?: uri.toString()
                projectManager.openProject(path)
                withContext(Dispatchers.Main) {
                    updateSidebarForItem(currentActivityBarSelection)
                    showToast("Folder opened: $path")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to open folder: ${e.message}")
                }
            }
        }
    }

    private fun handleFileOpen(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = readTextFromUri(uri)
                val fileName = getFileNameFromUri(uri) ?: "Untitled"
                withContext(Dispatchers.Main) {
                    editorEngine.openFile(fileName, content)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to open file: ${e.message}")
                }
            }
        }
    }

    private fun handleFileCreate(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write("".toByteArray())
                }
                withContext(Dispatchers.Main) {
                    editorEngine.openFile("New File", "")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to create file: ${e.message}")
                }
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        return if (parts.size >= 2) {
            val type = parts[0]
            val relativePath = parts[1]
            when {
                type.equals("primary", ignoreCase = true) ->
                    "/storage/emulated/0/$relativePath"
                type.equals("home", ignoreCase = true) ->
                    "/data/data/com.termux/files/home/$relativePath"
                else -> "/storage/$type/$relativePath"
            }
        } else {
            null
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else null
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage("Android VSCode needs access to all files to manage your projects. Please grant Manage All Files permission.")
                    .setPositiveButton("Grant") { _, _ ->
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val notGranted = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGranted.isNotEmpty()) {
                requestPermissions(notGranted.toTypedArray(), REQUEST_STORAGE_PERMISSION)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                showToast("Storage permissions are required for full functionality.")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { handleFileOpen(it) }
            }
            Intent.ACTION_EDIT -> {
                intent.data?.let { handleFileOpen(it) }
            }
        }
    }

    override fun onBackPressed() {
        when {
            isPanelVisible -> {
                togglePanel()
            }
            !isSidebarVisible -> {
                toggleSidebar(show = true)
            }
            else -> {
                val unsavedTabs = editorEngine.getUnsavedTabs()
                if (unsavedTabs.isNotEmpty()) {
                    showSaveChangesDialog(unsavedTabs) {
                        super.onBackPressed()
                    }
                } else {
                    super.onBackPressed()
                }
            }
        }
    }

    private fun showSaveChangesDialog(tabs: List<EditorTab>, onDismiss: () -> Unit) {
        val names = tabs.joinToString(", ") { it.title }
        AlertDialog.Builder(this)
            .setTitle(app.i18nManager.getString("dialog_save_changes_title"))
            .setMessage(app.i18nManager.getString("dialog_save_changes_message", names))
            .setPositiveButton(app.i18nManager.getString("dialog_save")) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    tabs.forEach { tab ->
                        editorEngine.saveTab(tab.id)
                    }
                    withContext(Dispatchers.Main) {
                        onDismiss()
                    }
                }
            }
            .setNegativeButton(app.i18nManager.getString("dialog_dont_save")) { _, _ ->
                onDismiss()
            }
            .setNeutralButton(app.i18nManager.getString("dialog_cancel"), null)
            .show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            app.i18nManager.getString("menu_settings"),
            app.i18nManager.getString("menu_command_palette")
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showFullSettingsDialog()
                    1 -> showCommandPalette()
                }
            }
            .show()
    }

    private fun showFullSettingsDialog() {
        val languages = app.i18nManager.getAvailableLanguages()
        val currentLang = app.i18nManager.getLanguage()
        val langNames = languages.map { it.second }.toTypedArray()
        val currentLangIndex = languages.indexOfFirst { it.first == currentLang }

        AlertDialog.Builder(this)
            .setTitle(app.i18nManager.getString("menu_settings"))
            .setSingleChoiceItems(langNames, currentLangIndex) { dialog, which ->
                val lang = languages[which].first
                app.setLanguage(lang)
                recreate()
                dialog.dismiss()
            }
            .setPositiveButton(app.i18nManager.getString("dialog_close"), null)
            .show()
    }

    private fun showCommandPalette() {
        val commands = listOf(
            "> " + app.i18nManager.getString("menu_new_file"),
            "> " + app.i18nManager.getString("menu_open_file"),
            "> " + app.i18nManager.getString("menu_open_folder"),
            "> " + app.i18nManager.getString("menu_save"),
            "> " + app.i18nManager.getString("menu_save_all"),
            "> " + app.i18nManager.getString("menu_find"),
            "> " + app.i18nManager.getString("toggle_panel"),
            "> " + app.i18nManager.getString("toggle_sidebar"),
            "> " + app.i18nManager.getString("terminal_new"),
            "> " + app.i18nManager.getString("menu_settings")
        )
        AlertDialog.Builder(this)
            .setTitle(app.i18nManager.getString("menu_command_palette"))
            .setItems(commands.toTypedArray()) { _, which ->
                executeCommand(which)
            }
            .setNegativeButton(app.i18nManager.getString("dialog_cancel"), null)
            .show()
    }

    private fun executeCommand(index: Int) {
        when (index) {
            0 -> lifecycleScope.launch { editorEngine.openFile("Untitled", "") }
            1 -> openDocumentLauncher.launch(arrayOf("*/*"))
            2 -> openFolder()
            3 -> {
                val active = editorEngine.getActiveTab()
                if (active != null) lifecycleScope.launch(Dispatchers.IO) { editorEngine.saveTab(active.id) }
            }
            4 -> lifecycleScope.launch(Dispatchers.IO) { editorEngine.saveAllTabs() }
            5 -> selectActivityBarItem(ActivityBarItem.SEARCH)
            6 -> togglePanel()
            7 -> toggleSidebar()
            8 -> {
                app.terminalManager.createTerminal("Terminal ${app.terminalManager.getAllTerminals().size + 1}")
                selectPanelTab(PanelTab.TERMINAL)
            }
            9 -> showFullSettingsDialog()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        editorEngine.removeAllListeners()
    }

    private enum class ActivityBarItem {
        EXPLORER, SEARCH, SOURCE_CONTROL, EXTENSIONS, DEBUG
    }

    private enum class PanelTab {
        PROBLEMS, OUTPUT, TERMINAL, DEBUG_CONSOLE
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1001
    }
}