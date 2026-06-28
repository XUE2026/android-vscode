package com.vscode.android.core.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class I18nManager(private val context: Context) {

    private val strings = ConcurrentHashMap<String, String>()
    private val languageListeners = mutableListOf<LanguageChangeListener>()
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentLanguage: String = "en"

    fun initialize(language: String = "en") {
        currentLanguage = language
        strings.clear()
        loadBuiltinStrings(language)
        loadStringsFromAssets(language)
        loadStringsFromResources(language)
    }

    private fun loadBuiltinStrings(language: String) {
        strings.putAll(BASE_STRINGS)
        when (language) {
            "zh" -> strings.putAll(ZH_STRINGS)
            "ja" -> strings.putAll(JA_STRINGS)
        }
    }

    private fun loadStringsFromAssets(language: String) {
        try {
            val assetPath = "i18n/$language.json"
            context.assets.open(assetPath).use { stream ->
                val json = stream.bufferedReader().readText()
                val parsed = parseJsonToMap(json)
                strings.putAll(parsed)
            }
        } catch (_: Exception) {
            // Fallback to built-in strings
        }
    }

    private fun loadStringsFromResources(language: String) {
        try {
            val resId = when (language) {
                "zh" -> context.resources.getIdentifier("values-zh", "string", context.packageName)
                "ja" -> context.resources.getIdentifier("values-ja", "string", context.packageName)
                else -> 0
            }
            if (resId != 0) {
                // Resources are loaded via Android's resource system automatically
            }
        } catch (_: Exception) {
            // Fallback
        }
    }

    private fun parseJsonToMap(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val cleaned = json.trim()
            if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                val content = cleaned.substring(1, cleaned.length - 1)
                val pairs = splitJsonPairs(content)
                for (pair in pairs) {
                    val colonIndex = pair.indexOf(':')
                    if (colonIndex > 0) {
                        val key = pair.substring(0, colonIndex).trim().removeSurrounding("\"")
                        val value = pair.substring(colonIndex + 1).trim().removeSurrounding("\"")
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            result[key] = value
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun splitJsonPairs(content: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        var inString = false
        var escape = false

        for (ch in content) {
            when {
                escape -> {
                    current.append(ch)
                    escape = false
                }
                ch == '\\' -> {
                    current.append(ch)
                    escape = true
                }
                ch == '"' -> {
                    inString = !inString
                    current.append(ch)
                }
                !inString && (ch == '{' || ch == '[') -> {
                    depth++
                    current.append(ch)
                }
                !inString && (ch == '}' || ch == ']') -> {
                    depth--
                    current.append(ch)
                }
                !inString && ch == ',' && depth == 0 -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    fun getString(key: String, vararg args: Any?): String {
        val template = strings[key] ?: key
        return if (args.isEmpty()) {
            template
        } else {
            try {
                String.format(template, *args)
            } catch (e: Exception) {
                template
            }
        }
    }

    fun getString(key: String, default: String, vararg args: Any?): String {
        val template = strings[key] ?: default
        return if (args.isEmpty()) {
            template
        } else {
            try {
                String.format(template, *args)
            } catch (e: Exception) {
                template
            }
        }
    }

    fun hasKey(key: String): Boolean = strings.containsKey(key)

    fun getLanguage(): String = currentLanguage

    fun setLanguage(language: String) {
        if (currentLanguage == language) return
        currentLanguage = language
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
        initialize(language)

        val locale = when (language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "ja" -> Locale.JAPANESE
            else -> Locale.ENGLISH
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        notifyLanguageChanged(language)
    }

    fun getAvailableLanguages(): List<Pair<String, String>> = listOf(
        "en" to "English",
        "zh" to "简体中文",
        "ja" to "日本語"
    )

    fun getAllKeys(): Set<String> = strings.keys.toSet()

    fun getAllStrings(): Map<String, String> = strings.toMap()

    fun getStringCount(): Int = strings.size

    fun addLanguageChangeListener(listener: LanguageChangeListener) {
        if (!languageListeners.contains(listener)) {
            languageListeners.add(listener)
        }
    }

    fun removeLanguageChangeListener(listener: LanguageChangeListener) {
        languageListeners.remove(listener)
    }

    private fun notifyLanguageChanged(language: String) {
        languageListeners.forEach { it.onLanguageChanged(language) }
    }

    fun shutdown() {
        strings.clear()
        languageListeners.clear()
    }

    interface LanguageChangeListener {
        fun onLanguageChanged(language: String)
    }

    companion object {
        private const val TAG = "I18nManager"
        private const val PREFS_NAME = "vscode_i18n_prefs"
        private const val KEY_LANGUAGE = "app_language"

        // ========== BASE STRINGS (English) ==========
        val BASE_STRINGS = mapOf(
            // App
            "app_name" to "Android VSCode",
            "app_version" to "1.0.0",
            "app_description" to "Visual Studio Code for Android",

            // Activity Bar
            "activity_bar.explorer" to "Explorer",
            "activity_bar.search" to "Search",
            "activity_bar.source_control" to "Source Control",
            "activity_bar.extensions" to "Extensions",
            "activity_bar.debug" to "Run and Debug",
            "activity_bar.settings" to "Settings",

            // Sidebar
            "sidebar.no_folder_open" to "You have not yet opened a folder.",
            "sidebar.open_folder_button" to "Open Folder",
            "sidebar.open_folder" to "Open Folder",
            "sidebar.close_folder" to "Close Folder",
            "sidebar.refresh" to "Refresh",
            "sidebar.collapse_all" to "Collapse All",

            // Panel
            "panel.problems" to "Problems",
            "panel.output" to "Output",
            "panel.terminal" to "Terminal",
            "panel.debug_console" to "Debug Console",
            "panel.no_problems" to "No problems detected.",
            "panel.no_output" to "No output.",

            // Status Bar
            "status_bar.branch" to "main",
            "status_bar.encoding" to "UTF-8",
            "status_bar.line_endings" to "LF",
            "status_bar.language" to "Plain Text",
            "status_bar.feedback" to "Feedback",
            "status_bar.notifications" to "Notifications",
            "status_bar.line_col" to "Ln %d, Col %d",
            "status_bar.indent" to "Spaces: %d",
            "status_bar.go_to_line" to "Go to Line",
            "status_bar.errors" to "%d errors",
            "status_bar.warnings" to "%d warnings",

            // Menu Items
            "menu.new_file" to "New File",
            "menu.new_folder" to "New Folder",
            "menu.open_file" to "Open File",
            "menu.open_folder" to "Open Folder",
            "menu.open_recent" to "Open Recent",
            "menu.save" to "Save",
            "menu.save_as" to "Save As...",
            "menu.save_all" to "Save All",
            "menu.undo" to "Undo",
            "menu.redo" to "Redo",
            "menu.cut" to "Cut",
            "menu.copy" to "Copy",
            "menu.paste" to "Paste",
            "menu.select_all" to "Select All",
            "menu.find" to "Find",
            "menu.replace" to "Replace",
            "menu.find_in_files" to "Find in Files",
            "menu.replace_in_files" to "Replace in Files",
            "menu.settings" to "Settings",
            "menu.command_palette" to "Command Palette...",
            "menu.toggle_sidebar" to "Toggle Sidebar",
            "menu.toggle_panel" to "Toggle Panel",
            "menu.toggle_fullscreen" to "Toggle Full Screen",
            "menu.zoom_in" to "Zoom In",
            "menu.zoom_out" to "Zoom Out",
            "menu.zoom_reset" to "Reset Zoom",
            "menu.split_editor" to "Split Editor",
            "menu.close_editor" to "Close Editor",
            "menu.close_all" to "Close All",
            "menu.close_others" to "Close Others",
            "menu.close_to_right" to "Close to the Right",
            "menu.close_saved" to "Close Saved",
            "menu.reopen_closed" to "Reopen Closed Editor",
            "menu.toggle_word_wrap" to "Toggle Word Wrap",
            "menu.toggle_line_numbers" to "Toggle Line Numbers",
            "menu.toggle_minimap" to "Toggle Minimap",
            "menu.change_language" to "Change Language Mode",
            "menu.change_encoding" to "Change File Encoding",
            "menu.change_line_endings" to "Change Line Endings",
            "menu.format_document" to "Format Document",
            "menu.format_selection" to "Format Selection",
            "menu.go_to_definition" to "Go to Definition",
            "menu.go_to_references" to "Find All References",
            "menu.go_to_symbol" to "Go to Symbol",
            "menu.rename_symbol" to "Rename Symbol",
            "menu.quick_fix" to "Quick Fix",
            "menu.show_hover" to "Show Hover",

            // Dialog
            "dialog.confirm_delete_title" to "Confirm Delete",
            "dialog.confirm_delete_message" to "Are you sure you want to delete \"%s\"?",
            "dialog.save_changes_title" to "Save Changes",
            "dialog.save_changes_message" to "Do you want to save changes to \"%s\"?",
            "dialog.unsaved_changes" to "There are unsaved changes. Do you want to save them?",
            "dialog.yes" to "Yes",
            "dialog.no" to "No",
            "dialog.cancel" to "Cancel",
            "dialog.save" to "Save",
            "dialog.dont_save" to "Don't Save",
            "dialog.ok" to "OK",
            "dialog.close" to "Close",
            "dialog.confirm" to "Confirm",
            "dialog.retry" to "Retry",
            "dialog.ignore" to "Ignore",
            "dialog.apply" to "Apply",
            "dialog.reset" to "Reset",
            "dialog.file_exists_title" to "File Already Exists",
            "dialog.file_exists_message" to "A file or folder \"%s\" already exists at this location.",
            "dialog.overwrite" to "Overwrite",
            "dialog.rename" to "Rename",
            "dialog.invalid_name" to "Invalid name. Please enter a valid file name.",
            "dialog.about_title" to "About Android VSCode",
            "dialog.about_message" to "Android VSCode v1.0.0\n\nA mobile code editor for Android.",

            // Editor
            "editor.welcome" to "Welcome to Android VSCode",
            "editor.no_file_open" to "No file is currently open",
            "editor.untitled" to "Untitled",
            "editor.modified" to "Modified",
            "editor.readonly" to "Read Only",
            "editor.unsaved" to "Unsaved",
            "editor.language_plaintext" to "Plain Text",
            "editor.open_file" to "Open File",
            "editor.open_folder_command" to "Open Folder",
            "editor.new_file" to "New File",
            "editor.recent" to "Recent",
            "editor.no_recent" to "No recent files",
            "editor.file_dropped" to "File dropped: %s",
            "editor.line" to "Line",
            "editor.column" to "Column",
            "editor.selection" to "Selection",
            "editor.indent_size" to "Indent Size",
            "editor.tab_size" to "Tab Size",
            "editor.insert_spaces" to "Insert Spaces",
            "editor.detect_indentation" to "Detect Indentation",

            // File Operations
            "file.new" to "New File",
            "file.new_folder" to "New Folder",
            "file.rename" to "Rename",
            "file.delete" to "Delete",
            "file.copy" to "Copy",
            "file.cut" to "Cut",
            "file.paste" to "Paste",
            "file.copy_path" to "Copy Path",
            "file.copy_relative_path" to "Copy Relative Path",
            "file.reveal_in_explorer" to "Reveal in Explorer",
            "file.open_in_terminal" to "Open in Terminal",
            "file.properties" to "Properties",
            "file.size" to "Size",
            "file.modified" to "Modified",
            "file.created" to "Created",
            "file.type" to "Type",
            "file.permissions" to "Permissions",
            "file.readable" to "Readable",
            "file.writable" to "Writable",
            "file.executable" to "Executable",

            // Search
            "search.find" to "Search",
            "search.replace" to "Replace",
            "search.find_in_files" to "Find in Files",
            "search.replace_in_files" to "Replace in Files",
            "search.match_case" to "Match Case",
            "search.whole_word" to "Whole Word",
            "search.regex" to "Use Regular Expression",
            "search.no_results" to "No results found.",
            "search.result_count" to "%d results in %d files",
            "search.result_count_single" to "%d results in 1 file",
            "search.replaced_count" to "Replaced %d occurrences",
            "search.preserve_case" to "Preserve Case",
            "search.files_to_include" to "files to include",
            "search.files_to_exclude" to "files to exclude",
            "search.searching" to "Searching...",
            "search.search_complete" to "Search complete",

            // Git / SCM
            "scm.changes" to "Changes",
            "scm.staged_changes" to "Staged Changes",
            "scm.merge_changes" to "Merge Changes",
            "scm.no_changes" to "No changes detected.",
            "scm.commit" to "Commit",
            "scm.commit_message" to "Commit message",
            "scm.commit_success" to "Commit successful",
            "scm.commit_failed" to "Commit failed",
            "scm.push" to "Push",
            "scm.pull" to "Pull",
            "scm.fetch" to "Fetch",
            "scm.branch" to "Branch",
            "scm.checkout" to "Checkout",
            "scm.merge" to "Merge",
            "scm.rebase" to "Rebase",
            "scm.stash" to "Stash",
            "scm.pop_stash" to "Pop Stash",
            "scm.discard" to "Discard Changes",
            "scm.stage" to "Stage",
            "scm.unstage" to "Unstage",
            "scm.initialize_repo" to "Initialize Repository",
            "scm.clone_repo" to "Clone Repository",
            "scm.repository" to "Repository",
            "scm.working_tree" to "Working Tree",
            "scm.index" to "Index",
            "scm.remote" to "Remote",
            "scm.sync" to "Sync",
            "scm.publish" to "Publish Branch",
            "scm.commit_signed" to "Commit (Signed)",
            "scm.undo_commit" to "Undo Last Commit",

            // Extensions
            "extensions.search" to "Search Extensions",
            "extensions.installed" to "Installed",
            "extensions.recommended" to "Recommended",
            "extensions.enabled" to "Enabled",
            "extensions.disabled" to "Disabled",
            "extensions.no_installed" to "No extensions installed.",
            "extensions.install" to "Install",
            "extensions.uninstall" to "Uninstall",
            "extensions.enable" to "Enable",
            "extensions.disable" to "Disable",
            "extensions.reload" to "Reload",
            "extensions.update" to "Update",
            "extensions.update_all" to "Update All",
            "extensions.author" to "Author",
            "extensions.version" to "Version",
            "extensions.description" to "Description",
            "extensions.details" to "Details",
            "extensions.features" to "Features",
            "extensions.changelog" to "Changelog",
            "extensions.dependencies" to "Dependencies",
            "extensions.marketplace" to "Marketplace",
            "extensions.installing" to "Installing...",
            "extensions.uninstalling" to "Uninstalling...",
            "extensions.reload_required" to "Reload required",
            "extensions.builtin" to "Built-in",

            // Terminal
            "terminal.new" to "New Terminal",
            "terminal.kill" to "Kill Terminal",
            "terminal.clear" to "Clear Terminal",
            "terminal.select_all" to "Select All",
            "terminal.copy" to "Copy",
            "terminal.paste" to "Paste",
            "terminal.split" to "Split Terminal",
            "terminal.rename" to "Rename Terminal",
            "terminal.change_color" to "Change Terminal Color",
            "terminal.scroll_up" to "Scroll Up",
            "terminal.scroll_down" to "Scroll Down",
            "terminal.no_terminals" to "No terminals open.",
            "terminal.running" to "Running",
            "terminal.stopped" to "Terminated",
            "terminal.process_exited" to "Process exited with code %d",

            // Build / Debug
            "build.configure" to "Configure Build",
            "build.run" to "Run",
            "build.debug" to "Debug",
            "build.stop" to "Stop",
            "build.restart" to "Restart",
            "build.build" to "Build",
            "build.clean" to "Clean",
            "build.rebuild" to "Rebuild",
            "build.building" to "Building...",
            "build.build_success" to "Build succeeded",
            "build.build_failed" to "Build failed",
            "build.no_tasks" to "No build tasks configured.",
            "debug.start" to "Start Debugging",
            "debug.stop" to "Stop Debugging",
            "debug.pause" to "Pause",
            "debug.continue" to "Continue",
            "debug.step_over" to "Step Over",
            "debug.step_into" to "Step Into",
            "debug.step_out" to "Step Out",
            "debug.restart" to "Restart",
            "debug.no_configuration" to "No debug configuration found.",
            "debug.create_launch_json" to "Create a launch.json file",
            "debug.add_configuration" to "Add Configuration",
            "debug.breakpoints" to "Breakpoints",
            "debug.call_stack" to "Call Stack",
            "debug.variables" to "Variables",
            "debug.watch" to "Watch",
            "debug.console" to "Debug Console",
            "debug.attach" to "Attach",
            "debug.disconnect" to "Disconnect",

            // Settings
            "settings.title" to "Settings",
            "settings.search" to "Search Settings",
            "settings.user" to "User",
            "settings.workspace" to "Workspace",
            "settings.editor" to "Editor",
            "settings.appearance" to "Appearance",
            "settings.language" to "Language",
            "settings.theme" to "Theme",
            "settings.font_size" to "Font Size",
            "settings.font_family" to "Font Family",
            "settings.tab_size" to "Tab Size",
            "settings.word_wrap" to "Word Wrap",
            "settings.line_numbers" to "Line Numbers",
            "settings.minimap" to "Minimap",
            "settings.auto_save" to "Auto Save",
            "settings.format_on_save" to "Format on Save",
            "settings.dark_theme" to "Dark",
            "settings.light_theme" to "Light",
            "settings.high_contrast_theme" to "High Contrast",
            "settings.language_en" to "English",
            "settings.language_zh" to "简体中文",
            "settings.language_ja" to "日本語",
            "settings.reset" to "Reset Settings",
            "settings.export" to "Export Settings",
            "settings.import" to "Import Settings",

            // Error Messages
            "error.file_not_found" to "File not found: %s",
            "error.folder_not_found" to "Folder not found: %s",
            "error.cannot_read" to "Cannot read file: %s",
            "error.cannot_write" to "Cannot write to file: %s",
            "error.cannot_save" to "Cannot save file: %s",
            "error.cannot_open" to "Cannot open file: %s",
            "error.cannot_delete" to "Cannot delete: %s",
            "error.cannot_create" to "Cannot create: %s",
            "error.cannot_rename" to "Cannot rename: %s",
            "error.cannot_copy" to "Cannot copy: %s",
            "error.permission_denied" to "Permission denied",
            "error.disk_full" to "Disk is full",
            "error.network_error" to "Network error occurred",
            "error.timeout" to "Operation timed out",
            "error.unknown" to "An unknown error occurred",
            "error.unsupported" to "Operation not supported",
            "error.invalid_format" to "Invalid format",
            "error.out_of_memory" to "Out of memory",
            "error.build_failed" to "Build failed with errors",
            "error.debug_failed" to "Debug session failed to start",
            "error.terminal_failed" to "Terminal failed to start",
            "error.plugin_load_failed" to "Failed to load plugin: %s",
            "error.plugin_activate_failed" to "Failed to activate plugin: %s",
            "error.no_language_support" to "No language support for: %s",
            "error.command_not_found" to "Command not found: %s",
            "error.file_too_large" to "File is too large to open",
            "error.binary_file" to "Cannot open binary file in editor",

            // Info Messages
            "info.file_saved" to "File saved: %s",
            "info.files_saved" to "%d files saved",
            "info.file_created" to "File created: %s",
            "info.folder_created" to "Folder created: %s",
            "info.file_deleted" to "File deleted: %s",
            "info.file_renamed" to "Renamed to: %s",
            "info.file_copied" to "Copied to: %s",
            "info.plugin_installed" to "Plugin installed: %s",
            "info.plugin_uninstalled" to "Plugin uninstalled: %s",
            "info.plugin_enabled" to "Plugin enabled: %s",
            "info.plugin_disabled" to "Plugin disabled: %s",
            "info.language_changed" to "Language changed to %s",
            "info.theme_changed" to "Theme changed to %s",
            "info.reload" to "Reload to apply changes",
            "info.reloading" to "Reloading...",
            "info.ready" to "Ready",
            "info.loading" to "Loading...",
            "info.processing" to "Processing...",
            "info.completed" to "Completed",
            "info.cancelled" to "Cancelled",
            "info.no_results" to "No results",
            "info.clipboard_copied" to "Copied to clipboard",
            "info.clipboard_cut" to "Cut to clipboard"
        )

        // ========== CHINESE STRINGS (简体中文) ==========
        val ZH_STRINGS = mapOf(
            "app_name" to "Android VSCode",
            "app_description" to "适用于 Android 的 Visual Studio Code",

            "activity_bar.explorer" to "资源管理器",
            "activity_bar.search" to "搜索",
            "activity_bar.source_control" to "源代码管理",
            "activity_bar.extensions" to "扩展",
            "activity_bar.debug" to "运行和调试",
            "activity_bar.settings" to "设置",

            "sidebar.no_folder_open" to "您尚未打开任何文件夹。",
            "sidebar.open_folder_button" to "打开文件夹",
            "sidebar.open_folder" to "打开文件夹",
            "sidebar.close_folder" to "关闭文件夹",
            "sidebar.refresh" to "刷新",
            "sidebar.collapse_all" to "全部折叠",

            "panel.problems" to "问题",
            "panel.output" to "输出",
            "panel.terminal" to "终端",
            "panel.debug_console" to "调试控制台",
            "panel.no_problems" to "未检测到问题。",
            "panel.no_output" to "无输出。",

            "status_bar.branch" to "main",
            "status_bar.encoding" to "UTF-8",
            "status_bar.line_endings" to "LF",
            "status_bar.language" to "纯文本",
            "status_bar.feedback" to "反馈",
            "status_bar.notifications" to "通知",
            "status_bar.line_col" to "第 %d 行，第 %d 列",
            "status_bar.indent" to "空格: %d",
            "status_bar.go_to_line" to "转到行",
            "status_bar.errors" to "%d 个错误",
            "status_bar.warnings" to "%d 个警告",

            "menu.new_file" to "新建文件",
            "menu.new_folder" to "新建文件夹",
            "menu.open_file" to "打开文件",
            "menu.open_folder" to "打开文件夹",
            "menu.open_recent" to "打开最近",
            "menu.save" to "保存",
            "menu.save_as" to "另存为...",
            "menu.save_all" to "全部保存",
            "menu.undo" to "撤销",
            "menu.redo" to "重做",
            "menu.cut" to "剪切",
            "menu.copy" to "复制",
            "menu.paste" to "粘贴",
            "menu.select_all" to "全选",
            "menu.find" to "查找",
            "menu.replace" to "替换",
            "menu.find_in_files" to "在文件中查找",
            "menu.replace_in_files" to "在文件中替换",
            "menu.settings" to "设置",
            "menu.command_palette" to "命令面板...",
            "menu.toggle_sidebar" to "切换侧边栏",
            "menu.toggle_panel" to "切换面板",
            "menu.toggle_fullscreen" to "切换全屏",
            "menu.zoom_in" to "放大",
            "menu.zoom_out" to "缩小",
            "menu.zoom_reset" to "重置缩放",
            "menu.split_editor" to "拆分编辑器",
            "menu.close_editor" to "关闭编辑器",
            "menu.close_all" to "全部关闭",
            "menu.close_others" to "关闭其他",
            "menu.close_to_right" to "关闭右侧",
            "menu.close_saved" to "关闭已保存",
            "menu.reopen_closed" to "重新打开已关闭的编辑器",
            "menu.toggle_word_wrap" to "切换自动换行",
            "menu.toggle_line_numbers" to "切换行号",
            "menu.toggle_minimap" to "切换缩略图",
            "menu.change_language" to "更改语言模式",
            "menu.change_encoding" to "更改文件编码",
            "menu.change_line_endings" to "更改行尾序列",
            "menu.format_document" to "格式化文档",
            "menu.format_selection" to "格式化选定内容",
            "menu.go_to_definition" to "转到定义",
            "menu.go_to_references" to "查找所有引用",
            "menu.go_to_symbol" to "转到符号",
            "menu.rename_symbol" to "重命名符号",
            "menu.quick_fix" to "快速修复",
            "menu.show_hover" to "显示悬停",

            "dialog.confirm_delete_title" to "确认删除",
            "dialog.confirm_delete_message" to "确定要删除 \"%s\" 吗？",
            "dialog.save_changes_title" to "保存更改",
            "dialog.save_changes_message" to "是否保存对 \"%s\" 的更改？",
            "dialog.unsaved_changes" to "有未保存的更改，是否保存？",
            "dialog.yes" to "是",
            "dialog.no" to "否",
            "dialog.cancel" to "取消",
            "dialog.save" to "保存",
            "dialog.dont_save" to "不保存",
            "dialog.ok" to "确定",
            "dialog.close" to "关闭",
            "dialog.confirm" to "确认",
            "dialog.retry" to "重试",
            "dialog.ignore" to "忽略",
            "dialog.apply" to "应用",
            "dialog.reset" to "重置",
            "dialog.file_exists_title" to "文件已存在",
            "dialog.file_exists_message" to "此位置已存在名为 \"%s\" 的文件或文件夹。",
            "dialog.overwrite" to "覆盖",
            "dialog.rename" to "重命名",
            "dialog.invalid_name" to "无效的名称。请输入有效的文件名。",
            "dialog.about_title" to "关于 Android VSCode",
            "dialog.about_message" to "Android VSCode v1.0.0\n\n面向 Android 的移动代码编辑器。",

            "editor.welcome" to "欢迎使用 Android VSCode",
            "editor.no_file_open" to "当前没有打开的文件",
            "editor.untitled" to "未命名",
            "editor.modified" to "已修改",
            "editor.readonly" to "只读",
            "editor.unsaved" to "未保存",
            "editor.language_plaintext" to "纯文本",

            "search.find" to "搜索",
            "search.replace" to "替换",
            "search.find_in_files" to "在文件中查找",
            "search.replace_in_files" to "在文件中替换",
            "search.match_case" to "区分大小写",
            "search.whole_word" to "全词匹配",
            "search.regex" to "使用正则表达式",
            "search.no_results" to "未找到结果。",
            "search.result_count" to "在 %2$d 个文件中找到 %1$d 个结果",
            "search.result_count_single" to "在 1 个文件中找到 %d 个结果",
            "search.replaced_count" to "已替换 %d 处",

            "scm.changes" to "更改",
            "scm.staged_changes" to "暂存的更改",
            "scm.merge_changes" to "合并更改",
            "scm.no_changes" to "未检测到更改。",
            "scm.commit" to "提交",
            "scm.commit_message" to "提交信息",
            "scm.push" to "推送",
            "scm.pull" to "拉取",
            "scm.fetch" to "获取",
            "scm.branch" to "分支",
            "scm.discard" to "放弃更改",
            "scm.stage" to "暂存",
            "scm.unstage" to "取消暂存",
            "scm.initialize_repo" to "初始化仓库",
            "scm.clone_repo" to "克隆仓库",

            "extensions.search" to "搜索扩展",
            "extensions.installed" to "已安装",
            "extensions.recommended" to "推荐",
            "extensions.enabled" to "已启用",
            "extensions.disabled" to "已禁用",
            "extensions.no_installed" to "未安装扩展。",
            "extensions.install" to "安装",
            "extensions.uninstall" to "卸载",
            "extensions.enable" to "启用",
            "extensions.disable" to "禁用",
            "extensions.update" to "更新",
            "extensions.builtin" to "内置",

            "terminal.new" to "新建终端",
            "terminal.kill" to "终止终端",
            "terminal.clear" to "清除终端",
            "terminal.select_all" to "全选",
            "terminal.copy" to "复制",
            "terminal.paste" to "粘贴",
            "terminal.split" to "拆分终端",
            "terminal.rename" to "重命名终端",

            "build.run" to "运行",
            "build.debug" to "调试",
            "build.stop" to "停止",
            "build.restart" to "重新启动",
            "build.build" to "构建",
            "build.clean" to "清理",
            "build.building" to "正在构建...",
            "build.build_success" to "构建成功",
            "build.build_failed" to "构建失败",

            "debug.start" to "开始调试",
            "debug.stop" to "停止调试",
            "debug.pause" to "暂停",
            "debug.continue" to "继续",
            "debug.step_over" to "单步跳过",
            "debug.step_into" to "单步进入",
            "debug.step_out" to "单步跳出",
            "debug.restart" to "重新启动",
            "debug.no_configuration" to "未找到调试配置。",
            "debug.create_launch_json" to "创建 launch.json 文件",

            "settings.title" to "设置",
            "settings.search" to "搜索设置",
            "settings.user" to "用户",
            "settings.workspace" to "工作区",
            "settings.editor" to "编辑器",
            "settings.appearance" to "外观",
            "settings.language" to "语言",
            "settings.theme" to "主题",
            "settings.font_size" to "字体大小",
            "settings.font_family" to "字体",
            "settings.tab_size" to "制表符大小",
            "settings.word_wrap" to "自动换行",
            "settings.line_numbers" to "行号",
            "settings.minimap" to "缩略图",
            "settings.auto_save" to "自动保存",
            "settings.format_on_save" to "保存时格式化",
            "settings.dark_theme" to "深色",
            "settings.light_theme" to "浅色",
            "settings.high_contrast_theme" to "高对比度",
            "settings.language_en" to "English",
            "settings.language_zh" to "简体中文",
            "settings.language_ja" to "日本語",

            "error.file_not_found" to "找不到文件: %s",
            "error.folder_not_found" to "找不到文件夹: %s",
            "error.cannot_read" to "无法读取文件: %s",
            "error.cannot_write" to "无法写入文件: %s",
            "error.cannot_save" to "无法保存文件: %s",
            "error.cannot_open" to "无法打开文件: %s",
            "error.cannot_delete" to "无法删除: %s",
            "error.cannot_create" to "无法创建: %s",
            "error.permission_denied" to "权限被拒绝",
            "error.unknown" to "发生未知错误",
            "error.build_failed" to "构建失败，存在错误",
            "error.plugin_load_failed" to "加载插件失败: %s",

            "info.file_saved" to "文件已保存: %s",
            "info.files_saved" to "已保存 %d 个文件",
            "info.file_created" to "文件已创建: %s",
            "info.folder_created" to "文件夹已创建: %s",
            "info.file_deleted" to "文件已删除: %s",
            "info.file_renamed" to "已重命名为: %s",
            "info.plugin_installed" to "插件已安装: %s",
            "info.plugin_enabled" to "插件已启用: %s",
            "info.plugin_disabled" to "插件已禁用: %s",
            "info.language_changed" to "语言已更改为 %s",
            "info.theme_changed" to "主题已更改为 %s",
            "info.ready" to "就绪",
            "info.loading" to "加载中...",
            "info.completed" to "已完成",
            "info.cancelled" to "已取消"
        )

        // ========== JAPANESE STRINGS (日本語) ==========
        val JA_STRINGS = mapOf(
            "app_name" to "Android VSCode",
            "app_description" to "Android 向け Visual Studio Code",

            "activity_bar.explorer" to "エクスプローラー",
            "activity_bar.search" to "検索",
            "activity_bar.source_control" to "ソース管理",
            "activity_bar.extensions" to "拡張機能",
            "activity_bar.debug" to "実行とデバッグ",
            "activity_bar.settings" to "設定",

            "sidebar.no_folder_open" to "まだフォルダーが開かれていません。",
            "sidebar.open_folder_button" to "フォルダーを開く",
            "sidebar.open_folder" to "フォルダーを開く",
            "sidebar.close_folder" to "フォルダーを閉じる",
            "sidebar.refresh" to "更新",
            "sidebar.collapse_all" to "すべて折りたたむ",

            "panel.problems" to "問題",
            "panel.output" to "出力",
            "panel.terminal" to "ターミナル",
            "panel.debug_console" to "デバッグコンソール",
            "panel.no_problems" to "問題は検出されませんでした。",
            "panel.no_output" to "出力はありません。",

            "status_bar.branch" to "main",
            "status_bar.encoding" to "UTF-8",
            "status_bar.line_endings" to "LF",
            "status_bar.language" to "プレーンテキスト",
            "status_bar.feedback" to "フィードバック",
            "status_bar.notifications" to "通知",
            "status_bar.line_col" to "%d 行 %d 列",
            "status_bar.indent" to "スペース: %d",
            "status_bar.go_to_line" to "行に移動",
            "status_bar.errors" to "%d 件のエラー",
            "status_bar.warnings" to "%d 件の警告",

            "menu.new_file" to "新しいファイル",
            "menu.new_folder" to "新しいフォルダー",
            "menu.open_file" to "ファイルを開く",
            "menu.open_folder" to "フォルダーを開く",
            "menu.open_recent" to "最近開いた項目",
            "menu.save" to "保存",
            "menu.save_as" to "名前を付けて保存...",
            "menu.save_all" to "すべて保存",
            "menu.undo" to "元に戻す",
            "menu.redo" to "やり直す",
            "menu.cut" to "切り取り",
            "menu.copy" to "コピー",
            "menu.paste" to "貼り付け",
            "menu.select_all" to "すべて選択",
            "menu.find" to "検索",
            "menu.replace" to "置換",
            "menu.find_in_files" to "ファイル内検索",
            "menu.replace_in_files" to "ファイル内置換",
            "menu.settings" to "設定",
            "menu.command_palette" to "コマンドパレット...",
            "menu.toggle_sidebar" to "サイドバー切替",
            "menu.toggle_panel" to "パネル切替",
            "menu.toggle_fullscreen" to "全画面切替",
            "menu.zoom_in" to "拡大",
            "menu.zoom_out" to "縮小",
            "menu.zoom_reset" to "ズームをリセット",
            "menu.split_editor" to "エディターを分割",
            "menu.close_editor" to "エディターを閉じる",
            "menu.close_all" to "すべて閉じる",
            "menu.close_others" to "その他を閉じる",
            "menu.close_to_right" to "右側を閉じる",
            "menu.close_saved" to "保存済みを閉じる",
            "menu.format_document" to "ドキュメントをフォーマット",
            "menu.format_selection" to "選択範囲をフォーマット",
            "menu.go_to_definition" to "定義へ移動",
            "menu.go_to_references" to "すべての参照を検索",
            "menu.rename_symbol" to "シンボル名を変更",
            "menu.quick_fix" to "クイック修正",

            "dialog.confirm_delete_title" to "削除の確認",
            "dialog.confirm_delete_message" to "\"%s\" を削除してもよろしいですか？",
            "dialog.save_changes_title" to "変更を保存",
            "dialog.save_changes_message" to "\"%s\" への変更を保存しますか？",
            "dialog.unsaved_changes" to "未保存の変更があります。保存しますか？",
            "dialog.yes" to "はい",
            "dialog.no" to "いいえ",
            "dialog.cancel" to "キャンセル",
            "dialog.save" to "保存",
            "dialog.dont_save" to "保存しない",
            "dialog.ok" to "OK",
            "dialog.close" to "閉じる",
            "dialog.confirm" to "確認",
            "dialog.retry" to "再試行",
            "dialog.ignore" to "無視",
            "dialog.apply" to "適用",
            "dialog.reset" to "リセット",

            "editor.welcome" to "Android VSCode へようこそ",
            "editor.no_file_open" to "ファイルが開かれていません",
            "editor.untitled" to "無題",
            "editor.modified" to "変更済み",
            "editor.readonly" to "読み取り専用",
            "editor.unsaved" to "未保存",

            "search.find" to "検索",
            "search.replace" to "置換",
            "search.find_in_files" to "ファイル内検索",
            "search.replace_in_files" to "ファイル内置換",
            "search.match_case" to "大文字/小文字を区別",
            "search.whole_word" to "単語単位",
            "search.regex" to "正規表現",
            "search.no_results" to "結果が見つかりません。",
            "search.result_count" to "%2$d ファイル中 %1$d 件の結果",
            "search.replaced_count" to "%d 件置換しました",

            "scm.changes" to "変更",
            "scm.staged_changes" to "ステージング済みの変更",
            "scm.no_changes" to "変更は検出されませんでした。",
            "scm.commit" to "コミット",
            "scm.commit_message" to "コミットメッセージ",
            "scm.push" to "プッシュ",
            "scm.pull" to "プル",
            "scm.branch" to "ブランチ",
            "scm.discard" to "変更を破棄",
            "scm.stage" to "ステージ",
            "scm.unstage" to "ステージ解除",
            "scm.initialize_repo" to "リポジトリを初期化",
            "scm.clone_repo" to "リポジトリをクローン",

            "extensions.search" to "拡張機能を検索",
            "extensions.installed" to "インストール済み",
            "extensions.recommended" to "おすすめ",
            "extensions.enabled" to "有効",
            "extensions.disabled" to "無効",
            "extensions.no_installed" to "拡張機能がインストールされていません。",
            "extensions.install" to "インストール",
            "extensions.uninstall" to "アンインストール",
            "extensions.enable" to "有効化",
            "extensions.disable" to "無効化",
            "extensions.update" to "更新",
            "extensions.builtin" to "組み込み",

            "terminal.new" to "新しいターミナル",
            "terminal.kill" to "ターミナルを終了",
            "terminal.clear" to "ターミナルをクリア",
            "terminal.select_all" to "すべて選択",
            "terminal.copy" to "コピー",
            "terminal.paste" to "貼り付け",
            "terminal.split" to "ターミナルを分割",
            "terminal.rename" to "ターミナル名を変更",

            "build.run" to "実行",
            "build.debug" to "デバッグ",
            "build.stop" to "停止",
            "build.restart" to "再起動",
            "build.build" to "ビルド",
            "build.clean" to "クリーン",
            "build.building" to "ビルド中...",
            "build.build_success" to "ビルド成功",
            "build.build_failed" to "ビルド失敗",

            "debug.start" to "デバッグ開始",
            "debug.stop" to "デバッグ停止",
            "debug.pause" to "一時停止",
            "debug.continue" to "続行",
            "debug.step_over" to "ステップオーバー",
            "debug.step_into" to "ステップイン",
            "debug.step_out" to "ステップアウト",
            "debug.restart" to "再起動",
            "debug.no_configuration" to "デバッグ構成が見つかりません。",
            "debug.create_launch_json" to "launch.json ファイルを作成",

            "settings.title" to "設定",
            "settings.search" to "設定を検索",
            "settings.user" to "ユーザー",
            "settings.workspace" to "ワークスペース",
            "settings.editor" to "エディター",
            "settings.appearance" to "外観",
            "settings.language" to "言語",
            "settings.theme" to "テーマ",
            "settings.font_size" to "フォントサイズ",
            "settings.font_family" to "フォント",
            "settings.tab_size" to "タブサイズ",
            "settings.word_wrap" to "ワードラップ",
            "settings.line_numbers" to "行番号",
            "settings.minimap" to "ミニマップ",
            "settings.auto_save" to "自動保存",
            "settings.format_on_save" to "保存時にフォーマット",
            "settings.dark_theme" to "ダーク",
            "settings.light_theme" to "ライト",
            "settings.high_contrast_theme" to "ハイコントラスト",
            "settings.language_en" to "English",
            "settings.language_zh" to "简体中文",
            "settings.language_ja" to "日本語",

            "error.file_not_found" to "ファイルが見つかりません: %s",
            "error.folder_not_found" to "フォルダーが見つかりません: %s",
            "error.cannot_read" to "ファイルを読み取れません: %s",
            "error.cannot_write" to "ファイルに書き込めません: %s",
            "error.cannot_save" to "ファイルを保存できません: %s",
            "error.cannot_open" to "ファイルを開けません: %s",
            "error.cannot_delete" to "削除できません: %s",
            "error.permission_denied" to "権限が拒否されました",
            "error.unknown" to "不明なエラーが発生しました",
            "error.build_failed" to "ビルドに失敗しました",
            "error.plugin_load_failed" to "プラグインの読み込みに失敗しました: %s",

            "info.file_saved" to "ファイルを保存しました: %s",
            "info.files_saved" to "%d ファイルを保存しました",
            "info.file_created" to "ファイルを作成しました: %s",
            "info.folder_created" to "フォルダーを作成しました: %s",
            "info.file_deleted" to "ファイルを削除しました: %s",
            "info.file_renamed" to "名前を変更しました: %s",
            "info.plugin_installed" to "プラグインをインストールしました: %s",
            "info.plugin_enabled" to "プラグインを有効化しました: %s",
            "info.plugin_disabled" to "プラグインを無効化しました: %s",
            "info.language_changed" to "言語を %s に変更しました",
            "info.theme_changed" to "テーマを %s に変更しました",
            "info.ready" to "準備完了",
            "info.loading" to "読み込み中...",
            "info.completed" to "完了",
            "info.cancelled" to "キャンセル"
        )
    }
}