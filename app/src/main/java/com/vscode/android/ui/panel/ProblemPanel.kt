package com.vscode.android.ui.panel

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class Problem(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: ProblemSeverity
)

enum class ProblemSeverity {
    ERROR, WARNING, INFO
}

class ProblemPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val filterRow: LinearLayout
    private val errorsFilterButton: TextView
    private val warningsFilterButton: TextView
    private val infoFilterButton: TextView
    private val problemsCount: TextView
    private val problemsRecyclerView: RecyclerView
    private val problemsAdapter: ProblemsAdapter

    private val allProblems = mutableListOf<Problem>()
    private val filteredProblems = mutableListOf<Problem>()

    private var showErrors: Boolean = true
    private var showWarnings: Boolean = true
    private var showInfo: Boolean = true

    private var onProblemClickListener: ((Problem) -> Unit)? = null

    @ColorInt
    private var backgroundColor: Int = 0xFF1E1E1E.toInt()

    @ColorInt
    private var textPrimaryColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var textSecondaryColor: Int = 0xFF969696.toInt()

    @ColorInt
    private var errorColor: Int = 0xFFF48771.toInt()

    @ColorInt
    private var warningColor: Int = 0xFFCCA700.toInt()

    @ColorInt
    private var infoColor: Int = 0xFF75BEFF.toInt()

    @ColorInt
    private var accentBlue: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var panelTabBackground: Int = 0xFF2D2D2D.toInt()

    @ColorInt
    private var panelTitleActiveColor: Int = 0xFFFFFFFF.toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)

        filterRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setBackgroundColor(panelTabBackground)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        errorsFilterButton = createFilterButton("Errors", errorColor, true) {
            showErrors = !showErrors
            applyFilters()
        }
        warningsFilterButton = createFilterButton("Warnings", warningColor, true) {
            showWarnings = !showWarnings
            applyFilters()
        }
        infoFilterButton = createFilterButton("Info", infoColor, true) {
            showInfo = !showInfo
            applyFilters()
        }
        problemsCount = TextView(context).apply {
            setTextColor(textSecondaryColor)
            textSize = 12f
            setPadding(dpToPx(8), 0, 0, 0)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        filterRow.addView(errorsFilterButton)
        filterRow.addView(warningsFilterButton)
        filterRow.addView(infoFilterButton)
        filterRow.addView(problemsCount)

        problemsAdapter = ProblemsAdapter()
        problemsRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = problemsAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        addView(filterRow)
        addView(problemsRecyclerView)
    }

    private fun createFilterButton(
        label: String,
        @ColorInt color: Int,
        initialActive: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(if (initialActive) color else textSecondaryColor)
            textSize = 12f
            setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
            setOnClickListener { onClick() }
        }
    }

    fun setProblems(problems: List<Problem>) {
        allProblems.clear()
        allProblems.addAll(problems)
        applyFilters()
    }

    fun addProblem(problem: Problem) {
        allProblems.add(problem)
        applyFilters()
    }

    fun clearProblems() {
        allProblems.clear()
        filteredProblems.clear()
        problemsAdapter.notifyDataSetChanged()
        updateCount()
    }

    fun clearProblemsForFile(filePath: String) {
        allProblems.removeAll { it.file == filePath }
        applyFilters()
    }

    private fun applyFilters() {
        filteredProblems.clear()
        filteredProblems.addAll(allProblems.filter { problem ->
            when (problem.severity) {
                ProblemSeverity.ERROR -> showErrors
                ProblemSeverity.WARNING -> showWarnings
                ProblemSeverity.INFO -> showInfo
            }
        })

        errorsFilterButton.setTextColor(if (showErrors) errorColor else textSecondaryColor)
        warningsFilterButton.setTextColor(if (showWarnings) warningColor else textSecondaryColor)
        infoFilterButton.setTextColor(if (showInfo) infoColor else textSecondaryColor)

        problemsAdapter.notifyDataSetChanged()
        updateCount()
    }

    private fun updateCount() {
        val errorCount = allProblems.count { it.severity == ProblemSeverity.ERROR }
        val warningCount = allProblems.count { it.severity == ProblemSeverity.WARNING }
        val infoCount = allProblems.count { it.severity == ProblemSeverity.INFO }

        val parts = mutableListOf<String>()
        if (errorCount > 0) parts.add("\u2715 $errorCount")
        if (warningCount > 0) parts.add("\u26A0 $warningCount")
        if (infoCount > 0) parts.add("\u2139 $infoCount")

        problemsCount.text = parts.joinToString("  ")
    }

    fun getErrorCount(): Int = allProblems.count { it.severity == ProblemSeverity.ERROR }

    fun getWarningCount(): Int = allProblems.count { it.severity == ProblemSeverity.WARNING }

    fun getInfoCount(): Int = allProblems.count { it.severity == ProblemSeverity.INFO }

    fun getTotalCount(): Int = allProblems.size

    fun setOnProblemClickListener(listener: (Problem) -> Unit) {
        onProblemClickListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int,
        @ColorInt error: Int,
        @ColorInt warning: Int,
        @ColorInt info: Int,
        @ColorInt accent: Int,
        @ColorInt tabBg: Int,
        @ColorInt titleActive: Int
    ) {
        backgroundColor = background
        textPrimaryColor = textPrimary
        textSecondaryColor = textSecondary
        errorColor = error
        warningColor = warning
        infoColor = info
        accentBlue = accent
        panelTabBackground = tabBg
        panelTitleActiveColor = titleActive
        setBackgroundColor(backgroundColor)
        filterRow.setBackgroundColor(panelTabBackground)
        problemsCount.setTextColor(textSecondaryColor)
        applyFilters()
        problemsAdapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class ProblemsAdapter : RecyclerView.Adapter<ProblemsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ProblemItemView(parent.context))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(filteredProblems[position])
        }

        override fun getItemCount() = filteredProblems.size

        inner class ViewHolder(private val view: ProblemItemView) : RecyclerView.ViewHolder(view) {
            fun bind(problem: Problem) {
                view.setProblem(problem)
                view.setOnClickListener {
                    onProblemClickListener?.invoke(problem)
                }
            }
        }
    }

    inner class ProblemItemView(context: Context) : LinearLayout(context) {

        private val severityIcon: TextView
        private val fileView: TextView
        private val locationView: TextView
        private val messageView: TextView

        init {
            orientation = VERTICAL
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))

            val headerRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            severityIcon = TextView(context).apply {
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, dpToPx(8), 0)
            }
            fileView = TextView(context).apply {
                setTextColor(textPrimaryColor)
                textSize = 12f
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            locationView = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 12f
                typeface = Typeface.MONOSPACE
            }
            headerRow.addView(severityIcon)
            headerRow.addView(fileView)
            headerRow.addView(locationView)

            messageView = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 12f
                setPadding(dpToPx(22), 0, 0, 0)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            addView(headerRow)
            addView(messageView)
        }

        fun setProblem(problem: Problem) {
            when (problem.severity) {
                ProblemSeverity.ERROR -> {
                    severityIcon.text = "\u2715"
                    severityIcon.setTextColor(errorColor)
                }
                ProblemSeverity.WARNING -> {
                    severityIcon.text = "\u26A0"
                    severityIcon.setTextColor(warningColor)
                }
                ProblemSeverity.INFO -> {
                    severityIcon.text = "\u2139"
                    severityIcon.setTextColor(infoColor)
                }
            }
            val fileName = problem.file.substringAfterLast('/')
            fileView.text = fileName
            locationView.text = "${problem.line}:${problem.column}"
            messageView.text = problem.message
        }
    }
}