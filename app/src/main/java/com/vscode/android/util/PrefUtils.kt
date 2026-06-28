package com.vscode.android.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object PrefUtils {

    private const val TAG = "PrefUtils"
    private lateinit var preferences: SharedPreferences
    private var isInitialized = false
    private val listeners = CopyOnWriteArrayList<PreferenceChangeListener>()
    private val listenerRegistry = ConcurrentHashMap<String, SharedPreferences.OnSharedPreferenceChangeListener>()

    fun init(context: Context) {
        if (!isInitialized) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            isInitialized = true
            setupGlobalListener()
        }
    }

    fun init(context: Context, prefsName: String) {
        if (!isInitialized) {
            preferences = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            isInitialized = true
            setupGlobalListener()
        }
    }

    fun getPreferences(): SharedPreferences {
        ensureInitialized()
        return preferences
    }

    fun putString(key: String, value: String?) {
        ensureInitialized()
        preferences.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? {
        ensureInitialized()
        return preferences.getString(key, default)
    }

    fun putInt(key: String, value: Int) {
        ensureInitialized()
        preferences.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int {
        ensureInitialized()
        return preferences.getInt(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        ensureInitialized()
        preferences.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        ensureInitialized()
        return preferences.getBoolean(key, default)
    }

    fun putLong(key: String, value: Long) {
        ensureInitialized()
        preferences.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long {
        ensureInitialized()
        return preferences.getLong(key, default)
    }

    fun putFloat(key: String, value: Float) {
        ensureInitialized()
        preferences.edit().putFloat(key, value).apply()
    }

    fun getFloat(key: String, default: Float = 0f): Float {
        ensureInitialized()
        return preferences.getFloat(key, default)
    }

    fun putStringSet(key: String, value: Set<String>?) {
        ensureInitialized()
        preferences.edit().putStringSet(key, value).apply()
    }

    fun getStringSet(key: String, default: Set<String>? = null): Set<String>? {
        ensureInitialized()
        return preferences.getStringSet(key, default)
    }

    fun remove(key: String) {
        ensureInitialized()
        preferences.edit().remove(key).apply()
    }

    fun clear() {
        ensureInitialized()
        preferences.edit().clear().apply()
    }

    fun contains(key: String): Boolean {
        ensureInitialized()
        return preferences.contains(key)
    }

    fun getAll(): Map<String, *> {
        ensureInitialized()
        return preferences.all
    }

    fun registerListener(listener: PreferenceChangeListener) {
        ensureInitialized()
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: PreferenceChangeListener) {
        listeners.remove(listener)
    }

    fun registerKeyListener(key: String, listener: (String, Any?) -> Unit) {
        ensureInitialized()
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                val value = preferences.all[changedKey]
                listener(changedKey, value)
            }
        }
        listenerRegistry[key] = prefListener
        preferences.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun unregisterKeyListener(key: String) {
        listenerRegistry.remove(key)?.let {
            preferences.unregisterOnSharedPreferenceChangeListener(it)
        }
    }

    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        ensureInitialized()
        val editor = preferences.edit()
        editor.block()
        editor.apply()
    }

    fun commit(block: SharedPreferences.Editor.() -> Unit): Boolean {
        ensureInitialized()
        val editor = preferences.edit()
        editor.block()
        return editor.commit()
    }

    // ============ Theme Preferences ============

    fun getThemeMode(): String {
        return getString(KEY_THEME_MODE, "dark") ?: "dark"
    }

    fun setThemeMode(mode: String) {
        putString(KEY_THEME_MODE, mode)
    }

    fun isDarkMode(): Boolean {
        return getThemeMode() == "dark"
    }

    fun isHighContrastMode(): Boolean {
        return getThemeMode() == "high_contrast"
    }

    // ============ Language Preferences ============

    fun getLanguage(): String {
        return getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun setLanguage(language: String) {
        putString(KEY_LANGUAGE, language)
    }

    // ============ Editor Preferences ============

    fun getFontSize(): Int {
        return getInt(KEY_FONT_SIZE, 14)
    }

    fun setFontSize(size: Int) {
        putInt(KEY_FONT_SIZE, size.coerceIn(8, 72))
    }

    fun getTabSize(): Int {
        return getInt(KEY_TAB_SIZE, 4)
    }

    fun setTabSize(size: Int) {
        putInt(KEY_TAB_SIZE, size.coerceIn(1, 16))
    }

    fun isInsertSpaces(): Boolean {
        return getBoolean(KEY_INSERT_SPACES, true)
    }

    fun setInsertSpaces(insertSpaces: Boolean) {
        putBoolean(KEY_INSERT_SPACES, insertSpaces)
    }

    fun isWordWrap(): Boolean {
        return getBoolean(KEY_WORD_WRAP, true)
    }

    fun setWordWrap(wrap: Boolean) {
        putBoolean(KEY_WORD_WRAP, wrap)
    }

    fun isLineNumbersVisible(): Boolean {
        return getBoolean(KEY_LINE_NUMBERS, true)
    }

    fun setLineNumbersVisible(visible: Boolean) {
        putBoolean(KEY_LINE_NUMBERS, visible)
    }

    fun isMinimapEnabled(): Boolean {
        return getBoolean(KEY_MINIMAP, true)
    }

    fun setMinimapEnabled(enabled: Boolean) {
        putBoolean(KEY_MINIMAP, enabled)
    }

    fun isAutoSaveEnabled(): Boolean {
        return getBoolean(KEY_AUTO_SAVE, false)
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        putBoolean(KEY_AUTO_SAVE, enabled)
    }

    fun getAutoSaveDelay(): Int {
        return getInt(KEY_AUTO_SAVE_DELAY, 1000)
    }

    fun setAutoSaveDelay(delay: Int) {
        putInt(KEY_AUTO_SAVE_DELAY, delay.coerceIn(100, 30000))
    }

    fun getEditorFontFamily(): String {
        return getString(KEY_FONT_FAMILY, "monospace") ?: "monospace"
    }

    fun setEditorFontFamily(fontFamily: String) {
        putString(KEY_FONT_FAMILY, fontFamily)
    }

    fun isRenderWhitespace(): Boolean {
        return getBoolean(KEY_RENDER_WHITESPACE, false)
    }

    fun setRenderWhitespace(render: Boolean) {
        putBoolean(KEY_RENDER_WHITESPACE, render)
    }

    fun isBracketPairColorization(): Boolean {
        return getBoolean(KEY_BRACKET_PAIR_COLORIZATION, true)
    }

    fun setBracketPairColorization(enabled: Boolean) {
        putBoolean(KEY_BRACKET_PAIR_COLORIZATION, enabled)
    }

    fun getCursorStyle(): String {
        return getString(KEY_CURSOR_STYLE, "line") ?: "line"
    }

    fun setCursorStyle(style: String) {
        putString(KEY_CURSOR_STYLE, style)
    }

    fun isCursorBlinking(): Boolean {
        return getBoolean(KEY_CURSOR_BLINKING, true)
    }

    fun setCursorBlinking(blinking: Boolean) {
        putBoolean(KEY_CURSOR_BLINKING, blinking)
    }

    // ============ Terminal Preferences ============

    fun getTerminalFontSize(): Int {
        return getInt(KEY_TERMINAL_FONT_SIZE, 13)
    }

    fun setTerminalFontSize(size: Int) {
        putInt(KEY_TERMINAL_FONT_SIZE, size.coerceIn(8, 48))
    }

    fun getTerminalFontFamily(): String {
        return getString(KEY_TERMINAL_FONT_FAMILY, "monospace") ?: "monospace"
    }

    fun setTerminalFontFamily(fontFamily: String) {
        putString(KEY_TERMINAL_FONT_FAMILY, fontFamily)
    }

    fun getTerminalCursorStyle(): String {
        return getString(KEY_TERMINAL_CURSOR_STYLE, "block") ?: "block"
    }

    fun setTerminalCursorStyle(style: String) {
        putString(KEY_TERMINAL_CURSOR_STYLE, style)
    }

    fun getTerminalDefaultShell(): String {
        return getString(KEY_TERMINAL_SHELL, "bash") ?: "bash"
    }

    fun setTerminalDefaultShell(shell: String) {
        putString(KEY_TERMINAL_SHELL, shell)
    }

    fun isTerminalCursorBlinking(): Boolean {
        return getBoolean(KEY_TERMINAL_CURSOR_BLINKING, true)
    }

    fun setTerminalCursorBlinking(blinking: Boolean) {
        putBoolean(KEY_TERMINAL_CURSOR_BLINKING, blinking)
    }

    // ============ Git Preferences ============

    fun getGitUserName(): String {
        return getString(KEY_GIT_USER_NAME, "") ?: ""
    }

    fun setGitUserName(name: String) {
        putString(KEY_GIT_USER_NAME, name)
    }

    fun getGitUserEmail(): String {
        return getString(KEY_GIT_USER_EMAIL, "") ?: ""
    }

    fun setGitUserEmail(email: String) {
        putString(KEY_GIT_USER_EMAIL, email)
    }

    fun isGitAutoFetch(): Boolean {
        return getBoolean(KEY_GIT_AUTO_FETCH, false)
    }

    fun setGitAutoFetch(autoFetch: Boolean) {
        putBoolean(KEY_GIT_AUTO_FETCH, autoFetch)
    }

    // ============ File Explorer Preferences ============

    fun isShowHiddenFiles(): Boolean {
        return getBoolean(KEY_SHOW_HIDDEN_FILES, false)
    }

    fun setShowHiddenFiles(show: Boolean) {
        putBoolean(KEY_SHOW_HIDDEN_FILES, show)
    }

    fun getExplorerSortOrder(): String {
        return getString(KEY_EXPLORER_SORT_ORDER, "default") ?: "default"
    }

    fun setExplorerSortOrder(order: String) {
        putString(KEY_EXPLORER_SORT_ORDER, order)
    }

    fun isCompactFolders(): Boolean {
        return getBoolean(KEY_COMPACT_FOLDERS, true)
    }

    fun setCompactFolders(compact: Boolean) {
        putBoolean(KEY_COMPACT_FOLDERS, compact)
    }

    // ============ Build Preferences ============

    fun getAndroidSdkPath(): String {
        return getString(KEY_ANDROID_SDK_PATH, "") ?: ""
    }

    fun setAndroidSdkPath(path: String) {
        putString(KEY_ANDROID_SDK_PATH, path)
    }

    fun getJdkPath(): String {
        return getString(KEY_JDK_PATH, "") ?: ""
    }

    fun setJdkPath(path: String) {
        putString(KEY_JDK_PATH, path)
    }

    fun getGradlePath(): String {
        return getString(KEY_GRADLE_PATH, "") ?: ""
    }

    fun setGradlePath(path: String) {
        putString(KEY_GRADLE_PATH, path)
    }

    // ============ Recent Items ============

    fun getRecentFiles(): Set<String> {
        return getStringSet(KEY_RECENT_FILES) ?: emptySet()
    }

    fun addRecentFile(filePath: String) {
        val recent = getRecentFiles().toMutableSet()
        recent.add(filePath)
        if (recent.size > 50) {
            val toRemove = recent.take(recent.size - 50)
            recent.removeAll(toRemove.toSet())
        }
        putStringSet(KEY_RECENT_FILES, recent)
    }

    fun clearRecentFiles() {
        putStringSet(KEY_RECENT_FILES, emptySet())
    }

    fun getRecentFolders(): Set<String> {
        return getStringSet(KEY_RECENT_FOLDERS) ?: emptySet()
    }

    fun addRecentFolder(folderPath: String) {
        val recent = getRecentFolders().toMutableSet()
        recent.add(folderPath)
        if (recent.size > 20) {
            val toRemove = recent.take(recent.size - 20)
            recent.removeAll(toRemove.toSet())
        }
        putStringSet(KEY_RECENT_FOLDERS, recent)
    }

    fun clearRecentFolders() {
        putStringSet(KEY_RECENT_FOLDERS, emptySet())
    }

    // ============ General Preferences ============

    fun getLastOpenedFolder(): String {
        return getString(KEY_LAST_OPENED_FOLDER, "") ?: ""
    }

    fun setLastOpenedFolder(path: String) {
        putString(KEY_LAST_OPENED_FOLDER, path)
    }

    fun isFirstLaunch(): Boolean {
        return getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete() {
        putBoolean(KEY_FIRST_LAUNCH, false)
    }

    fun getAppVersion(): String {
        return getString(KEY_APP_VERSION, "1.0.0") ?: "1.0.0"
    }

    fun setAppVersion(version: String) {
        putString(KEY_APP_VERSION, version)
    }

    private fun setupGlobalListener() {
        val globalListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val value = preferences.all[key]
            listeners.forEach { it.onPreferenceChanged(key, value) }
        }
        preferences.registerOnSharedPreferenceChangeListener(globalListener)
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("PrefUtils is not initialized. Call init(context) first.")
        }
    }

    // ============ Preference Keys ============

    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_LANGUAGE = "pref_language"
    private const val KEY_FONT_SIZE = "pref_font_size"
    private const val KEY_TAB_SIZE = "pref_tab_size"
    private const val KEY_INSERT_SPACES = "pref_insert_spaces"
    private const val KEY_WORD_WRAP = "pref_word_wrap"
    private const val KEY_LINE_NUMBERS = "pref_line_numbers"
    private const val KEY_MINIMAP = "pref_minimap"
    private const val KEY_AUTO_SAVE = "pref_auto_save"
    private const val KEY_AUTO_SAVE_DELAY = "pref_auto_save_delay"
    private const val KEY_FONT_FAMILY = "pref_font_family"
    private const val KEY_RENDER_WHITESPACE = "pref_render_whitespace"
    private const val KEY_BRACKET_PAIR_COLORIZATION = "pref_bracket_pair_colorization"
    private const val KEY_CURSOR_STYLE = "pref_cursor_style"
    private const val KEY_CURSOR_BLINKING = "pref_cursor_blinking"
    private const val KEY_TERMINAL_FONT_SIZE = "pref_terminal_font_size"
    private const val KEY_TERMINAL_FONT_FAMILY = "pref_terminal_font_family"
    private const val KEY_TERMINAL_CURSOR_STYLE = "pref_terminal_cursor_style"
    private const val KEY_TERMINAL_SHELL = "pref_terminal_shell"
    private const val KEY_TERMINAL_CURSOR_BLINKING = "pref_terminal_cursor_blinking"
    private const val KEY_GIT_USER_NAME = "pref_git_user_name"
    private const val KEY_GIT_USER_EMAIL = "pref_git_user_email"
    private const val KEY_GIT_AUTO_FETCH = "pref_git_auto_fetch"
    private const val KEY_SHOW_HIDDEN_FILES = "pref_show_hidden_files"
    private const val KEY_EXPLORER_SORT_ORDER = "pref_explorer_sort_order"
    private const val KEY_COMPACT_FOLDERS = "pref_compact_folders"
    private const val KEY_ANDROID_SDK_PATH = "pref_android_sdk_path"
    private const val KEY_JDK_PATH = "pref_jdk_path"
    private const val KEY_GRADLE_PATH = "pref_gradle_path"
    private const val KEY_RECENT_FILES = "pref_recent_files"
    private const val KEY_RECENT_FOLDERS = "pref_recent_folders"
    private const val KEY_LAST_OPENED_FOLDER = "pref_last_opened_folder"
    private const val KEY_FIRST_LAUNCH = "pref_first_launch"
    private const val KEY_APP_VERSION = "pref_app_version"

    interface PreferenceChangeListener {
        fun onPreferenceChanged(key: String, value: Any?) {}
    }
}