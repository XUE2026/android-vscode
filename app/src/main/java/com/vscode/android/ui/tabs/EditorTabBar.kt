package com.vscode.android.ui.tabs

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.ColorInt

data class EditorTab(
    val id: String,
    val title: String,
    val icon: String = "",
    val isModified: Boolean = false,
    val isActive: Boolean = false,
    var filePath: String? = null
)

class EditorTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val tabsContainer: LinearLayout
    private val overflowButton: TextView

    private val tabs = mutableListOf<EditorTab>()
    private val tabViews = mutableListOf<TabView>()
    private var activeTabId: String? = null

    private var onTabClickListener: ((EditorTab) -> Unit)? = null
    private var onTabCloseListener: ((EditorTab) -> Unit)? = null
    private var onTabOverflowListener: ((List<EditorTab>) -> Unit)? = null

    @ColorInt
    private var backgroundColor: Int = 0xFF2D2D2D.toInt()

    @ColorInt
    private var tabActiveBackground: Int = 0xFF1E1E1E.toInt()

    @ColorInt
    private var tabInactiveBackground: Int = 0xFF2D2D2D.toInt()

    @ColorInt
    private var tabActiveForeground: Int = 0xFFFFFFFF.toInt()

    @ColorInt
    private var tabInactiveForeground: Int = 0xFF969696.toInt()

    @ColorInt
    private var tabActiveBorderTop: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var tabBorder: Int = 0xFF252526.toInt()

    @ColorInt
    private var accentBlue: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var accentYellow: Int = 0xFFDCDCAA.toInt()

    private var tabMinWidth: Int = 0
    private var tabPadding: Int = 0

    init {
        setBackgroundColor(backgroundColor)
        isHorizontalScrollBarEnabled = false
        isFillViewport = true

        tabMinWidth = dpToPx(MIN_TAB_WIDTH_DP)
        tabPadding = dpToPx(12)

        tabsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        overflowButton = TextView(context).apply {
            text = "\u22EF"
            setTextColor(tabInactiveForeground)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener { showOverflowMenu() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val wrapper = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        wrapper.addView(tabsContainer)
        wrapper.addView(overflowButton)

        addView(wrapper)
    }

    fun addTab(tab: EditorTab) {
        tabs.add(tab)
        val tabView = TabView(context, tab)
        tabView.setOnClickListener {
            onTabClickListener?.invoke(tab)
        }
        tabView.setOnCloseListener {
            onTabCloseListener?.invoke(tab)
        }
        tabViews.add(tabView)
        tabsContainer.addView(tabView)
        updateTabWidths()
    }

    fun removeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        tabs.removeAt(index)
        val tabView = tabViews.removeAt(index)
        tabsContainer.removeView(tabView)
        updateTabWidths()
    }

    fun setActiveTab(tabId: String) {
        activeTabId = tabId
        for (i in tabs.indices) {
            tabViews[i].setActive(tabs[i].id == tabId)
        }
        scrollToTab(tabId)
    }

    fun setTabModified(tabId: String, modified: Boolean) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        tabs[index] = tabs[index].copy(isModified = modified)
        tabViews[index].setModified(modified)
    }

    fun updateTab(tab: EditorTab) {
        val index = tabs.indexOfFirst { it.id == tab.id }
        if (index < 0) return
        tabs[index] = tab
        tabViews[index].updateTab(tab)
        updateTabWidths()
    }

    fun getTabs(): List<EditorTab> = tabs.toList()

    fun getActiveTab(): EditorTab? {
        return activeTabId?.let { id -> tabs.find { it.id == id } }
    }

    fun getTabCount(): Int = tabs.size

    fun clearTabs() {
        tabs.clear()
        tabViews.clear()
        tabsContainer.removeAllViews()
        activeTabId = null
    }

    fun scrollToTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val tabView = tabViews[index]
        post {
            val scrollX = tabView.left - (width - tabView.width) / 2
            smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(context, overflowButton, Gravity.END)
        for (tab in tabs) {
            popup.menu.add(tab.title).setOnMenuItemClickListener {
                onTabClickListener?.invoke(tab)
                true
            }
        }
        if (tabs.isNotEmpty()) {
            popup.menu.add("Close All").setOnMenuItemClickListener {
                val allTabs = tabs.toList()
                allTabs.forEach { onTabCloseListener?.invoke(it) }
                true
            }
            popup.menu.add("Close Others").setOnMenuItemClickListener {
                val activeTab = getActiveTab()
                val toClose = tabs.filter { it.id != activeTab?.id }
                toClose.forEach { onTabCloseListener?.invoke(it) }
                true
            }
            popup.menu.add("Close to the Right").setOnMenuItemClickListener {
                val activeTab = getActiveTab()
                if (activeTab != null) {
                    val activeIndex = tabs.indexOfFirst { it.id == activeTab.id }
                    val toClose = tabs.drop(activeIndex + 1)
                    toClose.forEach { onTabCloseListener?.invoke(it) }
                }
                true
            }
            popup.menu.add("Close Saved").setOnMenuItemClickListener {
                val toClose = tabs.filter { !it.isModified }
                toClose.forEach { onTabCloseListener?.invoke(it) }
                true
            }
        }
        popup.show()
    }

    private fun updateTabWidths() {
        if (tabs.isEmpty()) return
        val availableWidth = width - dpToPx(40)
        val tabWidth = (availableWidth / tabs.size).coerceAtLeast(tabMinWidth)
        for (tabView in tabViews) {
            val params = tabView.layoutParams as LinearLayout.LayoutParams
            params.width = tabWidth
            tabView.layoutParams = params
        }
    }

    fun setOnTabClickListener(listener: (EditorTab) -> Unit) {
        onTabClickListener = listener
    }

    fun setOnTabCloseListener(listener: (EditorTab) -> Unit) {
        onTabCloseListener = listener
    }

    fun setOnTabOverflowListener(listener: (List<EditorTab>) -> Unit) {
        onTabOverflowListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt activeBg: Int,
        @ColorInt inactiveBg: Int,
        @ColorInt activeFg: Int,
        @ColorInt inactiveFg: Int,
        @ColorInt activeBorder: Int,
        @ColorInt border: Int,
        @ColorInt accent: Int
    ) {
        backgroundColor = background
        tabActiveBackground = activeBg
        tabInactiveBackground = inactiveBg
        tabActiveForeground = activeFg
        tabInactiveForeground = inactiveFg
        tabActiveBorderTop = activeBorder
        tabBorder = border
        accentBlue = accent
        setBackgroundColor(backgroundColor)
        overflowButton.setTextColor(tabInactiveForeground)
        for (tabView in tabViews) {
            tabView.updateColors()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class TabView(context: Context, private var tab: EditorTab) : LinearLayout(context) {

        private val iconView: TextView
        private val titleView: TextView
        private val modifiedIndicator: TextView
        private val closeButton: TextView

        private var onCloseClickListener: (() -> Unit)? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(tabPadding, 0, dpToPx(4), 0)
            minimumWidth = tabMinWidth
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(tabInactiveBackground)

            iconView = TextView(context).apply {
                text = tab.icon
                textSize = 14f
                setPadding(0, 0, dpToPx(6), 0)
            }
            titleView = TextView(context).apply {
                text = tab.title
                setTextColor(tabInactiveForeground)
                textSize = 13f
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dpToPx(160)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            modifiedIndicator = TextView(context).apply {
                text = "\u25CF"
                setTextColor(accentYellow)
                textSize = 10f
                setPadding(dpToPx(4), 0, dpToPx(2), 0)
                visibility = if (tab.isModified) VISIBLE else GONE
            }
            closeButton = TextView(context).apply {
                text = "\u2715"
                setTextColor(tabInactiveForeground)
                textSize = 12f
                setPadding(dpToPx(4), 0, dpToPx(4), 0)
                setOnClickListener {
                    onCloseClickListener?.invoke()
                }
            }

            addView(iconView)
            addView(titleView)
            addView(modifiedIndicator)
            addView(closeButton)
        }

        fun setActive(active: Boolean) {
            tab = tab.copy(isActive = active)
            setBackgroundColor(if (active) tabActiveBackground else tabInactiveBackground)
            titleView.setTextColor(if (active) tabActiveForeground else tabInactiveForeground)
            closeButton.setTextColor(if (active) tabActiveForeground else tabInactiveForeground)
        }

        fun setModified(modified: Boolean) {
            tab = tab.copy(isModified = modified)
            modifiedIndicator.visibility = if (modified) VISIBLE else GONE
        }

        fun updateTab(newTab: EditorTab) {
            tab = newTab
            iconView.text = newTab.icon
            titleView.text = newTab.title
            modifiedIndicator.visibility = if (newTab.isModified) VISIBLE else GONE
            setActive(newTab.isActive)
        }

        fun updateColors() {
            setBackgroundColor(if (tab.isActive) tabActiveBackground else tabInactiveBackground)
            titleView.setTextColor(if (tab.isActive) tabActiveForeground else tabInactiveForeground)
            closeButton.setTextColor(if (tab.isActive) tabActiveForeground else tabInactiveForeground)
            modifiedIndicator.setTextColor(accentYellow)
        }

        fun setOnCloseListener(listener: () -> Unit) {
            onCloseClickListener = listener
        }
    }

    companion object {
        private const val MIN_TAB_WIDTH_DP = 120
    }
}