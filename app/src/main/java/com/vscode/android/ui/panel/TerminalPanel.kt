package com.vscode.android.ui.panel

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt

data class TerminalTab(
    val id: String,
    val name: String,
    val output: StringBuilder = StringBuilder(),
    val history: MutableList<String> = mutableListOf(),
    var historyIndex: Int = -1
)

class TerminalPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val tabsContainer: LinearLayout
    private val newTerminalButton: TextView
    private val killTerminalButton: TextView
    private val clearTerminalButton: TextView
    private val headerRow: LinearLayout

    private val terminalOutput: TextView
    private val outputScrollView: ScrollView
    private val outputHorizontalScrollView: HorizontalScrollView

    private val inputRow: LinearLayout
    private val promptLabel: TextView
    private val commandInput: EditText

    private val terminalTabs = mutableListOf<TerminalTab>()
    private var activeTabIndex: Int = -1
    private var autoScrollToBottom: Boolean = true

    private var onCommandExecuteListener: ((String, TerminalTab) -> Unit)? = null
    private var onTerminalCreatedListener: ((TerminalTab) -> Unit)? = null
    private var onTerminalKilledListener: ((TerminalTab) -> Unit)? = null

    @ColorInt
    private var backgroundColor: Int = 0xFF1E1E1E.toInt()

    @ColorInt
    private var terminalForegroundColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var tabBackgroundColor: Int = 0xFF2D2D2D.toInt()

    @ColorInt
    private var textPrimaryColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var textSecondaryColor: Int = 0xFF969696.toInt()

    @ColorInt
    private var accentGreen: Int = 0xFF6A9955.toInt()

    @ColorInt
    private var accentRed: Int = 0xFFF44747.toInt()

    @ColorInt
    private var accentBlue: Int = 0xFF007ACC.toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)

        headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(tabBackgroundColor)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(32))
        }

        tabsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }

        newTerminalButton = TextView(context).apply {
            text = "+"
            setTextColor(textPrimaryColor)
            textSize = 16f
            setPadding(dpToPx(8), 0, dpToPx(4), 0)
            setOnClickListener { createNewTerminal() }
        }
        killTerminalButton = TextView(context).apply {
            text = "\u2715"
            setTextColor(accentRed)
            textSize = 14f
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
            setOnClickListener { killActiveTerminal() }
        }
        clearTerminalButton = TextView(context).apply {
            text = "\u2326"
            setTextColor(textPrimaryColor)
            textSize = 14f
            setPadding(dpToPx(4), 0, dpToPx(8), 0)
            setOnClickListener { clearActiveTerminal() }
        }

        headerRow.addView(tabsContainer)
        headerRow.addView(newTerminalButton)
        headerRow.addView(killTerminalButton)
        headerRow.addView(clearTerminalButton)

        terminalOutput = TextView(context).apply {
            setTextColor(terminalForegroundColor)
            setBackgroundColor(backgroundColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setHorizontallyScrolling(true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        outputHorizontalScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            addView(terminalOutput)
        }

        outputScrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            addView(outputHorizontalScrollView)
            viewTreeObserver.addOnScrollChangedListener {
                autoScrollToBottom = !canScrollVertically(1)
            }
        }

        inputRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundColor(backgroundColor)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        promptLabel = TextView(context).apply {
            text = "$ "
            setTextColor(accentGreen)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dpToPx(8), dpToPx(4), 0, dpToPx(4))
        }
        commandInput = EditText(context).apply {
            setTextColor(terminalForegroundColor)
            setBackgroundColor(backgroundColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dpToPx(4), dpToPx(4), dpToPx(8), dpToPx(4))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = null
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    executeCommand()
                    true
                } else false
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            navigateHistory(-1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            navigateHistory(1)
                            true
                        }
                        else -> false
                    }
                } else false
            }
        }
        inputRow.addView(promptLabel)
        inputRow.addView(commandInput)

        addView(headerRow)
        addView(outputScrollView)
        addView(inputRow)

        createNewTerminal()
    }

    fun createNewTerminal(name: String? = null): TerminalTab {
        val tab = TerminalTab(
            id = System.currentTimeMillis().toString(),
            name = name ?: "Terminal ${terminalTabs.size + 1}"
        )
        terminalTabs.add(tab)
        addTabButton(tab)
        setActiveTab(terminalTabs.size - 1)
        onTerminalCreatedListener?.invoke(tab)
        return tab
    }

    private fun addTabButton(tab: TerminalTab) {
        val tabButton = TextView(context).apply {
            text = tab.name
            setTextColor(textSecondaryColor)
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            gravity = Gravity.CENTER
            tag = tab.id
            setOnClickListener {
                val index = terminalTabs.indexOfFirst { it.id == tab.id }
                if (index >= 0) setActiveTab(index)
            }
        }
        tabsContainer.addView(tabButton)
    }

    private fun setActiveTab(index: Int) {
        if (index !in terminalTabs.indices) return
        activeTabIndex = index
        val tab = terminalTabs[index]

        for (i in 0 until tabsContainer.childCount) {
            val child = tabsContainer.getChildAt(i) as? TextView ?: continue
            child.setTextColor(if (child.tag == tab.id) textPrimaryColor else textSecondaryColor)
        }

        terminalOutput.text = parseAnsiCodes(tab.output.toString())
        if (autoScrollToBottom) {
            outputScrollView.post {
                outputScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun killActiveTerminal() {
        if (terminalTabs.size <= 1) {
            clearActiveTerminal()
            return
        }
        val tab = getActiveTerminal() ?: return
        val index = terminalTabs.indexOf(tab)
        terminalTabs.remove(tab)

        for (i in 0 until tabsContainer.childCount) {
            val child = tabsContainer.getChildAt(i) as? TextView ?: continue
            if (child.tag == tab.id) {
                tabsContainer.removeView(child)
                break
            }
        }

        onTerminalKilledListener?.invoke(tab)

        if (activeTabIndex >= terminalTabs.size) {
            setActiveTab(terminalTabs.size - 1)
        } else if (terminalTabs.isNotEmpty()) {
            setActiveTab(activeTabIndex.coerceAtMost(terminalTabs.size - 1))
        }
    }

    fun clearActiveTerminal() {
        val tab = getActiveTerminal() ?: return
        tab.output.clear()
        terminalOutput.text = ""
    }

    private fun executeCommand() {
        val command = commandInput.text.toString()
        if (command.isBlank()) return
        val tab = getActiveTerminal() ?: return

        tab.history.add(command)
        tab.historyIndex = -1
        tab.output.append("$ $command\n")
        terminalOutput.text = parseAnsiCodes(tab.output.toString())
        commandInput.text?.clear()

        onCommandExecuteListener?.invoke(command, tab)
    }

    fun appendOutput(text: String, tabId: String? = null) {
        val tab = if (tabId != null) {
            terminalTabs.find { it.id == tabId }
        } else {
            getActiveTerminal()
        } ?: return

        tab.output.append(text)
        if (tab.id == getActiveTerminal()?.id) {
            terminalOutput.text = parseAnsiCodes(tab.output.toString())
            if (autoScrollToBottom) {
                outputScrollView.post {
                    outputScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    fun appendOutputLine(text: String, tabId: String? = null) {
        appendOutput("$text\n", tabId)
    }

    private fun navigateHistory(direction: Int) {
        val tab = getActiveTerminal() ?: return
        if (tab.history.isEmpty()) return

        tab.historyIndex = when {
            direction < 0 && tab.historyIndex < tab.history.size - 1 -> tab.historyIndex + 1
            direction > 0 && tab.historyIndex > 0 -> tab.historyIndex - 1
            direction > 0 && tab.historyIndex == -1 -> -1
            else -> tab.historyIndex
        }

        if (tab.historyIndex >= 0) {
            commandInput.setText(tab.history[tab.history.size - 1 - tab.historyIndex])
            commandInput.setSelection(commandInput.text?.length ?: 0)
        } else {
            commandInput.text?.clear()
        }
    }

    fun getActiveTerminal(): TerminalTab? {
        return if (activeTabIndex in terminalTabs.indices) terminalTabs[activeTabIndex] else null
    }

    fun getTerminal(id: String): TerminalTab? {
        return terminalTabs.find { it.id == id }
    }

    fun getAllTerminals(): List<TerminalTab> = terminalTabs.toList()

    fun setAutoScrollToBottom(enabled: Boolean) {
        autoScrollToBottom = enabled
        if (enabled) {
            outputScrollView.post {
                outputScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    fun setOnCommandExecuteListener(listener: (String, TerminalTab) -> Unit) {
        onCommandExecuteListener = listener
    }

    fun setOnTerminalCreatedListener(listener: (TerminalTab) -> Unit) {
        onTerminalCreatedListener = listener
    }

    fun setOnTerminalKilledListener(listener: (TerminalTab) -> Unit) {
        onTerminalKilledListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
        @ColorInt tabBg: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int,
        @ColorInt green: Int,
        @ColorInt red: Int
    ) {
        backgroundColor = background
        terminalForegroundColor = foreground
        tabBackgroundColor = tabBg
        textPrimaryColor = textPrimary
        textSecondaryColor = textSecondary
        accentGreen = green
        accentRed = red
        setBackgroundColor(backgroundColor)
        headerRow.setBackgroundColor(tabBackgroundColor)
        terminalOutput.setBackgroundColor(backgroundColor)
        terminalOutput.setTextColor(terminalForegroundColor)
        inputRow.setBackgroundColor(backgroundColor)
        promptLabel.setTextColor(accentGreen)
        commandInput.setBackgroundColor(backgroundColor)
        commandInput.setTextColor(terminalForegroundColor)
        newTerminalButton.setTextColor(textPrimaryColor)
        killTerminalButton.setTextColor(accentRed)
        clearTerminalButton.setTextColor(textPrimaryColor)
    }

    private fun parseAnsiCodes(text: String): CharSequence {
        val result = SpannableStringBuilder()
        val ansiPattern = Regex("\u001B\\[(\\d+(;\\d+)*)?m")
        var lastMatchEnd = 0
        var currentColor = terminalForegroundColor

        for (match in ansiPattern.findAll(text)) {
            result.append(text.substring(lastMatchEnd, match.range.first))

            val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
            for (code in codes) {
                currentColor = when (code) {
                    0 -> terminalForegroundColor
                    30 -> ANSI_BLACK
                    31 -> ANSI_RED
                    32 -> ANSI_GREEN
                    33 -> ANSI_YELLOW
                    34 -> ANSI_BLUE
                    35 -> ANSI_MAGENTA
                    36 -> ANSI_CYAN
                    37 -> ANSI_WHITE
                    90 -> ANSI_BRIGHT_BLACK
                    91 -> ANSI_BRIGHT_RED
                    92 -> ANSI_BRIGHT_GREEN
                    93 -> ANSI_BRIGHT_YELLOW
                    94 -> ANSI_BRIGHT_BLUE
                    95 -> ANSI_BRIGHT_MAGENTA
                    96 -> ANSI_BRIGHT_CYAN
                    97 -> ANSI_BRIGHT_WHITE
                    else -> currentColor
                }
            }
            lastMatchEnd = match.range.last + 1
        }

        result.append(text.substring(lastMatchEnd))
        return result
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        @ColorInt private const val ANSI_BLACK = 0xFF0C0C0C.toInt()
        @ColorInt private const val ANSI_RED = 0xFFC50F1F.toInt()
        @ColorInt private const val ANSI_GREEN = 0xFF13A10E.toInt()
        @ColorInt private const val ANSI_YELLOW = 0xFFC19C00.toInt()
        @ColorInt private const val ANSI_BLUE = 0xFF0037DA.toInt()
        @ColorInt private const val ANSI_MAGENTA = 0xFF881798.toInt()
        @ColorInt private const val ANSI_CYAN = 0xFF3A96DD.toInt()
        @ColorInt private const val ANSI_WHITE = 0xFFCCCCCC.toInt()
        @ColorInt private const val ANSI_BRIGHT_BLACK = 0xFF767676.toInt()
        @ColorInt private const val ANSI_BRIGHT_RED = 0xFFE74856.toInt()
        @ColorInt private const val ANSI_BRIGHT_GREEN = 0xFF16C60C.toInt()
        @ColorInt private const val ANSI_BRIGHT_YELLOW = 0xFFF9F1A5.toInt()
        @ColorInt private const val ANSI_BRIGHT_BLUE = 0xFF3B78FF.toInt()
        @ColorInt private const val ANSI_BRIGHT_MAGENTA = 0xFFB4009E.toInt()
        @ColorInt private const val ANSI_BRIGHT_CYAN = 0xFF61D6D6.toInt()
        @ColorInt private const val ANSI_BRIGHT_WHITE = 0xFFF2F2F2.toInt()
    }
}