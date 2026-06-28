package com.vscode.android.ui.activitybar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt

class ActivityBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class ActivityBarItem(val icon: String, val label: String) {
        EXPLORER("\uD83D\uDCC1", "Explorer"),
        SEARCH("\uD83D\uDD0D", "Search"),
        SOURCE_CONTROL("\u2394", "Source Control"),
        EXTENSIONS("\u2B1A", "Extensions"),
        DEBUG("\u25B6\u25A0", "Debug"),
        SETTINGS("\u2699", "Settings")
    }

    private val items = mutableListOf<ActivityBarButton>()
    private var activeItem: ActivityBarItem = ActivityBarItem.EXPLORER

    @ColorInt
    private var activeForegroundColor: Int = 0xFFFFFFFF.toInt()

    @ColorInt
    private var inactiveForegroundColor: Int = 0xFF858585.toInt()

    @ColorInt
    private var backgroundColor: Int = 0xFF333333.toInt()

    @ColorInt
    private var activeBorderColor: Int = 0xFFFFFFFF.toInt()

    @ColorInt
    private var badgeBackgroundColor: Int = 0xFF007ACC.toInt()

    @ColorInt
    private var badgeForegroundColor: Int = 0xFFFFFFFF.toInt()

    private var onItemClickListener: ((ActivityBarItem) -> Unit)? = null

    private val iconItems = listOf(
        ActivityBarItem.EXPLORER,
        ActivityBarItem.SEARCH,
        ActivityBarItem.SOURCE_CONTROL,
        ActivityBarItem.EXTENSIONS,
        ActivityBarItem.DEBUG
    )

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(
            dpToPx(ACTIVITY_BAR_WIDTH_DP),
            LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(backgroundColor)
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        createButtons()
    }

    private fun createButtons() {
        for (item in iconItems) {
            val button = ActivityBarButton(context, item)
            button.setOnClickListener {
                setActiveItem(item)
                onItemClickListener?.invoke(item)
            }
            items.add(button)
            addView(button)
        }

        addView(View(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        })

        val settingsButton = ActivityBarButton(context, ActivityBarItem.SETTINGS)
        settingsButton.setOnClickListener {
            onItemClickListener?.invoke(ActivityBarItem.SETTINGS)
        }
        items.add(settingsButton)
        addView(settingsButton)

        setActiveItem(ActivityBarItem.EXPLORER)
    }

    fun setActiveItem(item: ActivityBarItem) {
        activeItem = item
        for (button in items) {
            button.setActive(button.item == item)
        }
    }

    fun getActiveItem(): ActivityBarItem = activeItem

    fun setBadge(item: ActivityBarItem, count: Int) {
        for (button in items) {
            if (button.item == item) {
                button.setBadge(count)
                break
            }
        }
    }

    fun clearBadge(item: ActivityBarItem) {
        setBadge(item, 0)
    }

    fun setOnItemClickListener(listener: (ActivityBarItem) -> Unit) {
        onItemClickListener = listener
    }

    fun setColors(
        @ColorInt background: Int,
        @ColorInt activeForeground: Int,
        @ColorInt inactiveForeground: Int,
        @ColorInt activeBorder: Int,
        @ColorInt badgeBackground: Int,
        @ColorInt badgeForeground: Int
    ) {
        backgroundColor = background
        activeForegroundColor = activeForeground
        inactiveForegroundColor = inactiveForeground
        activeBorderColor = activeBorder
        badgeBackgroundColor = badgeBackground
        badgeForegroundColor = badgeForeground
        setBackgroundColor(backgroundColor)
        for (button in items) {
            button.updateColors()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class ActivityBarButton(
        context: Context,
        val item: ActivityBarItem
    ) : LinearLayout(context) {

        private val iconView: TextView
        private val badgeView: TextView
        private val activeIndicator: View
        private var isActive = false
        private var badgeCount = 0

        init {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(ACTIVITY_BAR_BUTTON_SIZE_DP)
            )
            gravity = Gravity.CENTER

            activeIndicator = View(context).apply {
                layoutParams = LayoutParams(
                    dpToPx(ACTIVE_INDICATOR_WIDTH_DP),
                    LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            iconView = TextView(context).apply {
                text = item.icon
                textSize = 20f
                typeface = Typeface.DEFAULT
                gravity = Gravity.CENTER
                setTextColor(inactiveForegroundColor)
                layoutParams = LayoutParams(
                    dpToPx(ICON_SIZE_DP),
                    LayoutParams.WRAP_CONTENT
                )
            }

            badgeView = TextView(context).apply {
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(badgeForegroundColor)
                visibility = GONE
                layoutParams = LayoutParams(
                    dpToPx(16),
                    dpToPx(16)
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                }
            }

            addView(activeIndicator)
            addView(iconView)
            addView(badgeView)
        }

        fun setActive(active: Boolean) {
            isActive = active
            iconView.setTextColor(
                if (active) activeForegroundColor else inactiveForegroundColor
            )
            activeIndicator.setBackgroundColor(
                if (active) activeBorderColor else android.graphics.Color.TRANSPARENT
            )
        }

        fun setBadge(count: Int) {
            badgeCount = count
            if (count > 0) {
                badgeView.text = if (count > 99) "99+" else count.toString()
                badgeView.visibility = VISIBLE
                badgeView.setBackgroundColor(badgeBackgroundColor)
            } else {
                badgeView.visibility = GONE
            }
        }

        fun updateColors() {
            iconView.setTextColor(
                if (isActive) activeForegroundColor else inactiveForegroundColor
            )
            activeIndicator.setBackgroundColor(
                if (isActive) activeBorderColor else android.graphics.Color.TRANSPARENT
            )
            if (badgeCount > 0) {
                badgeView.setBackgroundColor(badgeBackgroundColor)
                badgeView.setTextColor(badgeForegroundColor)
            }
        }
    }

    companion object {
        private const val ACTIVITY_BAR_WIDTH_DP = 48
        private const val ACTIVITY_BAR_BUTTON_SIZE_DP = 48
        private const val ACTIVE_INDICATOR_WIDTH_DP = 2
        private const val ICON_SIZE_DP = 32
    }
}