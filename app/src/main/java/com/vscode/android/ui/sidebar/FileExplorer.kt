package com.vscode.android.ui.sidebar

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

data class TreeItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val depth: Int,
    val children: MutableList<TreeItem> = mutableListOf(),
    var isExpanded: Boolean = false
)

class FileExplorer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val openFoldersContainer: LinearLayout
    private val openFoldersLabel: TextView
    private val fileTreeRecyclerView: RecyclerView
    private val treeAdapter: TreeAdapter
    private val collapseAllButton: TextView

    private val rootItems = mutableListOf<TreeItem>()

    private var onFileOpenListener: ((String) -> Unit)? = null
    private var onFileCreateListener: ((String, Boolean) -> Unit)? = null
    private var onFileDeleteListener: ((String) -> Unit)? = null
    private var onFileRenameListener: ((String, String) -> Unit)? = null
    private var onFolderOpenListener: (() -> Unit)? = null
    private var onFolderCloseListener: ((String) -> Unit)? = null

    @ColorInt
    private var backgroundColor: Int = 0xFF252526.toInt()

    @ColorInt
    private var textPrimaryColor: Int = 0xFFCCCCCC.toInt()

    @ColorInt
    private var textSecondaryColor: Int = 0xFF969696.toInt()

    @ColorInt
    private var sectionHeaderBackground: Int = 0x80808033.toInt()

    @ColorInt
    private var sectionHeaderForeground: Int = 0xFFBBBBBB.toInt()

    @ColorInt
    private var listActiveBackground: Int = 0xFF37373D.toInt()

    @ColorInt
    private var accentBlue: Int = 0xFF007ACC.toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)

        openFoldersLabel = TextView(context).apply {
            text = "OPEN EDITORS"
            setTextColor(sectionHeaderForeground)
            setBackgroundColor(sectionHeaderBackground)
            textSize = 11f
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        openFoldersContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        collapseAllButton = TextView(context).apply {
            text = "Collapse All"
            setTextColor(accentBlue)
            textSize = 12f
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                collapseAll()
            }
        }

        treeAdapter = TreeAdapter()
        fileTreeRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = treeAdapter
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        addView(openFoldersLabel)
        addView(openFoldersContainer)
        addView(collapseAllButton)
        addView(fileTreeRecyclerView)
    }

    fun setRootFolder(folderPath: String) {
        rootItems.clear()
        try {
            val rootDir = File(folderPath)
            if (rootDir.exists() && rootDir.isDirectory) {
                val rootItem = buildTreeItem(rootDir, 0)
                rootItem.isExpanded = true
                rootItems.add(rootItem)
            }
        } catch (_: Exception) {
            // Ignore file system errors
        }
        treeAdapter.notifyDataSetChanged()
    }

    fun addOpenFolder(folderName: String, folderPath: String) {
        val existingView = openFoldersContainer.findViewWithTag<View>(folderPath)
        if (existingView != null) return

        val folderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(2), dpToPx(4), dpToPx(2))
            tag = folderPath
        }

        val folderIcon = TextView(context).apply {
            text = "\uD83D\uDCC1"
            textSize = 14f
            setPadding(0, 0, dpToPx(8), 0)
        }
        val folderNameView = TextView(context).apply {
            text = folderName
            setTextColor(textPrimaryColor)
            textSize = 13f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeButton = TextView(context).apply {
            text = "\u2715"
            setTextColor(textSecondaryColor)
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener {
                openFoldersContainer.removeView(folderRow)
                onFolderCloseListener?.invoke(folderPath)
            }
        }

        folderRow.addView(folderIcon)
        folderRow.addView(folderNameView)
        folderRow.addView(closeButton)
        openFoldersContainer.addView(folderRow)
    }

    fun removeOpenFolder(folderPath: String) {
        val view = openFoldersContainer.findViewWithTag<View>(folderPath)
        if (view != null) {
            openFoldersContainer.removeView(view)
        }
    }

    fun collapseAll() {
        fun collapse(item: TreeItem) {
            item.isExpanded = false
            for (child in item.children) {
                collapse(child)
            }
        }
        for (root in rootItems) {
            collapse(root)
        }
        treeAdapter.notifyDataSetChanged()
    }

    fun expandAll() {
        fun expand(item: TreeItem) {
            item.isExpanded = true
            for (child in item.children) {
                expand(child)
            }
        }
        for (root in rootItems) {
            expand(root)
        }
        treeAdapter.notifyDataSetChanged()
    }

    fun setOnFileOpenListener(listener: (String) -> Unit) {
        onFileOpenListener = listener
    }

    fun setOnFileCreateListener(listener: (String, Boolean) -> Unit) {
        onFileCreateListener = listener
    }

    fun setOnFileDeleteListener(listener: (String) -> Unit) {
        onFileDeleteListener = listener
    }

    fun setOnFileRenameListener(listener: (String, String) -> Unit) {
        onFileRenameListener = listener
    }

    fun setOnFolderOpenListener(listener: () -> Unit) {
        onFolderOpenListener = listener
    }

    fun setOnFolderCloseListener(listener: (String) -> Unit) {
        onFolderCloseListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int,
        @ColorInt sectionHeaderBg: Int,
        @ColorInt sectionHeaderFg: Int,
        @ColorInt listActiveBg: Int,
        @ColorInt accent: Int
    ) {
        backgroundColor = background
        textPrimaryColor = textPrimary
        textSecondaryColor = textSecondary
        sectionHeaderBackground = sectionHeaderBg
        sectionHeaderForeground = sectionHeaderFg
        listActiveBackground = listActiveBg
        accentBlue = accent
        setBackgroundColor(backgroundColor)
        openFoldersLabel.setBackgroundColor(sectionHeaderBackground)
        openFoldersLabel.setTextColor(sectionHeaderForeground)
        collapseAllButton.setTextColor(accentBlue)
        treeAdapter.notifyDataSetChanged()
    }

    private fun buildTreeItem(file: File, depth: Int): TreeItem {
        val item = TreeItem(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            depth = depth
        )
        if (file.isDirectory) {
            try {
                val children = file.listFiles()?.sortedWith(
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                )
                children?.forEach { child ->
                    if (!child.isHidden && child.name != ".git" && child.name != "node_modules") {
                        item.children.add(buildTreeItem(child, depth + 1))
                    }
                }
            } catch (_: Exception) {
                // Ignore file system errors
            }
        }
        return item
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
            else -> {
                if (fileName.contains('.')) "\uD83D\uDCC4" else "\uD83D\uDCC1"
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class TreeAdapter : RecyclerView.Adapter<TreeAdapter.ViewHolder>() {

        private val flatItems = mutableListOf<TreeItem>()

        init {
            rebuildFlatList()
        }

        private fun rebuildFlatList() {
            flatItems.clear()
            for (root in rootItems) {
                addFlatItems(root)
            }
        }

        private fun addFlatItems(item: TreeItem) {
            flatItems.add(item)
            if (item.isDirectory && item.isExpanded) {
                for (child in item.children) {
                    addFlatItems(child)
                }
            }
        }

        fun toggleItem(item: TreeItem) {
            if (item.isDirectory) {
                item.isExpanded = !item.isExpanded
                rebuildFlatList()
                notifyDataSetChanged()
            }
        }

        fun getItemAtPosition(position: Int): TreeItem? {
            return if (position in flatItems.indices) flatItems[position] else null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(FileTreeItemView(parent.context))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = flatItems[position]
            holder.bind(item)
        }

        override fun getItemCount() = flatItems.size

        inner class ViewHolder(private val view: FileTreeItemView) : RecyclerView.ViewHolder(view) {
            fun bind(item: TreeItem) {
                view.setItem(item)
                view.setOnClickListener {
                    if (item.isDirectory) {
                        toggleItem(item)
                    } else {
                        onFileOpenListener?.invoke(item.path)
                    }
                }
                view.setOnLongClickListener {
                    showContextMenu(item, view)
                    true
                }
            }
        }
    }

    private fun showContextMenu(item: TreeItem, anchorView: View) {
        val popup = PopupMenu(context, anchorView, Gravity.START)
        popup.menu.add("New File").setOnMenuItemClickListener {
            onFileCreateListener?.invoke(item.path, false)
            true
        }
        popup.menu.add("New Folder").setOnMenuItemClickListener {
            onFileCreateListener?.invoke(item.path, true)
            true
        }
        popup.menu.add("Rename").setOnMenuItemClickListener {
            showRenameDialog(item)
            true
        }
        popup.menu.add("Delete").setOnMenuItemClickListener {
            showDeleteDialog(item)
            true
        }
        popup.menu.add("Copy Path").setOnMenuItemClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", item.path))
            true
        }
        popup.show()
    }

    private fun showRenameDialog(item: TreeItem) {
        val builder = android.app.AlertDialog.Builder(context)
        val input = android.widget.EditText(context).apply {
            setText(item.name)
            setTextColor(textPrimaryColor)
            setBackgroundColor(0xFF3C3C3C.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }
        builder.setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    onFileRenameListener?.invoke(item.path, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(item: TreeItem) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete \"${item.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                onFileDeleteListener?.invoke(item.path)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class FileTreeItemView(context: Context) : LinearLayout(context) {

        private val indentView: View
        private val expandIcon: TextView
        private val fileIcon: TextView
        private val fileNameView: TextView

        private var currentItem: TreeItem? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(2), dpToPx(8), dpToPx(2))

            indentView = View(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT)
            }
            expandIcon = TextView(context).apply {
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(dpToPx(16), LayoutParams.WRAP_CONTENT)
            }
            fileIcon = TextView(context).apply {
                textSize = 14f
                setPadding(dpToPx(4), 0, dpToPx(8), 0)
            }
            fileNameView = TextView(context).apply {
                textSize = 13f
                setTextColor(textPrimaryColor)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            addView(indentView)
            addView(expandIcon)
            addView(fileIcon)
            addView(fileNameView)
        }

        fun setItem(item: TreeItem) {
            currentItem = item
            val indentWidth = item.depth * dpToPx(INDENT_PER_LEVEL_DP)
            indentView.layoutParams = LayoutParams(indentWidth, LayoutParams.MATCH_PARENT)

            if (item.isDirectory) {
                expandIcon.text = if (item.isExpanded) "\u25BC" else "\u25B6"
                expandIcon.setTextColor(textSecondaryColor)
                expandIcon.visibility = VISIBLE
                fileIcon.text = if (item.isExpanded) "\uD83D\uDCC2" else "\uD83D\uDCC1"
            } else {
                expandIcon.visibility = INVISIBLE
                fileIcon.text = getFileIcon(item.name)
            }

            fileNameView.text = item.name
        }
    }

    companion object {
        private const val INDENT_PER_LEVEL_DP = 16
    }
}