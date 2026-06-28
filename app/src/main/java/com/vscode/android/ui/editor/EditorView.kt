package com.vscode.android.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import com.vscode.android.VSCodeApp
import com.vscode.android.core.editor.Suggestion
import com.vscode.android.core.editor.SyntaxToken
import java.io.File

class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val codeEditor: CodeEditor
    private val lineNumberView: LineNumberView
    private val editorScrollView: ScrollView
    private val horizontalScrollView: HorizontalScrollView
    private val editorContentContainer: LinearLayout
    private val mainContainer: LinearLayout

    private var suggestionPopup: PopupWindow? = null
    private var suggestionListView: android.widget.ListView? = null

    private var currentFilePath: String? = null
    private var currentLanguage: String = "plaintext"
    private var currentEncoding: String = "UTF-8"
    private var isModified: Boolean = false
    private var originalContent: String = ""

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var syntaxHighlightRunnable: Runnable? = null
    private var suggestionRunnable: Runnable? = null

    private var onFileModifiedListener: ((Boolean) -> Unit)? = null
    private var onCursorPositionChangedListener: ((Int, Int) -> Unit)? = null
    private var onFileOpenRequestListener: ((String) -> Unit)? = null
    private var onEditorSaveListener: ((String, String) -> Unit)? = null

    @ColorInt
    private var editorBackgroundColor: Int = 0xFF1E1E1E.toInt()

    @ColorInt
    private var editorForegroundColor: Int = 0xFFD4D4D4.toInt()

    @ColorInt
    private var gutterBackgroundColor: Int = 0xFF1E1E1E.toInt()

    @ColorInt
    private var lineNumberColor: Int = 0xFF858585.toInt()

    @ColorInt
    private var lineNumberActiveColor: Int = 0xFFC6C6C6.toInt()

    @ColorInt
    private var lineHighlightColor: Int = 0xFF2A2D2E.toInt()

    init {
        codeEditor = CodeEditor(context)
        lineNumberView = LineNumberView(context)

        editorContentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        lineNumberView.layoutParams = LinearLayout.LayoutParams(
            dpToPx(GUTTER_WIDTH_DP),
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        codeEditor.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            weight = 0f
        }

        editorContentContainer.addView(lineNumberView)
        editorContentContainer.addView(codeEditor)

        horizontalScrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isHorizontalScrollBarEnabled = true
            addView(editorContentContainer)
        }

        editorScrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = true
            addView(horizontalScrollView)
        }

        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        mainContainer.addView(editorScrollView)

        addView(mainContainer)

        setupEditorListeners()
        applyDefaultColors()
        updateLineNumbers()
        refreshSyntaxHighlighting()
    }

    private fun setupEditorListeners() {
        codeEditor.setOnTextChangeDebouncedListener { text ->
            checkModifiedState(text)
            updateLineNumbers()
            refreshSyntaxHighlighting()
            showSuggestions()
        }

        codeEditor.setOnSelectionChangeListener { selStart, selEnd ->
            updateCurrentLineHighlight()
            onCursorPositionChangedListener?.invoke(
                codeEditor.getCurrentLineNumber(),
                codeEditor.getCurrentColumn()
            )
        }

        editorScrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = editorScrollView.scrollY
            lineNumberView.syncWithEditorScroll(scrollY)
        }
    }

    private fun checkModifiedState(text: String) {
        val modified = text != originalContent
        if (modified != isModified) {
            isModified = modified
            onFileModifiedListener?.invoke(modified)
        }
    }

    private fun updateLineNumbers() {
        val lineCount = codeEditor.getLineCount()
        lineNumberView.setLineCount(lineCount)
    }

    private fun updateCurrentLineHighlight() {
        val currentLine = codeEditor.getCurrentLineNumber()
        lineNumberView.setCurrentLine(currentLine)
    }

    private fun refreshSyntaxHighlighting() {
        syntaxHighlightRunnable?.let { debounceHandler.removeCallbacks(it) }
        syntaxHighlightRunnable = Runnable {
            val text = codeEditor.text?.toString() ?: return@Runnable
            if (text.isEmpty()) return@Runnable

            try {
                val app = VSCodeApp.getInstance()
                val tokens = app.editorEngine.highlightSyntax("")
                if (tokens.isNotEmpty()) {
                    applySyntaxHighlighting(text, tokens)
                }
            } catch (_: Exception) {
                // Fallback: use local highlighter instance
            }
        }
        debounceHandler.postDelayed(syntaxHighlightRunnable!!, SYNTAX_HIGHLIGHT_DELAY_MS)
    }

    private fun applySyntaxHighlighting(text: String, tokens: List<SyntaxToken>) {
        try {
            val spannable = SpannableStringBuilder(text)
            for (token in tokens) {
                if (token.start < spannable.length && token.end <= spannable.length) {
                    val color = getColorForTokenType(token.type)
                    spannable.setSpan(
                        ForegroundColorSpan(color),
                        token.start,
                        token.end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            codeEditor.setEditorText(text)
            codeEditor.setSelection(codeEditor.selectionStart)
        } catch (_: Exception) {
            // Ignore span application errors
        }
    }

    private fun getColorForTokenType(type: com.vscode.android.VSCodeApp.SyntaxTokenType): Int {
        return when (type) {
            com.vscode.android.VSCodeApp.SyntaxTokenType.KEYWORD -> KEYWORD_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.STRING -> STRING_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.NUMBER -> NUMBER_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.COMMENT -> COMMENT_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.TYPE -> TYPE_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.FUNCTION -> FUNCTION_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.VARIABLE -> VARIABLE_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.OPERATOR -> OPERATOR_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.CLASS_NAME -> TYPE_COLOR
            com.vscode.android.VSCodeApp.SyntaxTokenType.PLAIN -> editorForegroundColor
            else -> editorForegroundColor
        }
    }

    private fun showSuggestions() {
        suggestionRunnable?.let { debounceHandler.removeCallbacks(it) }
        suggestionRunnable = Runnable {
            try {
                val app = VSCodeApp.getInstance()
                val activeTab = app.editorEngine.getActiveTab()
                if (activeTab != null) {
                    val suggestions = app.editorEngine.getSuggestions(activeTab.id, codeEditor.selectionStart)
                    if (suggestions.isNotEmpty()) {
                        showSuggestionPopup(suggestions)
                    }
                }
            } catch (_: Exception) {
                // Ignore suggestion errors
            }
        }
        debounceHandler.postDelayed(suggestionRunnable!!, SUGGESTION_DELAY_MS)
    }

    private fun showSuggestionPopup(suggestions: List<Suggestion>) {
        dismissSuggestionPopup()

        val listView = android.widget.ListView(context).apply {
            setBackgroundColor(0xFF252526.toInt())
            adapter = SuggestionAdapter(context, suggestions)
            setOnItemClickListener { _, _, position, _ ->
                if (position < suggestions.size) {
                    val suggestion = suggestions[position]
                    insertSuggestion(suggestion)
                    dismissSuggestionPopup()
                }
            }
        }
        suggestionListView = listView

        suggestionPopup = PopupWindow(
            listView,
            dpToPx(300),
            dpToPx(200),
            true
        ).apply {
            isOutsideTouchable = true
            setOnDismissListener {
                suggestionListView = null
                suggestionPopup = null
            }
        }

        suggestionPopup?.showAsDropDown(codeEditor, 0, -dpToPx(200))
    }

    private fun dismissSuggestionPopup() {
        suggestionPopup?.dismiss()
        suggestionPopup = null
        suggestionListView = null
    }

    private fun insertSuggestion(suggestion: Suggestion) {
        val text = codeEditor.text ?: return
        val selStart = codeEditor.selectionStart
        val prefix = getCurrentWordPrefix()
        val insertStart = selStart - prefix.length
        if (insertStart >= 0 && insertStart <= text.length) {
            text.replace(insertStart, selStart, suggestion.insertText)
            codeEditor.setSelection(insertStart + suggestion.insertText.length)
        }
    }

    private fun getCurrentWordPrefix(): String {
        val text = codeEditor.text?.toString() ?: return ""
        val selStart = codeEditor.selectionStart
        var start = selStart - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) {
            start--
        }
        return text.substring(start + 1, selStart)
    }

    fun openFile(filePath: String, content: String, language: String? = null, encoding: String? = null) {
        currentFilePath = filePath
        currentLanguage = language ?: detectLanguage(filePath)
        currentEncoding = encoding ?: detectEncoding()
        originalContent = content
        isModified = false
        codeEditor.setEditorText(content)
        codeEditor.setSelection(0)
        updateLineNumbers()
        refreshSyntaxHighlighting()
    }

    fun saveFile(): Boolean {
        val filePath = currentFilePath ?: return false
        val content = codeEditor.text?.toString() ?: return false
        onEditorSaveListener?.invoke(filePath, content)
        originalContent = content
        isModified = false
        onFileModifiedListener?.invoke(false)
        return true
    }

    fun closeFile() {
        currentFilePath = null
        currentLanguage = "plaintext"
        currentEncoding = "UTF-8"
        originalContent = ""
        isModified = false
        codeEditor.setEditorText("")
        lineNumberView.setLineCount(1)
    }

    fun reloadFile() {
        val filePath = currentFilePath ?: return
        try {
            val file = File(filePath)
            if (file.exists()) {
                val content = file.readText()
                openFile(filePath, content, currentLanguage, currentEncoding)
            }
        } catch (_: Exception) {
            // Ignore reload errors
        }
    }

    fun getCurrentFilePath(): String? = currentFilePath

    fun getCurrentLanguage(): String = currentLanguage

    fun getCurrentEncoding(): String = currentEncoding

    fun isModified(): Boolean = isModified

    fun getContent(): String = codeEditor.text?.toString() ?: ""

    fun getCursorLine(): Int = codeEditor.getCurrentLineNumber()

    fun getCursorColumn(): Int = codeEditor.getCurrentColumn()

    fun setEditorColors(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
        @ColorInt gutterBackground: Int,
        @ColorInt lineNumber: Int,
        @ColorInt lineNumberActive: Int,
        @ColorInt lineHighlight: Int
    ) {
        editorBackgroundColor = background
        editorForegroundColor = foreground
        gutterBackgroundColor = gutterBackground
        lineNumberColor = lineNumber
        lineNumberActiveColor = lineNumberActive
        lineHighlightColor = lineHighlight
        applyDefaultColors()
    }

    private fun applyDefaultColors() {
        codeEditor.setBackgroundColor(editorBackgroundColor)
        codeEditor.setTextColor(editorForegroundColor)
        lineNumberView.setGutterBackgroundColor(gutterBackgroundColor)
        lineNumberView.setLineNumberColor(lineNumberColor)
        lineNumberView.setLineNumberActiveColor(lineNumberActiveColor)
        lineNumberView.setCurrentLineHighlightColor(lineHighlightColor)
    }

    fun setReadOnly(readOnly: Boolean) {
        codeEditor.readOnlyMode = readOnly
    }

    fun setLineWrapEnabled(enabled: Boolean) {
        codeEditor.lineWrapEnabled = enabled
    }

    fun setTextSize(sizeSp: Float) {
        codeEditor.setEditorTextSize(sizeSp)
        lineNumberView.setTextSizePx(sizeSp * context.resources.displayMetrics.scaledDensity)
    }

    fun setOnFileModifiedListener(listener: (Boolean) -> Unit) {
        onFileModifiedListener = listener
    }

    fun setOnCursorPositionChangedListener(listener: (Int, Int) -> Unit) {
        onCursorPositionChangedListener = listener
    }

    fun setOnFileOpenRequestListener(listener: (String) -> Unit) {
        onFileOpenRequestListener = listener
    }

    fun setOnEditorSaveListener(listener: (String, String) -> Unit) {
        onEditorSaveListener = listener
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

    private fun detectEncoding(): String {
        return "UTF-8"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissSuggestionPopup()
        syntaxHighlightRunnable?.let { debounceHandler.removeCallbacks(it) }
        suggestionRunnable?.let { debounceHandler.removeCallbacks(it) }
    }

    inner class SuggestionAdapter(
        context: Context,
        private val suggestions: List<Suggestion>
    ) : android.widget.BaseAdapter() {

        private val inflater = android.view.LayoutInflater.from(context)

        override fun getCount() = suggestions.size

        override fun getItem(position: Int) = suggestions[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: TextView(context).apply {
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
                typeface = Typeface.MONOSPACE
            }

            val suggestion = suggestions[position]
            (view as TextView).text = "${suggestion.label}  ${suggestion.detail}"
            return view
        }
    }

    companion object {
        private const val GUTTER_WIDTH_DP = 48
        private const val SYNTAX_HIGHLIGHT_DELAY_MS = 300L
        private const val SUGGESTION_DELAY_MS = 200L

        @ColorInt private const val KEYWORD_COLOR = 0xFF569CD6.toInt()
        @ColorInt private const val STRING_COLOR = 0xFFCE9178.toInt()
        @ColorInt private const val NUMBER_COLOR = 0xFFB5CEA8.toInt()
        @ColorInt private const val COMMENT_COLOR = 0xFF6A9955.toInt()
        @ColorInt private const val TYPE_COLOR = 0xFF4EC9B0.toInt()
        @ColorInt private const val FUNCTION_COLOR = 0xFFDCDCAA.toInt()
        @ColorInt private const val VARIABLE_COLOR = 0xFF9CDCFE.toInt()
        @ColorInt private const val OPERATOR_COLOR = 0xFFD4D4D4.toInt()
    }
}