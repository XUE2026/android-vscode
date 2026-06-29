package com.vscode.android.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText

class CodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var lineWrapEnabled: Boolean = false
        set(value) {
            field = value
            applyLineWrap()
        }

    var readOnlyMode: Boolean = false
        set(value) {
            field = value
            isFocusable = !value
            isFocusableInTouchMode = !value
            isCursorVisible = !value
        }

    var tabSize: Int = 4
        set(value) {
            field = value.coerceIn(1, 8)
        }

    var showWhitespaceCharacters: Boolean = false
        set(value) {
            field = value
            if (value) {
                val text = text?.toString() ?: ""
                setText(renderWhitespace(text))
            }
        }

    private var currentTextSizeSp: Float = 14f
    private var debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var onTextChangeDebouncedListener: ((String) -> Unit)? = null
    private var onSelectionChangeListener: ((Int, Int) -> Unit)? = null
    private var onPasteListener: ((String) -> Unit)? = null
    private var isInternalTextChange = false

    init {
        setupEditor()
    }

    private fun setupEditor() {
        gravity = Gravity.TOP or Gravity.START
        setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        setHorizontallyScrolling(!lineWrapEnabled)
        isVerticalScrollBarEnabled = true
        movementMethod = ScrollingMovementMethod()
        inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION

        typeface = getMonospaceTypeface()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSizeSp)

        addTextChangedListener(createTextWatcher())
    }

    private fun getMonospaceTypeface(): Typeface {
        return try {
            Typeface.create("JetBrains Mono", Typeface.NORMAL)
        } catch (_: Exception) {
            try {
                Typeface.create("Droid Sans Mono", Typeface.NORMAL)
            } catch (_: Exception) {
                Typeface.MONOSPACE
            }
        }
    }

    private fun createTextWatcher(): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isInternalTextChange) return
                val text = s?.toString() ?: ""
                debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    onTextChangeDebouncedListener?.invoke(text)
                }
                debounceRunnable = runnable
                debounceHandler.postDelayed(runnable, DEBOUNCE_DELAY_MS)
            }
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangeListener?.invoke(selStart, selEnd)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TAB && event?.action == KeyEvent.ACTION_DOWN) {
            if (!readOnlyMode) {
                handleTabKey()
                return true
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }

    private fun handleTabKey() {
        val selStart = selectionStart
        val selEnd = selectionEnd
        if (selStart == selEnd) {
            val spaces = " ".repeat(tabSize)
            text?.insert(selStart, spaces)
            setSelection(selStart + tabSize)
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                onPasteListener?.invoke(pastedText)
            }
        }
        return super.onTextContextMenuItem(id)
    }

    fun setOnTextChangeDebouncedListener(listener: (String) -> Unit) {
        onTextChangeDebouncedListener = listener
    }

    fun setOnSelectionChangeListener(listener: (Int, Int) -> Unit) {
        onSelectionChangeListener = listener
    }

    fun setOnPasteListener(listener: (String) -> Unit) {
        onPasteListener = listener
    }

    fun setEditorTextSize(sizeSp: Float) {
        currentTextSizeSp = sizeSp.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSizeSp)
    }

    fun getEditorTextSize(): Float = currentTextSizeSp

    fun setEditorText(text: String) {
        isInternalTextChange = true
        setText(text)
        isInternalTextChange = false
    }

    fun applyLineWrap() {
        setHorizontallyScrolling(!lineWrapEnabled)
        if (lineWrapEnabled) {
            inputType = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE.inv()
            inputType = inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
    }

    override fun getLineCount(): Int {
        return layout?.lineCount ?: (text?.toString()?.lines()?.size ?: 1)
    }

    fun getLineForOffset(offset: Int): Int {
        return layout?.getLineForOffset(offset) ?: 0
    }

    fun getLineStart(line: Int): Int {
        return layout?.getLineStart(line) ?: 0
    }

    fun getLineEnd(line: Int): Int {
        return layout?.getLineEnd(line) ?: 0
    }

    fun getCurrentLineNumber(): Int {
        return getLineForOffset(selectionStart) + 1
    }

    fun getCurrentColumn(): Int {
        return selectionStart - getLineStart(getLineForOffset(selectionStart)) + 1
    }

    fun getLineText(line: Int): String {
        val start = getLineStart(line)
        val end = getLineEnd(line)
        return text?.subSequence(start, end.coerceAtMost(text!!.length))?.toString() ?: ""
    }

    private fun renderWhitespace(text: String): String {
        return text.replace(' ', '\u00B7').replace('\t', '\u2192')
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        onTextChangeDebouncedListener = null
        onSelectionChangeListener = null
        onPasteListener = null
    }

    companion object {
        private const val DEBOUNCE_DELAY_MS = 500L
        private const val MIN_TEXT_SIZE = 8f
        private const val MAX_TEXT_SIZE = 32f
    }
}