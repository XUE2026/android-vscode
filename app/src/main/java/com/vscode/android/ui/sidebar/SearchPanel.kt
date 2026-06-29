package com.vscode.android.ui.sidebar

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class SearchResult(
    val file: String,
    val line: Int,
    val column: Int,
    val text: String,
    val matchStart: Int,
    val matchEnd: Int
)

class SearchPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val searchInput: EditText
    private val replaceInput: EditText
    private val optionsRow: LinearLayout
    private val matchCaseCheckbox: CheckBox
    private val regexCheckbox: CheckBox
    private val wholeWordCheckbox: CheckBox
    private val replaceToggleButton: TextView
    private val replaceAllButton: TextView
    private val replaceOneButton: TextView
    private val replaceRow: LinearLayout
    private val includeFilterInput: EditText
    private val excludeFilterInput: EditText
    private val filterRow: LinearLayout
    private val resultsLabel: TextView
    private val resultsRecyclerView: RecyclerView
    private val resultsAdapter: SearchResultsAdapter
    private val progressIndicator: ProgressBar
    private val actionRow: LinearLayout

    private val searchResults = mutableListOf<SearchResult>()

    private var onSearchListener: ((String, Boolean, Boolean, Boolean) -> Unit)? = null
    private var onReplaceListener: ((String, String, Boolean, Boolean, Boolean) -> Unit)? = null
    private var onReplaceAllListener: ((String, String, Boolean, Boolean, Boolean) -> Unit)? = null
    private var onResultClickListener: ((SearchResult) -> Unit)? = null

    @ColorInt
    private var backgroundColor: Int = 0xFF252526.toInt()

    @ColorInt
    private var textPrimaryColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var textSecondaryColor: Int = 0xFF969696.toInt()

    @ColorInt
    private var inputBackgroundColor: Int = 0xFF3C3C3C.toInt()

    @ColorInt
    private var accentBlue: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var accentRed: Int = 0xFFF44747.toInt()

    private var isReplaceVisible = false
    private var isFilterVisible = false

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)
        setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))

        searchInput = EditText(context).apply {
            hint = "Search"
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            setBackgroundColor(inputBackgroundColor)
            textSize = 13f
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch()
                    true
                } else false
            }
        }

        replaceInput = EditText(context).apply {
            hint = "Replace"
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            setBackgroundColor(inputBackgroundColor)
            textSize = 13f
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            isSingleLine = true
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
        }

        replaceRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
        }

        replaceOneButton = TextView(context).apply {
            text = "Replace"
            setTextColor(accentBlue)
            textSize = 12f
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener { performReplaceOne() }
        }
        replaceAllButton = TextView(context).apply {
            text = "Replace All"
            setTextColor(accentBlue)
            textSize = 12f
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener { performReplaceAll() }
        }

        replaceRow.addView(replaceOneButton)
        replaceRow.addView(replaceAllButton)

        optionsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
        }

        matchCaseCheckbox = CheckBox(context).apply {
            text = "Aa"
            setTextColor(textSecondaryColor)
            textSize = 11f
            setPadding(0, 0, dpToPx(8), 0)
        }
        wholeWordCheckbox = CheckBox(context).apply {
            text = "W"
            setTextColor(textSecondaryColor)
            textSize = 11f
            setPadding(0, 0, dpToPx(8), 0)
        }
        regexCheckbox = CheckBox(context).apply {
            text = ".*"
            setTextColor(textSecondaryColor)
            textSize = 11f
            setPadding(0, 0, dpToPx(8), 0)
        }
        replaceToggleButton = TextView(context).apply {
            text = ">"
            setTextColor(textSecondaryColor)
            textSize = 14f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener { toggleReplace() }
        }

        optionsRow.addView(matchCaseCheckbox)
        optionsRow.addView(wholeWordCheckbox)
        optionsRow.addView(regexCheckbox)
        optionsRow.addView(View(context).apply {
            layoutParams = LayoutParams(0, 0, 1f)
        })
        optionsRow.addView(replaceToggleButton)

        filterRow = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
        }
        includeFilterInput = EditText(context).apply {
            hint = "Files to include (e.g. *.kt)"
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            setBackgroundColor(inputBackgroundColor)
            textSize = 12f
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            isSingleLine = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(2)
            }
        }
        excludeFilterInput = EditText(context).apply {
            hint = "Files to exclude (e.g. node_modules)"
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            setBackgroundColor(inputBackgroundColor)
            textSize = 12f
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            isSingleLine = true
        }
        filterRow.addView(includeFilterInput)
        filterRow.addView(excludeFilterInput)

        actionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
        }
        val filterToggleButton = TextView(context).apply {
            text = "Filter"
            setTextColor(textSecondaryColor)
            textSize = 12f
            setPadding(0, 0, dpToPx(12), 0)
            setOnClickListener { toggleFilter() }
        }
        actionRow.addView(filterToggleButton)

        resultsLabel = TextView(context).apply {
            setTextColor(textSecondaryColor)
            textSize = 12f
            setPadding(0, dpToPx(4), 0, dpToPx(4))
            visibility = GONE
        }

        progressIndicator = ProgressBar(context).apply {
            visibility = GONE
            layoutParams = LayoutParams(dpToPx(24), dpToPx(24)).apply {
                gravity = Gravity.CENTER
            }
        }

        resultsAdapter = SearchResultsAdapter()
        resultsRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = resultsAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        addView(searchInput)
        addView(replaceInput)
        addView(replaceRow)
        addView(optionsRow)
        addView(filterRow)
        addView(actionRow)
        addView(resultsLabel)
        addView(progressIndicator)
        addView(resultsRecyclerView)
    }

    fun setSearchResults(results: List<SearchResult>) {
        searchResults.clear()
        searchResults.addAll(results)
        resultsAdapter.notifyDataSetChanged()
        progressIndicator.visibility = GONE
        resultsLabel.visibility = VISIBLE
        val fileCount = results.map { it.file }.distinct().size
        resultsLabel.text = "${results.size} results in $fileCount files"
    }

    fun setSearchProgress(visible: Boolean) {
        progressIndicator.visibility = if (visible) VISIBLE else GONE
    }

    fun clearResults() {
        searchResults.clear()
        resultsAdapter.notifyDataSetChanged()
        resultsLabel.visibility = GONE
    }

    private fun toggleReplace() {
        isReplaceVisible = !isReplaceVisible
        replaceInput.visibility = if (isReplaceVisible) VISIBLE else GONE
        replaceRow.visibility = if (isReplaceVisible) VISIBLE else GONE
        replaceToggleButton.text = if (isReplaceVisible) "\u25BC" else ">"
    }

    private fun toggleFilter() {
        isFilterVisible = !isFilterVisible
        filterRow.visibility = if (isFilterVisible) VISIBLE else GONE
    }

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isNotEmpty()) {
            onSearchListener?.invoke(
                query,
                matchCaseCheckbox.isChecked,
                wholeWordCheckbox.isChecked,
                regexCheckbox.isChecked
            )
        }
    }

    private fun performReplaceOne() {
        val query = searchInput.text.toString().trim()
        val replacement = replaceInput.text.toString()
        if (query.isNotEmpty()) {
            onReplaceListener?.invoke(
                query,
                replacement,
                matchCaseCheckbox.isChecked,
                wholeWordCheckbox.isChecked,
                regexCheckbox.isChecked
            )
        }
    }

    private fun performReplaceAll() {
        val query = searchInput.text.toString().trim()
        val replacement = replaceInput.text.toString()
        if (query.isNotEmpty()) {
            onReplaceAllListener?.invoke(
                query,
                replacement,
                matchCaseCheckbox.isChecked,
                wholeWordCheckbox.isChecked,
                regexCheckbox.isChecked
            )
        }
    }

    fun getSearchQuery(): String = searchInput.text.toString().trim()
    fun getReplaceText(): String = replaceInput.text.toString()
    fun getIncludeFilter(): String = includeFilterInput.text.toString().trim()
    fun getExcludeFilter(): String = excludeFilterInput.text.toString().trim()

    fun setOnSearchListener(listener: (String, Boolean, Boolean, Boolean) -> Unit) {
        onSearchListener = listener
    }

    fun setOnReplaceListener(listener: (String, String, Boolean, Boolean, Boolean) -> Unit) {
        onReplaceListener = listener
    }

    fun setOnReplaceAllListener(listener: (String, String, Boolean, Boolean, Boolean) -> Unit) {
        onReplaceAllListener = listener
    }

    fun setOnResultClickListener(listener: (SearchResult) -> Unit) {
        onResultClickListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int,
        @ColorInt inputBg: Int,
        @ColorInt accent: Int
    ) {
        backgroundColor = background
        textPrimaryColor = textPrimary
        textSecondaryColor = textSecondary
        inputBackgroundColor = inputBg
        accentBlue = accent
        setBackgroundColor(backgroundColor)
        searchInput.setTextColor(textPrimaryColor)
        searchInput.setHintTextColor(textSecondaryColor)
        searchInput.setBackgroundColor(inputBackgroundColor)
        replaceInput.setTextColor(textPrimaryColor)
        replaceInput.setHintTextColor(textSecondaryColor)
        replaceInput.setBackgroundColor(inputBackgroundColor)
        matchCaseCheckbox.setTextColor(textSecondaryColor)
        wholeWordCheckbox.setTextColor(textSecondaryColor)
        regexCheckbox.setTextColor(textSecondaryColor)
        replaceToggleButton.setTextColor(textSecondaryColor)
        replaceOneButton.setTextColor(accentBlue)
        replaceAllButton.setTextColor(accentBlue)
        includeFilterInput.setTextColor(textPrimaryColor)
        includeFilterInput.setHintTextColor(textSecondaryColor)
        includeFilterInput.setBackgroundColor(inputBackgroundColor)
        excludeFilterInput.setTextColor(textPrimaryColor)
        excludeFilterInput.setHintTextColor(textSecondaryColor)
        excludeFilterInput.setBackgroundColor(inputBackgroundColor)
        resultsLabel.setTextColor(textSecondaryColor)
        resultsAdapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class SearchResultsAdapter : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(SearchResultItemView(parent.context))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = searchResults[position]
            holder.bind(result)
        }

        override fun getItemCount() = searchResults.size

        inner class ViewHolder(private val view: SearchResultItemView) : RecyclerView.ViewHolder(view) {
            fun bind(result: SearchResult) {
                view.setResult(result)
                view.setOnClickListener {
                    onResultClickListener?.invoke(result)
                }
            }
        }
    }

    inner class SearchResultItemView(context: Context) : LinearLayout(context) {

        private val fileView: TextView
        private val lineView: TextView
        private val contentView: TextView

        private var currentResult: SearchResult? = null

        init {
            orientation = VERTICAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))

            val headerRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
            }
            fileView = TextView(context).apply {
                setTextColor(accentBlue)
                textSize = 12f
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            lineView = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 12f
                typeface = Typeface.MONOSPACE
            }
            headerRow.addView(fileView)
            headerRow.addView(lineView)

            contentView = TextView(context).apply {
                setTextColor(textPrimaryColor)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(0, dpToPx(2), 0, 0)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            addView(headerRow)
            addView(contentView)
        }

        fun setResult(result: SearchResult) {
            currentResult = result
            val fileName = result.file.substringAfterLast('/')
            fileView.text = fileName
            lineView.text = "  ${result.line}:${result.column}"
            val displayText = result.text.trim()
            contentView.text = displayText.ifEmpty { result.text }
        }
    }

    companion object {
        private const val TAG = "SearchPanel"
    }
}