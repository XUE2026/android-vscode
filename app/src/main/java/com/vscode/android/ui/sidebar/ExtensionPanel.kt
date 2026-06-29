package com.vscode.android.ui.sidebar

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class ExtensionData(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    var enabled: Boolean,
    val builtIn: Boolean
)

class ExtensionPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val searchInput: EditText
    private val marketplaceLabel: TextView
    private val marketplaceRecyclerView: RecyclerView
    private val marketplaceAdapter: ExtensionAdapter

    private val installedLabel: TextView
    private val installedRecyclerView: RecyclerView
    private val installedAdapter: ExtensionAdapter

    private val detailView: ExtensionDetailView
    private val mainContent: LinearLayout

    private val installedExtensions = mutableListOf<ExtensionData>()
    private val marketplaceExtensions = mutableListOf<ExtensionData>()

    private var onInstallListener: ((ExtensionData) -> Unit)? = null
    private var onUninstallListener: ((ExtensionData) -> Unit)? = null
    private var onEnableToggleListener: ((ExtensionData, Boolean) -> Unit)? = null
    private var onSearchListener: ((String) -> Unit)? = null

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
    private var accentGreen: Int = 0xFF6A9955.toInt()

    @ColorInt
    private var accentRed: Int = 0xFFF44747.toInt()

    @ColorInt
    private var sectionHeaderBackground: Int = 0x80808033.toInt()

    @ColorInt
    private var sectionHeaderForeground: Int = 0xFFBBBBBB.toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)

        searchInput = EditText(context).apply {
            hint = "Search Extensions in Marketplace"
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            setBackgroundColor(inputBackgroundColor)
            textSize = 13f
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            isSingleLine = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(4))
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onSearchListener?.invoke(text.toString().trim())
                    true
                } else false
            }
        }

        marketplaceLabel = createSectionLabel("MARKETPLACE")
        marketplaceAdapter = ExtensionAdapter(marketplaceExtensions, false)
        marketplaceRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = marketplaceAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        installedLabel = createSectionLabel("INSTALLED")
        installedAdapter = ExtensionAdapter(installedExtensions, true)
        installedRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = installedAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        mainContent = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        mainContent.addView(installedLabel)
        mainContent.addView(installedRecyclerView)
        mainContent.addView(marketplaceLabel)
        mainContent.addView(marketplaceRecyclerView)

        detailView = ExtensionDetailView(context).apply {
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        addView(searchInput)
        addView(mainContent)
        addView(detailView)

        loadMarketplaceData()
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

    private fun loadMarketplaceData() {
        marketplaceExtensions.clear()
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.python",
            name = "Python",
            description = "Python language support with IntelliSense, linting, debugging, and more.",
            version = "2024.1.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.java",
            name = "Java",
            description = "Java language support with debugging, testing, and project management.",
            version = "1.2.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.kotlin",
            name = "Kotlin",
            description = "Kotlin language support with IntelliSense, build tool integration.",
            version = "1.0.0",
            author = "JetBrains",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.javascript",
            name = "JavaScript",
            description = "JavaScript and TypeScript language support with IntelliSense.",
            version = "1.0.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.cpp",
            name = "C/C++",
            description = "C/C++ language support with IntelliSense, debugging, and code browsing.",
            version = "1.0.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.html",
            name = "HTML/CSS",
            description = "HTML, CSS, SCSS, and Less language support.",
            version = "1.0.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.git",
            name = "Git",
            description = "Git integration with source control, diff viewing, and more.",
            version = "1.0.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.markdown",
            name = "Markdown",
            description = "Markdown language support with preview, snippets, and formatting.",
            version = "1.0.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.terminal",
            name = "Terminal",
            description = "Integrated terminal with shell integration.",
            version = "1.0.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceExtensions.add(ExtensionData(
            id = "vscode.json",
            name = "JSON",
            description = "JSON language support with schema validation.",
            version = "1.0.0",
            author = "Microsoft",
            enabled = true,
            builtIn = true
        ))
        marketplaceAdapter.notifyDataSetChanged()
    }

    fun setInstalledExtensions(extensions: List<ExtensionData>) {
        installedExtensions.clear()
        installedExtensions.addAll(extensions)
        installedAdapter.notifyDataSetChanged()
    }

    fun addInstalledExtension(extension: ExtensionData) {
        installedExtensions.add(extension)
        installedAdapter.notifyDataSetChanged()
    }

    fun removeInstalledExtension(extensionId: String) {
        installedExtensions.removeAll { it.id == extensionId }
        installedAdapter.notifyDataSetChanged()
    }

    fun showExtensionDetail(extension: ExtensionData) {
        mainContent.visibility = GONE
        detailView.visibility = VISIBLE
        detailView.setExtension(extension)
        detailView.setOnBackListener {
            mainContent.visibility = VISIBLE
            detailView.visibility = GONE
        }
        detailView.setOnInstallToggleListener { ext ->
            if (installedExtensions.any { it.id == ext.id }) {
                onUninstallListener?.invoke(ext)
            } else {
                onInstallListener?.invoke(ext)
            }
        }
        detailView.setOnEnableToggleListener { ext, enabled ->
            onEnableToggleListener?.invoke(ext, enabled)
        }
    }

    fun setOnInstallListener(listener: (ExtensionData) -> Unit) {
        onInstallListener = listener
    }

    fun setOnUninstallListener(listener: (ExtensionData) -> Unit) {
        onUninstallListener = listener
    }

    fun setOnEnableToggleListener(listener: (ExtensionData, Boolean) -> Unit) {
        onEnableToggleListener = listener
    }

    fun setOnSearchListener(listener: (String) -> Unit) {
        onSearchListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int,
        @ColorInt inputBg: Int,
        @ColorInt accent: Int,
        @ColorInt green: Int,
        @ColorInt red: Int,
        @ColorInt sectionHeaderBg: Int,
        @ColorInt sectionHeaderFg: Int
    ) {
        backgroundColor = background
        textPrimaryColor = textPrimary
        textSecondaryColor = textSecondary
        inputBackgroundColor = inputBg
        accentBlue = accent
        accentGreen = green
        accentRed = red
        sectionHeaderBackground = sectionHeaderBg
        sectionHeaderForeground = sectionHeaderFg
        setBackgroundColor(backgroundColor)
        searchInput.setTextColor(textPrimaryColor)
        searchInput.setHintTextColor(textSecondaryColor)
        searchInput.setBackgroundColor(inputBackgroundColor)
        marketplaceLabel.setBackgroundColor(sectionHeaderBackground)
        marketplaceLabel.setTextColor(sectionHeaderForeground)
        installedLabel.setBackgroundColor(sectionHeaderBackground)
        installedLabel.setTextColor(sectionHeaderForeground)
        marketplaceAdapter.notifyDataSetChanged()
        installedAdapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class ExtensionAdapter(
        private val extensions: List<ExtensionData>,
        private val isInstalled: Boolean
    ) : RecyclerView.Adapter<ExtensionAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ExtensionItemView(parent.context))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(extensions[position])
        }

        override fun getItemCount() = extensions.size

        inner class ViewHolder(private val view: ExtensionItemView) : RecyclerView.ViewHolder(view) {
            fun bind(extension: ExtensionData) {
                view.setExtension(extension, isInstalled)
                view.setOnClickListener {
                    showExtensionDetail(extension)
                }
                view.setEnableToggleListener { enabled ->
                    onEnableToggleListener?.invoke(extension, enabled)
                }
            }
        }
    }

    inner class ExtensionItemView(context: Context) : LinearLayout(context) {

        private val iconView: TextView
        private val infoLayout: LinearLayout
        private val nameView: TextView
        private val descView: TextView
        private val authorView: TextView
        private val enableToggleView: TextView
        private val actionButton: TextView

        private var enableToggleListener: ((Boolean) -> Unit)? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))

            iconView = TextView(context).apply {
                text = "\u2B1A"
                textSize = 20f
                setTextColor(accentBlue)
                setPadding(0, 0, dpToPx(12), 0)
                gravity = Gravity.CENTER
            }
            infoLayout = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            nameView = TextView(context).apply {
                setTextColor(textPrimaryColor)
                textSize = 13f
            }
            descView = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 11f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            authorView = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 10f
            }
            infoLayout.addView(nameView)
            infoLayout.addView(descView)
            infoLayout.addView(authorView)

            enableToggleView = TextView(context).apply {
                textSize = 12f
                setPadding(dpToPx(8), 0, 0, 0)
            }
            actionButton = TextView(context).apply {
                textSize = 12f
                setPadding(dpToPx(8), 0, 0, 0)
            }

            addView(iconView)
            addView(infoLayout)
            addView(enableToggleView)
            addView(actionButton)
        }

        fun setExtension(extension: ExtensionData, isInstalled: Boolean) {
            nameView.text = "${extension.name} v${extension.version}"
            descView.text = extension.description
            authorView.text = extension.author

            if (extension.builtIn) {
                enableToggleView.visibility = GONE
                actionButton.visibility = GONE
            } else {
                enableToggleView.visibility = VISIBLE
                actionButton.visibility = VISIBLE
                enableToggleView.text = if (extension.enabled) "Disable" else "Enable"
                enableToggleView.setTextColor(if (extension.enabled) accentRed else accentGreen)
                enableToggleView.setOnClickListener {
                    val newEnabled = !extension.enabled
                    enableToggleListener?.invoke(newEnabled)
                }
                actionButton.text = if (isInstalled) "Uninstall" else "Install"
                actionButton.setTextColor(if (isInstalled) accentRed else accentGreen)
            }
        }

        fun setEnableToggleListener(listener: (Boolean) -> Unit) {
            enableToggleListener = listener
        }
    }

    inner class ExtensionDetailView(context: Context) : LinearLayout(context) {

        private val headerRow: LinearLayout
        private val backButton: TextView
        private val extensionName: TextView
        private val extensionId: TextView
        private val extensionDesc: TextView
        private val extensionVersion: TextView
        private val extensionAuthor: TextView
        private val installToggleButton: TextView
        private val enableToggleButton: TextView

        private var currentExtension: ExtensionData? = null
        private var isInstalled: Boolean = false
        private var onBackListener: (() -> Unit)? = null
        private var onInstallToggleListener: ((ExtensionData) -> Unit)? = null
        private var onEnableToggleListener: ((ExtensionData, Boolean) -> Unit)? = null

        init {
            orientation = VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))

            headerRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            backButton = TextView(context).apply {
                text = "\u2190"
                setTextColor(accentBlue)
                textSize = 18f
                setPadding(0, 0, dpToPx(12), 0)
                setOnClickListener { onBackListener?.invoke() }
            }
            extensionName = TextView(context).apply {
                setTextColor(textPrimaryColor)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(backButton)
            headerRow.addView(extensionName)

            extensionId = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 11f
                setPadding(0, dpToPx(4), 0, dpToPx(8))
            }
            extensionDesc = TextView(context).apply {
                setTextColor(textPrimaryColor)
                textSize = 13f
                setPadding(0, 0, 0, dpToPx(8))
            }
            extensionVersion = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 12f
                setPadding(0, 0, 0, dpToPx(4))
            }
            extensionAuthor = TextView(context).apply {
                setTextColor(textSecondaryColor)
                textSize = 12f
                setPadding(0, 0, 0, dpToPx(12))
            }

            installToggleButton = TextView(context).apply {
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(8)
                }
            }
            enableToggleButton = TextView(context).apply {
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }

            addView(headerRow)
            addView(extensionId)
            addView(extensionDesc)
            addView(extensionVersion)
            addView(extensionAuthor)
            addView(installToggleButton)
            addView(enableToggleButton)
        }

        fun setExtension(extension: ExtensionData) {
            currentExtension = extension
            isInstalled = installedExtensions.any { it.id == extension.id }
            extensionName.text = extension.name
            extensionId.text = extension.id
            extensionDesc.text = extension.description
            extensionVersion.text = "Version: ${extension.version}"
            extensionAuthor.text = "Author: ${extension.author}"

            if (extension.builtIn) {
                installToggleButton.visibility = GONE
                enableToggleButton.visibility = GONE
            } else {
                installToggleButton.visibility = VISIBLE
                enableToggleButton.visibility = VISIBLE
                installToggleButton.text = if (isInstalled) "Uninstall" else "Install"
                installToggleButton.setTextColor(if (isInstalled) accentRed else accentGreen)
                installToggleButton.setOnClickListener {
                    onInstallToggleListener?.invoke(extension)
                }
                enableToggleButton.text = if (extension.enabled) "Disable" else "Enable"
                enableToggleButton.setTextColor(if (extension.enabled) accentRed else accentGreen)
                enableToggleButton.setOnClickListener {
                    onEnableToggleListener?.invoke(extension, !extension.enabled)
                }
            }
        }

        fun setOnBackListener(listener: () -> Unit) {
            onBackListener = listener
        }

        fun setOnInstallToggleListener(listener: (ExtensionData) -> Unit) {
            onInstallToggleListener = listener
        }

        fun setOnEnableToggleListener(listener: (ExtensionData, Boolean) -> Unit) {
            onEnableToggleListener = listener
        }
    }
}