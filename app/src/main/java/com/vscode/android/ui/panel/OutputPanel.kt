package com.vscode.android.ui.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt

class OutputPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerRow: LinearLayout
    private val channelSpinner: Spinner
    private val clearButton: TextView
    private val copyButton: TextView
    private val autoScrollButton: TextView

    private val outputTextView: TextView
    private val outputScrollView: ScrollView
    private val outputHorizontalScrollView: HorizontalScrollView

    private var autoScrollToBottom: Boolean = true
    private var currentChannel: String = "Build"

    private val channelOutputs = mutableMapOf<String, StringBuilder>()

    @ColorInt
    private var backgroundColor: Int = 0xFF1E1E1E.toInt()

    @ColorInt
    private var terminalForegroundColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var textPrimaryColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var textSecondaryColor: Int = 0xFF969696.toInt()

    @ColorInt
    private var accentBlue: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var panelTabBackground: Int = 0xFF2D2D2D.toInt()

    @ColorInt
    private var dropdownBackground: Int = 0xFF252526.toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)

        headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panelTabBackground)
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(28))
        }

        channelSpinner = Spinner(context).apply {
            setBackgroundColor(dropdownBackground)
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(dropdownBackground))
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(120),
                LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dpToPx(4)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val channel = channels[position]
                    switchChannel(channel)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        clearButton = TextView(context).apply {
            text = "\u2326"
            setTextColor(textPrimaryColor)
            textSize = 14f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setOnClickListener { clearOutput() }
        }
        copyButton = TextView(context).apply {
            text = "\uD83D\uDCCB"
            setTextColor(textPrimaryColor)
            textSize = 12f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setOnClickListener { copyOutput() }
        }
        autoScrollButton = TextView(context).apply {
            text = "\u2193"
            setTextColor(if (autoScrollToBottom) accentBlue else textSecondaryColor)
            textSize = 14f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setOnClickListener {
                autoScrollToBottom = !autoScrollToBottom
                autoScrollButton.setTextColor(if (autoScrollToBottom) accentBlue else textSecondaryColor)
                if (autoScrollToBottom) {
                    outputScrollView.post {
                        outputScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }

        headerRow.addView(channelSpinner)
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        headerRow.addView(clearButton)
        headerRow.addView(copyButton)
        headerRow.addView(autoScrollButton)

        outputTextView = TextView(context).apply {
            setTextColor(terminalForegroundColor)
            setBackgroundColor(backgroundColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setHorizontallyScrolling(true)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        outputHorizontalScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            addView(outputTextView)
        }

        outputScrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            addView(outputHorizontalScrollView)
            viewTreeObserver.addOnScrollChangedListener {
                autoScrollToBottom = !canScrollVertically(1)
                autoScrollButton.setTextColor(if (autoScrollToBottom) accentBlue else textSecondaryColor)
            }
        }

        addView(headerRow)
        addView(outputScrollView)

        initChannels()
    }

    private fun initChannels() {
        for (channel in channels) {
            channelOutputs[channel] = StringBuilder()
        }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, channels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        channelSpinner.adapter = adapter
        channelSpinner.setSelection(0)
    }

    fun appendOutput(text: String, channel: String? = null) {
        val targetChannel = channel ?: currentChannel
        val output = channelOutputs.getOrPut(targetChannel) { StringBuilder() }
        output.append(text)

        if (targetChannel == currentChannel) {
            outputTextView.text = output.toString()
            if (autoScrollToBottom) {
                outputScrollView.post {
                    outputScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    fun appendOutputLine(text: String, channel: String? = null) {
        appendOutput("$text\n", channel)
    }

    fun clearOutput(channel: String? = null) {
        val targetChannel = channel ?: currentChannel
        channelOutputs[targetChannel]?.clear()
        if (targetChannel == currentChannel) {
            outputTextView.text = ""
        }
    }

    fun clearAllOutputs() {
        channelOutputs.values.forEach { it.clear() }
        outputTextView.text = ""
    }

    private fun switchChannel(channel: String) {
        currentChannel = channel
        val output = channelOutputs.getOrPut(channel) { StringBuilder() }
        outputTextView.text = output.toString()
        if (autoScrollToBottom) {
            outputScrollView.post {
                outputScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    fun getCurrentChannel(): String = currentChannel

    fun setChannel(channel: String) {
        val index = channels.indexOf(channel)
        if (index >= 0) {
            channelSpinner.setSelection(index)
        }
    }

    private fun copyOutput() {
        val text = outputTextView.text?.toString() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("output", text))
        Toast.makeText(context, "Output copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int,
        @ColorInt accent: Int,
        @ColorInt tabBg: Int,
        @ColorInt dropdownBg: Int
    ) {
        backgroundColor = background
        terminalForegroundColor = foreground
        textPrimaryColor = textPrimary
        textSecondaryColor = textSecondary
        accentBlue = accent
        panelTabBackground = tabBg
        dropdownBackground = dropdownBg
        setBackgroundColor(backgroundColor)
        headerRow.setBackgroundColor(panelTabBackground)
        channelSpinner.setBackgroundColor(dropdownBackground)
        outputTextView.setBackgroundColor(backgroundColor)
        outputTextView.setTextColor(terminalForegroundColor)
        clearButton.setTextColor(textPrimaryColor)
        copyButton.setTextColor(textPrimaryColor)
        autoScrollButton.setTextColor(if (autoScrollToBottom) accentBlue else textSecondaryColor)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        val channels = listOf(
            "Build",
            "Git",
            "Extension",
            "Terminal",
            "Debug",
            "Tasks",
            "Problems"
        )
    }
}