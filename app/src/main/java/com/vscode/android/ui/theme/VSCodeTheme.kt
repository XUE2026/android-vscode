package com.vscode.android.ui.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

class VSCodeTheme(private val context: Context) {

    enum class ThemeMode {
        DARK, LIGHT, HIGH_CONTRAST
    }

    private var currentMode: ThemeMode = ThemeMode.DARK
    private var currentColors: ThemeColors = ThemeColors.defaultDarkTheme

    private val onThemeChangedListeners = mutableListOf<(ThemeColors) -> Unit>()

    fun applyDarkTheme() {
        currentMode = ThemeMode.DARK
        currentColors = ThemeColors.defaultDarkTheme
        notifyThemeChanged()
    }

    fun applyLightTheme() {
        currentMode = ThemeMode.LIGHT
        currentColors = ThemeColors.lightTheme
        notifyThemeChanged()
    }

    fun applyHighContrastTheme() {
        currentMode = ThemeMode.HIGH_CONTRAST
        currentColors = ThemeColors.highContrastTheme
        notifyThemeChanged()
    }

    fun getCurrentThemeMode(): ThemeMode = currentMode

    fun getCurrentColors(): ThemeColors = currentColors

    fun addThemeChangedListener(listener: (ThemeColors) -> Unit) {
        onThemeChangedListeners.add(listener)
    }

    fun removeThemeChangedListener(listener: (ThemeColors) -> Unit) {
        onThemeChangedListeners.remove(listener)
    }

    private fun notifyThemeChanged() {
        onThemeChangedListeners.forEach { it(currentColors) }
    }

    fun setThemeMode(mode: ThemeMode) {
        when (mode) {
            ThemeMode.DARK -> applyDarkTheme()
            ThemeMode.LIGHT -> applyLightTheme()
            ThemeMode.HIGH_CONTRAST -> applyHighContrastTheme()
        }
    }

    @ColorInt fun getBackgroundColor(): Int = currentColors.background
    @ColorInt fun getForegroundColor(): Int = currentColors.foreground
    @ColorInt fun getSidebarBackground(): Int = currentColors.sidebarBackground
    @ColorInt fun getActivityBarBackground(): Int = currentColors.activityBarBackground
    @ColorInt fun getActivityBarActiveForeground(): Int = currentColors.activityBarActiveForeground
    @ColorInt fun getActivityBarInactiveForeground(): Int = currentColors.activityBarInactiveForeground
    @ColorInt fun getActivityBarBadgeBackground(): Int = currentColors.activityBarBadgeBackground
    @ColorInt fun getActivityBarBadgeForeground(): Int = currentColors.activityBarBadgeForeground
    @ColorInt fun getEditorBackground(): Int = currentColors.editorBackground
    @ColorInt fun getEditorForeground(): Int = currentColors.editorForeground
    @ColorInt fun getEditorLineHighlight(): Int = currentColors.editorLineHighlight
    @ColorInt fun getEditorSelectionBackground(): Int = currentColors.editorSelectionBackground
    @ColorInt fun getEditorLineNumber(): Int = currentColors.editorLineNumber
    @ColorInt fun getEditorLineNumberActive(): Int = currentColors.editorLineNumberActive
    @ColorInt fun getEditorCursor(): Int = currentColors.editorCursor
    @ColorInt fun getEditorGutterBackground(): Int = currentColors.editorGutterBackground
    @ColorInt fun getStatusBarBackground(): Int = currentColors.statusBarBackground
    @ColorInt fun getStatusBarForeground(): Int = currentColors.statusBarForeground
    @ColorInt fun getTabBackground(): Int = currentColors.tabBackground
    @ColorInt fun getTabActiveBackground(): Int = currentColors.tabActiveBackground
    @ColorInt fun getTabInactiveBackground(): Int = currentColors.tabInactiveBackground
    @ColorInt fun getTabActiveForeground(): Int = currentColors.tabActiveForeground
    @ColorInt fun getTabInactiveForeground(): Int = currentColors.tabInactiveForeground
    @ColorInt fun getPanelBackground(): Int = currentColors.panelBackground
    @ColorInt fun getPanelTitleActiveForeground(): Int = currentColors.panelTitleActiveForeground
    @ColorInt fun getPanelTitleInactiveForeground(): Int = currentColors.panelTitleInactiveForeground
    @ColorInt fun getTerminalBackground(): Int = currentColors.terminalBackground
    @ColorInt fun getTerminalForeground(): Int = currentColors.terminalForeground
    @ColorInt fun getTextPrimary(): Int = currentColors.textPrimary
    @ColorInt fun getTextSecondary(): Int = currentColors.textSecondary
    @ColorInt fun getTextDisabled(): Int = currentColors.textDisabled
    @ColorInt fun getTextLink(): Int = currentColors.textLink
    @ColorInt fun getAccentBlue(): Int = currentColors.accentBlue
    @ColorInt fun getAccentGreen(): Int = currentColors.accentGreen
    @ColorInt fun getAccentRed(): Int = currentColors.accentRed
    @ColorInt fun getAccentOrange(): Int = currentColors.accentOrange
    @ColorInt fun getInputBackground(): Int = currentColors.inputBackground
    @ColorInt fun getInputForeground(): Int = currentColors.inputForeground
    @ColorInt fun getInputBorder(): Int = currentColors.inputBorder
    @ColorInt fun getDivider(): Int = currentColors.divider
    @ColorInt fun getBorder(): Int = currentColors.border
    @ColorInt fun getFocusBorder(): Int = currentColors.focusBorder
    @ColorInt fun getErrorForeground(): Int = currentColors.errorForeground
    @ColorInt fun getWarningForeground(): Int = currentColors.warningForeground
    @ColorInt fun getInfoForeground(): Int = currentColors.infoForeground
    @ColorInt fun getSuccessForeground(): Int = currentColors.successForeground
    @ColorInt fun getButtonBackground(): Int = currentColors.buttonBackground
    @ColorInt fun getButtonForeground(): Int = currentColors.buttonForeground
    @ColorInt fun getDropdownBackground(): Int = currentColors.dropdownBackground
    @ColorInt fun getDropdownForeground(): Int = currentColors.dropdownForeground
    @ColorInt fun getMenuBackground(): Int = currentColors.menuBackground
    @ColorInt fun getMenuForeground(): Int = currentColors.menuForeground
    @ColorInt fun getMenuSelectionBackground(): Int = currentColors.menuSelectionBackground
    @ColorInt fun getScrollbarSliderBackground(): Int = currentColors.scrollbarSliderBackground
    @ColorInt fun getBadgeBackground(): Int = currentColors.badgeBackground
    @ColorInt fun getBadgeForeground(): Int = currentColors.badgeForeground
    @ColorInt fun getTitleBarBackground(): Int = currentColors.titleBarBackground
    @ColorInt fun getSidebarSectionHeaderBackground(): Int = currentColors.sidebarSectionHeaderBackground
    @ColorInt fun getSidebarSectionHeaderForeground(): Int = currentColors.sidebarSectionHeaderForeground
    @ColorInt fun getSidebarListActiveBackground(): Int = currentColors.sidebarListActiveBackground
    @ColorInt fun getSidebarListInactiveBackground(): Int = currentColors.sidebarListInactiveBackground

