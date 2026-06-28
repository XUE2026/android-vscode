package com.vscode.android.ui.statusbar

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt

data class StatusBarItem(
    val text: String,
    val icon: String = "",
    var clickListener: (() -> Unit)? = null
)

class StatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val leftContainer: LinearLayout
    private val rightContainer: LinearLayout
    private val centerSpacer: View

    private val leftItems = mutableListOf<StatusBarItem>()
    private val rightItems = mutableListOf<StatusBarItem>()

    @ColorInt
    private var backgroundColor: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var foregroundColor: Int = 0xFFFFFFFF.toInt()

    @ColorInt
    private var itemHoverBackgroundColor: Int = 0xFFFFFF1A.toInt()

    @ColorInt
    private var prominentItemBackgroundColor: Int = 0xFF000000.toInt()

    private var gitBranchView: TextView? = null
    private var gitSyncStatusView: TextView? = null
    private var problemsCountView: TextView? = null
    private var cursorPositionView: TextView? = null
    private var indentView: TextView? = null
    private var encodingView: TextView? = null
    private var languageModeView: TextView? = null
    private var feedbackView: TextView? = null

    init {
        orientation = HORIZONTAL
        setBackgroundColor(backgroundColor)
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            dpToPx(STATUS_BAR_HEIGHT_DP)
        )
        gravity = Gravity.CENTER_VERTICAL

        leftContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
        }

        centerSpacer = View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }

        rightContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
        }

        addView(leftContainer)
        addView(centerSpacer)
        addView(rightContainer)

        initDefaultItems()
    }

    private fun initDefaultItems() {
        gitBranchView = createStatusItem("\u2387 main").also {
            leftContainer.addView(it)
        }
        gitSyncStatusView = createStatusItem("\u21C5 0\u2193 0\u2191").also {
            leftContainer.addView(it)
        }
        problemsCountView = createStatusItem("\u2715 0 \u26A0 0").also {
            leftContainer.addView(it)
        }

        cursorPositionView = createStatusItem("Ln 1, Col 1").also {
            rightContainer.addView(it)
        }
        indentView = createStatusItem("Spaces: 4").also {
            rightContainer.addView(it)
        }
        encodingView = createStatusItem("UTF-8").also {
            rightContainer.addView(it)
        }
        languageModeView = createStatusItem("Plain Text").also {
            rightContainer.addView(it)
        }
        feedbackView = createStatusItem("Feedback").also {
            it.setOnClickListener {
                rightItems.find { item -> item.text == "Feedback" }?.clickListener?.invoke()
            }
            rightContainer.addView(it)
        }
    }

    private fun createStatusItem(text: String): TextView {
        val item = TextView(context).apply {
            this.text = text
            setTextColor(foregroundColor)
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                val item = findItemByText(text)
                item?.clickListener?.invoke()
            }
        }
        return item
    }

    private fun findItemByText(text: String): StatusBarItem? {
        return leftItems.find { it.text == text } ?: rightItems.find { it.text == text }
    }

    fun setGitBranch(branch: String) {
        gitBranchView?.text = "\u2387 $branch"
    }

    fun setGitSyncStatus(ahead: Int = 0, behind: Int = 0) {
        gitSyncStatusView?.text = "\u21C5 ${behind}\u2193 ${ahead}\u2191"
    }

    fun setProblemsCount(errors: Int, warnings: Int) {
        problemsCountView?.text = "\u2715 $errors \u26A0 $warnings"
    }

    fun setCursorPosition(line: Int, column: Int) {
        cursorPositionView?.text = "Ln $line, Col $column"
    }

    fun setIndentation(indent: String) {
        indentView?.text = indent
    }

    fun setEncoding(encoding: String) {
        encodingView?.text = encoding
    }

    fun setLanguageMode(language: String) {
        languageModeView?.text = language
    }

    fun addLeftItem(item: StatusBarItem) {
        leftItems.add(item)
        val view = createStatusItem("${item.icon} ${item.text}".trim())
        view.setOnClickListener { item.clickListener?.invoke() }
        leftContainer.addView(view)
    }

    fun addRightItem(item: StatusBarItem) {
        rightItems.add(item)
        val view = createStatusItem("${item.icon} ${item.text}".trim())
        view.setOnClickListener { item.clickListener?.invoke() }
        rightContainer.addView(view)
    }

    fun removeLeftItem(text: String) {
        leftItems.removeAll { it.text == text }
        for (i in 0 until leftContainer.childCount) {
            val child = leftContainer.getChildAt(i) as? TextView ?: continue
            if (child.text.toString().contains(text)) {
                leftContainer.removeViewAt(i)
                break
            }
        }
    }

    fun removeRightItem(text: String) {
        rightItems.removeAll { it.text == text }
        for (i in 0 until rightContainer.childCount) {
            val child = rightContainer.getChildAt(i) as? TextView ?: continue
            if (child.text.toString().contains(text)) {
                rightContainer.removeViewAt(i)
                break
            }
        }
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
        @ColorInt itemHoverBg: Int,
        @ColorInt prominentBg: Int
    ) {
        backgroundColor = background
        foregroundColor = foreground
        itemHoverBackgroundColor = itemHoverBg
        prominentItemBackgroundColor = prominentBg
        setBackgroundColor(backgroundColor)

        fun updateContainer(container: LinearLayout) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i) as? TextView ?: continue
                child.setTextColor(foregroundColor)
            }
        }
        updateContainer(leftContainer)
        updateContainer(rightContainer)
    }

    fun setOnGitBranchClickListener(listener: () -> Unit) {
        gitBranchView?.setOnClickListener { listener() }
    }

    fun setOnCursorPositionClickListener(listener: () -> Unit) {
        cursorPositionView?.setOnClickListener { listener() }
    }

    fun setOnEncodingClickListener(listener: () -> Unit) {
        encodingView?.setOnClickListener { listener() }
    }

    fun setOnLanguageModeClickListener(listener: () -> Unit) {
        languageModeView?.setOnClickListener { listener() }
    }

    fun setOnFeedbackClickListener(listener: () -> Unit) {
        feedbackView?.setOnClickListener { listener() }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        const val STATUS_BAR_HEIGHT_DP = 28
    }
}