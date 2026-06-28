package com.vscode.android.ui.sidebar

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class GitFileStatus(
    val filePath: String,
    val status: String,
    val oldContent: String = "",
    val newContent: String = ""
)

class SourceControlPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val branchInfoView: TextView
    private val pullButton: TextView
    private val pushButton: TextView
    private val branchRow: LinearLayout

    private val stagedSectionLabel: TextView
    private val stagedRecyclerView: RecyclerView
    private val stagedAdapter: GitStatusAdapter

    private val changesSectionLabel: TextView
    private val changesRecyclerView: RecyclerView
    private val changesAdapter: GitStatusAdapter

    private val commitMessageInput: EditText
    private val commitButton: TextView
    private val commitRow: LinearLayout

    private val diffView: TextView
    private val diffContainer: ScrollView

    private val stagedFiles = mutableListOf<GitFileStatus>()
    private val changedFiles = mutableListOf<GitFileStatus>()
    private var currentBranch: String = "main"

    private var onStageListener: ((GitFileStatus) -> Unit)? = null
    private var onUnstageListener: ((GitFileStatus) -> Unit)? = null
    private var onCommitListener: ((String) -> Unit)? = null
    private var onPullListener: (() -> Unit)? = null
    private var onPushListener: (() -> Unit)? = null
    private var onFileStatusClickListener: ((GitFileStatus) -> Unit)? = null

    @ColorInt
    private var backgroundColor: Int = 0xFF252526.toInt()

    @ColorInt
    private var textPrimaryColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var textSecondaryColor: Int = 0xFF969696.toInt()

    @ColorInt
    private var accentBlue: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var accentGreen: Int = 0xFF6A9955.toInt()

    @ColorInt
    private var accentRed: Int = 0xFFF44747.toInt()

    @ColorInt
    private var accentYellow: Int = 0xFFDCDCAA.toInt()

    @ColorInt
    private var inputBackgroundColor: Int = 0xFF3C3C3C.toInt()

    @ColorInt
    private var sectionHeaderBackground: Int = 0x80808033.toInt()

    @ColorInt
    private var sectionHeaderForeground: Int = 0xFFBBBBBB.toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)

        branchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        branchInfoView = TextView(context).apply {
            text = "\u2387 $currentBranch"
            setTextColor(textPrimaryColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        pullButton = TextView(context).apply {
            text = "\u2193 Pull"
            setTextColor(accentBlue)
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener { onPullListener?.invoke() }
        }
        pushButton = TextView(context).apply {
            text = "\u2191 Push"
            setTextColor(accentBlue)
            textSize = 12f
            setPadding(dpToPx(8), 0, 0, 0)
            setOnClickListener { onPushListener?.invoke() }
        }
        branchRow.addView(branchInfoView)
        branchRow.addView(pullButton)
        branchRow.addView(pushButton)

        stagedSectionLabel = createSectionLabel("STAGED CHANGES")
        stagedAdapter = GitStatusAdapter(stagedFiles, true)
        stagedRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = stagedAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        changesSectionLabel = createSectionLabel("CHANGES")
        changesAdapter = GitStatusAdapter(changedFiles, false)
        changesRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = changesAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        commitMessageInput = EditText(context).apply {
            hint = "Message (press Ctrl+Enter to commit)"
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            setBackgroundColor(inputBackgroundColor)
            textSize = 13f
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        commitButton = TextView(context).apply {
            text = "Commit"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(accentBlue)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setOnClickListener {
                val message = commitMessageInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    onCommitListener?.invoke(message)
                }
            }
        }
        commitRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        commitRow.addView(commitMessageInput.apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dpToPx(8)
            }
        })
        commitRow.addView(commitButton)

        diffView = TextView(context).apply {
            setTextColor(textPrimaryColor)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }
        diffContainer = ScrollView(context).apply {
            visibility = GONE
            addView(diffView)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        addView(branchRow)
        addView(stagedSectionLabel)
        addView(stagedRecyclerView)
        addView(changesSectionLabel)
        addView(changesRecyclerView)
        addView(commitRow)
        addView(diffContainer)
    }

    private fun createSectionLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(sectionHeaderForeground)
            setBackgroundColor(sectionHeaderBackground)
            textSize = 11f
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
    }

    fun setBranch(branch: String) {
        currentBranch = branch
        branchInfoView.text = "\u2387 $branch"
    }

    fun getBranch(): String = currentBranch

    fun setStagedFiles(files: List<GitFileStatus>) {
        stagedFiles.clear()
        stagedFiles.addAll(files)
        stagedAdapter.notifyDataSetChanged()
        stagedSectionLabel.visibility = if (files.isEmpty()) GONE else VISIBLE
    }

    fun setChangedFiles(files: List<GitFileStatus>) {
        changedFiles.clear()
        changedFiles.addAll(files)
        changesAdapter.notifyDataSetChanged()
        changesSectionLabel.visibility = if (files.isEmpty()) GONE else VISIBLE
    }

    fun stageFile(file: GitFileStatus) {
        changedFiles.removeAll { it.filePath == file.filePath }
        stagedFiles.add(file)
        stagedAdapter.notifyDataSetChanged()
        changesAdapter.notifyDataSetChanged()
        stagedSectionLabel.visibility = if (stagedFiles.isEmpty()) GONE else VISIBLE
        changesSectionLabel.visibility = if (changedFiles.isEmpty()) GONE else VISIBLE
    }

    fun unstageFile(file: GitFileStatus) {
        stagedFiles.removeAll { it.filePath == file.filePath }
        changedFiles.add(file)
        stagedAdapter.notifyDataSetChanged()
        changesAdapter.notifyDataSetChanged()
        stagedSectionLabel.visibility = if (stagedFiles.isEmpty()) GONE else VISIBLE
        changesSectionLabel.visibility = if (changedFiles.isEmpty()) GONE else VISIBLE
    }

    fun showDiff(file: GitFileStatus) {
        diffContainer.visibility = VISIBLE
        val sb = StringBuilder()
        sb.appendLine("--- ${file.filePath}")
        sb.appendLine("+++ ${file.filePath}")
        val oldLines = file.oldContent.lines()
        val newLines = file.newContent.lines()
        val maxLines = maxOf(oldLines.size, newLines.size)
        for (i in 0 until maxLines) {
            val oldLine = oldLines.getOrElse(i) { "" }
            val newLine = newLines.getOrElse(i) { "" }
            when {
                oldLine != newLine && oldLine.isNotEmpty() && newLine.isNotEmpty() -> {
                    sb.appendLine("- $oldLine")
                    sb.appendLine("+ $newLine")
                }
                oldLine.isNotEmpty() && newLine.isEmpty() -> {
                    sb.appendLine("- $oldLine")
                }
                newLine.isNotEmpty() && oldLine.isEmpty() -> {
                    sb.appendLine("+ $newLine")
                }
                else -> {
                    sb.appendLine("  $oldLine")
                }
            }
        }
        diffView.text = sb.toString()
    }

    fun hideDiff() {
        diffContainer.visibility = GONE
    }

    fun clearCommitMessage() {
        commitMessageInput.text?.clear()
    }

    fun setOnStageListener(listener: (GitFileStatus) -> Unit) {
        onStageListener = listener
    }

    fun setOnUnstageListener(listener: (GitFileStatus) -> Unit) {
        onUnstageListener = listener
    }

    fun setOnCommitListener(listener: (String) -> Unit) {
        onCommitListener = listener
    }

    fun setOnPullListener(listener: () -> Unit) {
        onPullListener = listener
    }

    fun setOnPushListener(listener: () -> Unit) {
        onPushListener = listener
    }

    fun setOnFileStatusClickListener(listener: (GitFileStatus) -> Unit) {
        onFileStatusClickListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int,
        @ColorInt accent: Int,
        @ColorInt green: Int,
        @ColorInt red: Int,
        @ColorInt inputBg: Int,
        @ColorInt sectionHeaderBg: Int,
        @ColorInt sectionHeaderFg: Int
    ) {
        backgroundColor = background
        textPrimaryColor = textPrimary
        textSecondaryColor = textSecondary
        accentBlue = accent
        accentGreen = green
        accentRed = red
        inputBackgroundColor = inputBg
        sectionHeaderBackground = sectionHeaderBg
        sectionHeaderForeground = sectionHeaderFg
        setBackgroundColor(backgroundColor)
        branchInfoView.setTextColor(textPrimaryColor)
        pullButton.setTextColor(accentBlue)
        pushButton.setTextColor(accentBlue)
        stagedSectionLabel.setBackgroundColor(sectionHeaderBackground)
        stagedSectionLabel.setTextColor(sectionHeaderForeground)
        changesSectionLabel.setBackgroundColor(sectionHeaderBackground)
        changesSectionLabel.setTextColor(sectionHeaderForeground)
        commitMessageInput.setTextColor(textPrimaryColor)
        commitMessageInput.setHintTextColor(textSecondaryColor)
        commitMessageInput.setBackgroundColor(inputBackgroundColor)
        commitButton.setBackgroundColor(accentBlue)
        diffView.setTextColor(textPrimaryColor)
        stagedAdapter.notifyDataSetChanged()
        changesAdapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class GitStatusAdapter(
        private val files: List<GitFileStatus>,
        private val isStaged: Boolean
    ) : RecyclerView.Adapter<GitStatusAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(GitStatusItemView(parent.context))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(files[position])
        }

        override fun getItemCount() = files.size

        inner class ViewHolder(private val view: GitStatusItemView) : RecyclerView.ViewHolder(view) {
            fun bind(file: GitFileStatus) {
                view.setFile(file, isStaged)
                view.setOnClickListener {
                    onFileStatusClickListener?.invoke(file)
                }
                view.setStageActionListener {
                    if (isStaged) {
                        onUnstageListener?.invoke(file)
                    } else {
                        onStageListener?.invoke(file)
                    }
                }
            }
        }
    }

    inner class GitStatusItemView(context: Context) : LinearLayout(context) {

        private val statusIcon: TextView
        private val fileNameView: TextView
        private val actionButton: TextView

        private var stageActionListener: (() -> Unit)? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))

            statusIcon = TextView(context).apply {
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, dpToPx(8), 0)
            }
            fileNameView = TextView(context).apply {
                setTextColor(textPrimaryColor)
                textSize = 13f
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            actionButton = TextView(context).apply {
                textSize = 12f
                setPadding(dpToPx(8), 0, 0, 0)
            }

            addView(statusIcon)
            addView(fileNameView)
            addView(actionButton)
        }

        fun setFile(file: GitFileStatus, isStaged: Boolean) {
            val fileName = file.filePath.substringAfterLast('/')
            fileNameView.text = fileName

            statusIcon.text = file.status
            statusIcon.setTextColor(when (file.status) {
                "M" -> accentYellow
                "A" -> accentGreen
                "D" -> accentRed
                "??" -> accentRed
                "R" -> accentBlue
                else -> textSecondaryColor
            })

            actionButton.text = if (isStaged) "\u2212" else "+"
            actionButton.setTextColor(if (isStaged) accentRed else accentGreen)
            actionButton.setOnClickListener {
                stageActionListener?.invoke()
            }
        }

        fun setStageActionListener(listener: () -> Unit) {
            stageActionListener = listener
        }
    }
}