    @ColorInt fun getKeywordColor(): Int = currentColors.keyword
    @ColorInt fun getStringColor(): Int = currentColors.string
    @ColorInt fun getNumberColor(): Int = currentColors.number
    @ColorInt fun getCommentColor(): Int = currentColors.comment
    @ColorInt fun getTypeColor(): Int = currentColors.type
    @ColorInt fun getFunctionColor(): Int = currentColors.function
    @ColorInt fun getVariableColor(): Int = currentColors.variable
    @ColorInt fun getOperatorColor(): Int = currentColors.operator
    @ColorInt fun getConstantColor(): Int = currentColors.constant
    @ColorInt fun getClassNameColor(): Int = currentColors.className
    @ColorInt fun getParameterColor(): Int = currentColors.parameter
    @ColorInt fun getPropertyColor(): Int = currentColors.property
    @ColorInt fun getRegexColor(): Int = currentColors.regex
    @ColorInt fun getTagColor(): Int = currentColors.tag
    @ColorInt fun getAttributeNameColor(): Int = currentColors.attributeName
    @ColorInt fun getAttributeValueColor(): Int = currentColors.attributeValue

    fun applyToView(view: View, @ColorInt backgroundColor: Int) {
        view.setBackgroundColor(backgroundColor)
    }

    fun applyToTextView(textView: TextView, @ColorInt textColor: Int, @ColorInt backgroundColor: Int? = null) {
        textView.setTextColor(textColor)
        backgroundColor?.let { textView.setBackgroundColor(it) }
    }

    fun createBorderDrawable(
        @ColorInt borderColor: Int,
        @ColorInt backgroundColor: Int = android.graphics.Color.TRANSPARENT,
        borderWidthDp: Float = 1f,
        cornerRadiusDp: Float = 0f
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(backgroundColor)
            setStroke(
                (borderWidthDp * density).toInt(),
                borderColor
            )
            if (cornerRadiusDp > 0f) {
                cornerRadius = cornerRadiusDp * density
            }
        }
    }

    fun createSolidDrawable(@ColorInt color: Int, cornerRadiusDp: Float = 0f): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(color)
            if (cornerRadiusDp > 0f) {
                cornerRadius = cornerRadiusDp * density
            }
        }
    }

    fun getColorForSyntaxToken(tokenType: com.vscode.android.VSCodeApp.SyntaxTokenType): Int {
        return when (tokenType) {
            com.vscode.android.VSCodeApp.SyntaxTokenType.KEYWORD -> getKeywordColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.STRING -> getStringColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.NUMBER -> getNumberColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.COMMENT -> getCommentColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.TYPE -> getTypeColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.FUNCTION -> getFunctionColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.VARIABLE -> getVariableColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.CONSTANT -> getConstantColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.OPERATOR -> getOperatorColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.CLASS_NAME -> getClassNameColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.PARAMETER -> getParameterColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.PROPERTY -> getPropertyColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.REGEX -> getRegexColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.TAG -> getTagColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.ATTRIBUTE_NAME -> getAttributeNameColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.ATTRIBUTE_VALUE -> getAttributeValueColor()
            com.vscode.android.VSCodeApp.SyntaxTokenType.PLAIN -> getEditorForeground()
        }
    }
}