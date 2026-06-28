package com.vscode.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import com.vscode.android.core.editor.EditorEngine
import com.vscode.android.core.project.FileSystemManager
import com.vscode.android.core.project.ProjectManager
import com.vscode.android.core.project.WorkspaceManager
import com.vscode.android.core.terminal.TermuxBridgeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class VSCodeApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var pluginManager: PluginManager
        private set
    lateinit var i18nManager: I18nManager
        private set
    lateinit var themeManager: ThemeManager
        private set
    lateinit var editorEngine: EditorEngine
        private set
    lateinit var terminalManager: TerminalManager
        private set
    lateinit var projectManager: ProjectManager
        private set
    lateinit var fileSystemManager: FileSystemManager
        private set
    lateinit var workspaceManager: WorkspaceManager
        private set
    lateinit var preferences: SharedPreferences
        private set

    private var termuxBridgeInitialized = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        setDefaultLanguage()

        themeManager = ThemeManager(this)
        themeManager.initialize()

        i18nManager = I18nManager(this)
        i18nManager.initialize(currentLanguage())

        pluginManager = PluginManager(this)
        pluginManager.initialize()

        editorEngine = EditorEngine(this)
        editorEngine.initialize()

        fileSystemManager = FileSystemManager(this)
        workspaceManager = WorkspaceManager(this)
        projectManager = ProjectManager(this, fileSystemManager, workspaceManager)
        projectManager.initialize()

        terminalManager = TerminalManager(this)
        terminalManager.initialize()

        applicationScope.launch(Dispatchers.IO) {
            initializeTermuxBridge()
        }
    }

    private fun setDefaultLanguage() {
        val savedLanguage = preferences.getString(KEY_LANGUAGE, null)
        if (savedLanguage == null) {
            val systemLocale = Locale.getDefault().language
            val defaultLang = when {
                systemLocale.startsWith("zh") -> "zh"
                systemLocale.startsWith("ja") -> "ja"
                else -> "en"
            }
            preferences.edit().putString(KEY_LANGUAGE, defaultLang).apply()
        }
    }

    fun currentLanguage(): String {
        return preferences.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun setLanguage(language: String) {
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
        i18nManager.initialize(language)
        val locale = when (language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "ja" -> Locale.JAPANESE
            else -> Locale.ENGLISH
        }
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private suspend fun initializeTermuxBridge() {
        try {
            termuxBridgeInitialized = TermuxBridgeService.initialize(this)
            if (termuxBridgeInitialized) {
                terminalManager.onTermuxBridgeReady()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize Termux bridge", e)
        }
    }

    fun isTermuxBridgeReady(): Boolean = termuxBridgeInitialized

    override fun onTerminate() {
        super.onTerminate()
        editorEngine.shutdown()
        terminalManager.shutdown()
        pluginManager.shutdown()
        projectManager.shutdown()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeManager.applyTheme()
    }

    inner class PluginManager(private val context: Context) {

        private val plugins = mutableMapOf<String, PluginInfo>()
        private val enabledPlugins = mutableSetOf<String>()

        fun initialize() {
            val savedEnabled = preferences.getStringSet(KEY_ENABLED_PLUGINS, emptySet())
            enabledPlugins.addAll(savedEnabled ?: emptySet())
            loadBuiltinPlugins()
        }

        private fun loadBuiltinPlugins() {
            registerPlugin(PluginInfo(
                id = "vscode.python",
                name = "Python",
                version = "1.0.0",
                description = "Python language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.java",
                name = "Java",
                version = "1.0.0",
                description = "Java language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.kotlin",
                name = "Kotlin",
                version = "1.0.0",
                description = "Kotlin language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.javascript",
                name = "JavaScript",
                version = "1.0.0",
                description = "JavaScript language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.typescript",
                name = "TypeScript",
                version = "1.0.0",
                description = "TypeScript language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.cpp",
                name = "C/C++",
                version = "1.0.0",
                description = "C/C++ language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.html",
                name = "HTML",
                version = "1.0.0",
                description = "HTML language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.css",
                name = "CSS",
                version = "1.0.0",
                description = "CSS language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.json",
                name = "JSON",
                version = "1.0.0",
                description = "JSON language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.xml",
                name = "XML",
                version = "1.0.0",
                description = "XML language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.markdown",
                name = "Markdown",
                version = "1.0.0",
                description = "Markdown language support",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.git",
                name = "Git",
                version = "1.0.0",
                description = "Git integration",
                isBuiltin = true
            ))
            registerPlugin(PluginInfo(
                id = "vscode.terminal",
                name = "Terminal",
                version = "1.0.0",
                description = "Integrated terminal",
                isBuiltin = true
            ))
        }

        fun registerPlugin(plugin: PluginInfo) {
            plugins[plugin.id] = plugin
            if (!preferences.contains("plugin_${plugin.id}_enabled")) {
                enabledPlugins.add(plugin.id)
            }
        }

        fun getPlugin(id: String): PluginInfo? = plugins[id]

        fun getAllPlugins(): List<PluginInfo> = plugins.values.toList()

        fun getEnabledPlugins(): List<PluginInfo> =
            plugins.values.filter { enabledPlugins.contains(it.id) }

        fun isPluginEnabled(id: String): Boolean = enabledPlugins.contains(id)

        fun enablePlugin(id: String) {
            enabledPlugins.add(id)
            preferences.edit().putBoolean("plugin_${id}_enabled", true).apply()
            saveEnabledPlugins()
        }

        fun disablePlugin(id: String) {
            enabledPlugins.remove(id)
            preferences.edit().putBoolean("plugin_${id}_enabled", false).apply()
            saveEnabledPlugins()
        }

        private fun saveEnabledPlugins() {
            preferences.edit().putStringSet(KEY_ENABLED_PLUGINS, enabledPlugins).apply()
        }

        fun getPluginsForLanguage(languageId: String): List<PluginInfo> {
            val languagePluginIds = mapOf(
                "python" to "vscode.python",
                "java" to "vscode.java",
                "kotlin" to "vscode.kotlin",
                "javascript" to "vscode.javascript",
                "typescript" to "vscode.typescript",
                "cpp" to "vscode.cpp",
                "c" to "vscode.cpp",
                "html" to "vscode.html",
                "css" to "vscode.css",
                "json" to "vscode.json",
                "xml" to "vscode.xml",
                "markdown" to "vscode.markdown"
            )
            val pluginId = languagePluginIds[languageId]
            return if (pluginId != null) listOfNotNull(plugins[pluginId]) else emptyList()
        }

        fun shutdown() {
            plugins.clear()
            enabledPlugins.clear()
        }
    }

    data class PluginInfo(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val isBuiltin: Boolean = false,
        val author: String = "",
        val iconUrl: String = ""
    )

    inner class I18nManager(private val context: Context) {

        private val strings = mutableMapOf<String, String>()
        private var currentLanguage: String = "en"

        fun initialize(language: String) {
            currentLanguage = language
            strings.clear()
            loadBuiltinStrings(language)
        }

        private fun loadBuiltinStrings(language: String) {
            val baseStrings = mapOf(
                "app_name" to "Android VSCode",
                "menu_new_file" to "New File",
                "menu_new_folder" to "New Folder",
                "menu_open_file" to "Open File",
                "menu_open_folder" to "Open Folder",
                "menu_save" to "Save",
                "menu_save_as" to "Save As...",
                "menu_save_all" to "Save All",
                "menu_undo" to "Undo",
                "menu_redo" to "Redo",
                "menu_cut" to "Cut",
                "menu_copy" to "Copy",
                "menu_paste" to "Paste",
                "menu_select_all" to "Select All",
                "menu_find" to "Find",
                "menu_replace" to "Replace",
                "menu_settings" to "Settings",
                "menu_command_palette" to "Command Palette...",
                "dialog_confirm_delete_title" to "Confirm Delete",
                "dialog_confirm_delete_message" to "Are you sure you want to delete \"%s\"?",
                "dialog_save_changes_title" to "Save Changes",
                "dialog_save_changes_message" to "Do you want to save changes to \"%s\"?",
                "dialog_unsaved_changes" to "There are unsaved changes. Do you want to save them?",
                "dialog_yes" to "Yes",
                "dialog_no" to "No",
                "dialog_cancel" to "Cancel",
                "dialog_save" to "Save",
                "dialog_dont_save" to "Don't Save",
                "dialog_ok" to "OK",
                "dialog_close" to "Close",
                "editor_welcome" to "Welcome to Android VSCode",
                "editor_no_file_open" to "No file is currently open",
                "editor_untitled" to "Untitled",
                "editor_modified" to "Modified",
                "editor_readonly" to "Read Only",
                "sidebar_no_folder_open" to "You have not yet opened a folder.",
                "sidebar_open_folder_button" to "Open Folder",
                "search_find" to "Search",
                "search_replace" to "Replace",
                "search_find_in_files" to "Find in Files",
                "search_replace_in_files" to "Replace in Files",
                "search_match_case" to "Match Case",
                "search_whole_word" to "Whole Word",
                "search_regex" to "Use Regular Expression",
                "search_no_results" to "No results found.",
                "search_result_count" to "%d results in %d files",
                "scm_changes" to "Changes",
                "scm_staged_changes" to "Staged Changes",
                "scm_no_changes" to "No changes detected.",
                "scm_commit" to "Commit",
                "scm_initialize_repo" to "Initialize Repository",
                "extensions_search" to "Search Extensions",
                "extensions_installed" to "Installed",
                "extensions_recommended" to "Recommended",
                "extensions_no_installed" to "No extensions installed.",
                "extensions_install" to "Install",
                "extensions_uninstall" to "Uninstall",
                "extensions_enable" to "Enable",
                "extensions_disable" to "Disable",
                "debug_run" to "Run",
                "debug_run_and_debug" to "Run and Debug",
                "debug_no_configuration" to "No debug configuration found.",
                "debug_create_launch_json" to "Create a launch.json file",
                "debug_start" to "Start Debugging",
                "debug_stop" to "Stop",
                "debug_pause" to "Pause",
                "debug_continue" to "Continue",
                "debug_step_over" to "Step Over",
                "debug_step_into" to "Step Into",
                "debug_step_out" to "Step Out",
                "debug_restart" to "Restart",
                "terminal_new" to "New Terminal",
                "terminal_kill" to "Kill Terminal",
                "terminal_clear" to "Clear Terminal",
                "terminal_select_all" to "Select All",
                "terminal_copy" to "Copy",
                "terminal_paste" to "Paste",
                "close" to "Close",
                "close_all" to "Close All",
                "close_others" to "Close Others",
                "close_to_right" to "Close to the Right",
                "close_saved" to "Close Saved",
                "toggle_panel" to "Toggle Panel",
                "toggle_sidebar" to "Toggle Sidebar",
                "toggle_fullscreen" to "Toggle Full Screen",
                "status_bar_branch" to "main",
                "status_bar_encoding" to "UTF-8",
                "status_bar_line_endings" to "LF",
                "status_bar_language" to "Plain Text",
                "status_bar_feedback" to "Feedback",
                "status_bar_notifications" to "Notifications",
                "activity_bar_explorer" to "Explorer",
                "activity_bar_search" to "Search",
                "activity_bar_source_control" to "Source Control",
                "activity_bar_extensions" to "Extensions",
                "activity_bar_debug" to "Run and Debug",
                "panel_problems" to "Problems",
                "panel_output" to "Output",
                "panel_terminal" to "Terminal",
                "panel_debug_console" to "Debug Console"
            )

            val zhStrings = mapOf(
                "app_name" to "Android VSCode",
                "menu_new_file" to "新建文件",
                "menu_new_folder" to "新建文件夹",
                "menu_open_file" to "打开文件",
                "menu_open_folder" to "打开文件夹",
                "menu_save" to "保存",
                "menu_save_as" to "另存为...",
                "menu_save_all" to "全部保存",
                "menu_undo" to "撤销",
                "menu_redo" to "重做",
                "menu_cut" to "剪切",
                "menu_copy" to "复制",
                "menu_paste" to "粘贴",
                "menu_select_all" to "全选",
                "menu_find" to "查找",
                "menu_replace" to "替换",
                "menu_settings" to "设置",
                "menu_command_palette" to "命令面板...",
                "dialog_confirm_delete_title" to "确认删除",
                "dialog_confirm_delete_message" to "确定要删除 \"%s\" 吗？",
                "dialog_save_changes_title" to "保存更改",
                "dialog_save_changes_message" to "是否保存对 \"%s\" 的更改？",
                "dialog_unsaved_changes" to "有未保存的更改，是否保存？",
                "dialog_yes" to "是",
                "dialog_no" to "否",
                "dialog_cancel" to "取消",
                "dialog_save" to "保存",
                "dialog_dont_save" to "不保存",
                "dialog_ok" to "确定",
                "dialog_close" to "关闭",
                "editor_welcome" to "欢迎使用 Android VSCode",
                "editor_no_file_open" to "当前没有打开的文件",
                "editor_untitled" to "未命名",
                "editor_modified" to "已修改",
                "editor_readonly" to "只读",
                "sidebar_no_folder_open" to "您尚未打开任何文件夹。",
                "sidebar_open_folder_button" to "打开文件夹",
                "search_find" to "搜索",
                "search_replace" to "替换",
                "search_find_in_files" to "在文件中查找",
                "search_replace_in_files" to "在文件中替换",
                "search_match_case" to "区分大小写",
                "search_whole_word" to "全词匹配",
                "search_regex" to "使用正则表达式",
                "search_no_results" to "未找到结果。",
                "search_result_count" to "在 %2$d 个文件中找到 %1$d 个结果",
                "scm_changes" to "更改",
                "scm_staged_changes" to "暂存的更改",
                "scm_no_changes" to "未检测到更改。",
                "scm_commit" to "提交",
                "scm_initialize_repo" to "初始化仓库",
                "extensions_search" to "搜索扩展",
                "extensions_installed" to "已安装",
                "extensions_recommended" to "推荐",
                "extensions_no_installed" to "未安装扩展。",
                "extensions_install" to "安装",
                "extensions_uninstall" to "卸载",
                "extensions_enable" to "启用",
                "extensions_disable" to "禁用",
                "debug_run" to "运行",
                "debug_run_and_debug" to "运行和调试",
                "debug_no_configuration" to "未找到调试配置。",
                "debug_create_launch_json" to "创建 launch.json 文件",
                "debug_start" to "开始调试",
                "debug_stop" to "停止",
                "debug_pause" to "暂停",
                "debug_continue" to "继续",
                "debug_step_over" to "单步跳过",
                "debug_step_into" to "单步进入",
                "debug_step_out" to "单步跳出",
                "debug_restart" to "重新启动",
                "terminal_new" to "新建终端",
                "terminal_kill" to "终止终端",
                "terminal_clear" to "清除终端",
                "terminal_select_all" to "全选",
                "terminal_copy" to "复制",
                "terminal_paste" to "粘贴",
                "close" to "关闭",
                "close_all" to "全部关闭",
                "close_others" to "关闭其他",
                "close_to_right" to "关闭右侧",
                "close_saved" to "关闭已保存",
                "toggle_panel" to "切换面板",
                "toggle_sidebar" to "切换侧边栏",
                "toggle_fullscreen" to "切换全屏",
                "status_bar_branch" to "main",
                "status_bar_encoding" to "UTF-8",
                "status_bar_line_endings" to "LF",
                "status_bar_language" to "纯文本",
                "status_bar_feedback" to "反馈",
                "status_bar_notifications" to "通知",
                "activity_bar_explorer" to "资源管理器",
                "activity_bar_search" to "搜索",
                "activity_bar_source_control" to "源代码管理",
                "activity_bar_extensions" to "扩展",
                "activity_bar_debug" to "运行和调试",
                "panel_problems" to "问题",
                "panel_output" to "输出",
                "panel_terminal" to "终端",
                "panel_debug_console" to "调试控制台"
            )

            val jaStrings = mapOf(
                "app_name" to "Android VSCode",
                "menu_new_file" to "新しいファイル",
                "menu_new_folder" to "新しいフォルダー",
                "menu_open_file" to "ファイルを開く",
                "menu_open_folder" to "フォルダーを開く",
                "menu_save" to "保存",
                "menu_save_as" to "名前を付けて保存...",
                "menu_save_all" to "すべて保存",
                "menu_undo" to "元に戻す",
                "menu_redo" to "やり直す",
                "menu_cut" to "切り取り",
                "menu_copy" to "コピー",
                "menu_paste" to "貼り付け",
                "menu_select_all" to "すべて選択",
                "menu_find" to "検索",
                "menu_replace" to "置換",
                "menu_settings" to "設定",
                "menu_command_palette" to "コマンドパレット...",
                "dialog_confirm_delete_title" to "削除の確認",
                "dialog_confirm_delete_message" to "\"%s\" を削除してもよろしいですか？",
                "dialog_save_changes_title" to "変更を保存",
                "dialog_save_changes_message" to "\"%s\" への変更を保存しますか？",
                "dialog_unsaved_changes" to "未保存の変更があります。保存しますか？",
                "dialog_yes" to "はい",
                "dialog_no" to "いいえ",
                "dialog_cancel" to "キャンセル",
                "dialog_save" to "保存",
                "dialog_dont_save" to "保存しない",
                "dialog_ok" to "OK",
                "dialog_close" to "閉じる",
                "editor_welcome" to "Android VSCode へようこそ",
                "editor_no_file_open" to "ファイルが開かれていません",
                "editor_untitled" to "無題",
                "editor_modified" to "変更済み",
                "editor_readonly" to "読み取り専用",
                "sidebar_no_folder_open" to "まだフォルダーが開かれていません。",
                "sidebar_open_folder_button" to "フォルダーを開く",
                "search_find" to "検索",
                "search_replace" to "置換",
                "search_find_in_files" to "ファイル内検索",
                "search_replace_in_files" to "ファイル内置換",
                "search_match_case" to "大文字/小文字を区別",
                "search_whole_word" to "単語単位",
                "search_regex" to "正規表現",
                "search_no_results" to "結果が見つかりません。",
                "search_result_count" to "%2$d ファイル中 %1$d 件の結果",
                "scm_changes" to "変更",
                "scm_staged_changes" to "ステージング済みの変更",
                "scm_no_changes" to "変更は検出されませんでした。",
                "scm_commit" to "コミット",
                "scm_initialize_repo" to "リポジトリを初期化",
                "extensions_search" to "拡張機能を検索",
                "extensions_installed" to "インストール済み",
                "extensions_recommended" to "おすすめ",
                "extensions_no_installed" to "拡張機能がインストールされていません。",
                "extensions_install" to "インストール",
                "extensions_uninstall" to "アンインストール",
                "extensions_enable" to "有効化",
                "extensions_disable" to "無効化",
                "debug_run" to "実行",
                "debug_run_and_debug" to "実行とデバッグ",
                "debug_no_configuration" to "デバッグ構成が見つかりません。",
                "debug_create_launch_json" to "launch.json ファイルを作成",
                "debug_start" to "デバッグ開始",
                "debug_stop" to "停止",
                "debug_pause" to "一時停止",
                "debug_continue" to "続行",
                "debug_step_over" to "ステップオーバー",
                "debug_step_into" to "ステップイン",
                "debug_step_out" to "ステップアウト",
                "debug_restart" to "再起動",
                "terminal_new" to "新しいターミナル",
                "terminal_kill" to "ターミナルを終了",
                "terminal_clear" to "ターミナルをクリア",
                "terminal_select_all" to "すべて選択",
                "terminal_copy" to "コピー",
                "terminal_paste" to "貼り付け",
                "close" to "閉じる",
                "close_all" to "すべて閉じる",
                "close_others" to "その他を閉じる",
                "close_to_right" to "右側を閉じる",
                "close_saved" to "保存済みを閉じる",
                "toggle_panel" to "パネル切替",
                "toggle_sidebar" to "サイドバー切替",
                "toggle_fullscreen" to "全画面切替",
                "status_bar_branch" to "main",
                "status_bar_encoding" to "UTF-8",
                "status_bar_line_endings" to "LF",
                "status_bar_language" to "プレーンテキスト",
                "status_bar_feedback" to "フィードバック",
                "status_bar_notifications" to "通知",
                "activity_bar_explorer" to "エクスプローラー",
                "activity_bar_search" to "検索",
                "activity_bar_source_control" to "ソース管理",
                "activity_bar_extensions" to "拡張機能",
                "activity_bar_debug" to "実行とデバッグ",
                "panel_problems" to "問題",
                "panel_output" to "出力",
                "panel_terminal" to "ターミナル",
                "panel_debug_console" to "デバッグコンソール"
            )

            strings.putAll(baseStrings)
            when (language) {
                "zh" -> strings.putAll(zhStrings)
                "ja" -> strings.putAll(jaStrings)
            }
        }

        fun getString(key: String, vararg args: Any?): String {
            val template = strings[key] ?: key
            return if (args.isEmpty()) {
                template
            } else {
                String.format(template, *args)
            }
        }

        fun getLanguage(): String = currentLanguage

        fun getAvailableLanguages(): List<Pair<String, String>> = listOf(
            "en" to "English",
            "zh" to "简体中文",
            "ja" to "日本語"
        )
    }

    inner class ThemeManager(private val context: Context) {

        enum class ThemeMode { DARK, LIGHT, HIGH_CONTRAST }

        data class ThemeColors(
            val background: Int = 0xFF1E1E1E.toInt(),
            val foreground: Int = 0xFFD4D4D4.toInt(),
            val sidebarBackground: Int = 0xFF252526.toInt(),
            val activityBarBackground: Int = 0xFF333333.toInt(),
            val editorBackground: Int = 0xFF1E1E1E.toInt(),
            val statusBarBackground: Int = 0xFF007ACC.toInt(),
            val terminalBackground: Int = 0xFF1E1E1E.toInt(),
            val tabBackground: Int = 0xFF2D2D2D.toInt(),
            val panelBackground: Int = 0xFF1E1E1E.toInt(),
            val accentBlue: Int = 0xFF007ACC.toInt(),
            val accentGreen: Int = 0xFF6A9955.toInt(),
            val accentYellow: Int = 0xFFDCDCAA.toInt(),
            val accentRed: Int = 0xFFF44747.toInt(),
            val accentPurple: Int = 0xFFC586C0.toInt(),
            val accentCyan: Int = 0xFF4EC9B0.toInt(),
            val accentOrange: Int = 0xFFCE9178.toInt(),
            val textPrimary: Int = 0xFFCCCCCC.toInt(),
            val textSecondary: Int = 0xFF969696.toInt(),
            val lineNumber: Int = 0xFF858585.toInt(),
            val activeLineNumber: Int = 0xFFC6C6C6.toInt(),
            val cursor: Int = 0xFFAEAFAD.toInt(),
            val selection: Int = 0xFF264F78.toInt(),
            val lineHighlight: Int = 0xFF2A2D2E.toInt(),
            val bracketMatch: Int = 0xFFFFFF00.toInt(),
            val keyword: Int = 0xFF569CD6.toInt(),
            val string: Int = 0xFFCE9178.toInt(),
            val number: Int = 0xFFB5CEA8.toInt(),
            val comment: Int = 0xFF6A9955.toInt(),
            val type: Int = 0xFF4EC9B0.toInt(),
            val function: Int = 0xFFDCDCAA.toInt(),
            val variable: Int = 0xFF9CDCFE.toInt(),
            val constant: Int = 0xFF4FC1FF.toInt(),
            val operator: Int = 0xFFD4D4D4.toInt(),
            val className: Int = 0xFF4EC9B0.toInt(),
            val parameter: Int = 0xFF9CDCFE.toInt(),
            val property: Int = 0xFF9CDCFE.toInt(),
            val regex: Int = 0xFFD16969.toInt(),
            val tag: Int = 0xFF569CD6.toInt(),
            val attributeName: Int = 0xFF9CDCFE.toInt(),
            val attributeValue: Int = 0xFFCE9178.toInt()
        )

        private var currentTheme = ThemeMode.DARK
        private var currentColors = ThemeColors()

        fun initialize() {
            val savedTheme = preferences.getString(KEY_THEME, "dark") ?: "dark"
            currentTheme = when (savedTheme) {
                "light" -> ThemeMode.LIGHT
                "high_contrast" -> ThemeMode.HIGH_CONTRAST
                else -> ThemeMode.DARK
            }
            applyTheme()
        }

        fun applyTheme() {
            currentColors = when (currentTheme) {
                ThemeMode.DARK -> ThemeColors()
                ThemeMode.LIGHT -> ThemeColors(
                    background = 0xFFFFFFFF.toInt(),
                    foreground = 0xFF333333.toInt(),
                    sidebarBackground = 0xFFF3F3F3.toInt(),
                    activityBarBackground = 0xFFECECEC.toInt(),
                    editorBackground = 0xFFFFFFFF.toInt(),
                    statusBarBackground = 0xFF007ACC.toInt(),
                    terminalBackground = 0xFFFFFFFF.toInt(),
                    tabBackground = 0xFFECECEC.toInt(),
                    panelBackground = 0xFFFFFFFF.toInt(),
                    textPrimary = 0xFF333333.toInt(),
                    textSecondary = 0xFF717171.toInt(),
                    lineNumber = 0xFF999999.toInt(),
                    activeLineNumber = 0xFF333333.toInt(),
                    cursor = 0xFF333333.toInt(),
                    selection = 0xFFADD6FF.toInt(),
                    lineHighlight = 0xFFF0F0F0.toInt(),
                    keyword = 0xFF0000FF.toInt(),
                    string = 0xFFA31515.toInt(),
                    number = 0xFF098658.toInt(),
                    comment = 0xFF008000.toInt(),
                    type = 0xFF267F99.toInt(),
                    function = 0xFF795E26.toInt(),
                    variable = 0xFF001080.toInt(),
                    className = 0xFF267F99.toInt(),
                    tag = 0xFF800000.toInt(),
                    attributeName = 0xFFE50000.toInt(),
                    attributeValue = 0xFF0451A5.toInt()
                )
                ThemeMode.HIGH_CONTRAST -> ThemeColors(
                    background = 0xFF000000.toInt(),
                    foreground = 0xFFFFFFFF.toInt(),
                    sidebarBackground = 0xFF000000.toInt(),
                    activityBarBackground = 0xFF000000.toInt(),
                    editorBackground = 0xFF000000.toInt(),
                    statusBarBackground = 0xFF000000.toInt(),
                    terminalBackground = 0xFF000000.toInt(),
                    tabBackground = 0xFF000000.toInt(),
                    panelBackground = 0xFF000000.toInt(),
                    keyword = 0xFF569CD6.toInt(),
                    string = 0xFFCE9178.toInt(),
                    number = 0xFFB5CEA8.toInt(),
                    comment = 0xFF6A9955.toInt(),
                    type = 0xFF4EC9B0.toInt(),
                    function = 0xFFDCDCAA.toInt(),
                    variable = 0xFF9CDCFE.toInt()
                )
            }
        }

        fun getColors(): ThemeColors = currentColors

        fun getThemeMode(): ThemeMode = currentTheme

        fun setThemeMode(mode: ThemeMode) {
            currentTheme = mode
            preferences.edit().putString(KEY_THEME, mode.name.lowercase()).apply()
            applyTheme()
        }

        fun getColorForToken(tokenType: SyntaxTokenType): Int {
            return when (tokenType) {
                SyntaxTokenType.KEYWORD -> currentColors.keyword
                SyntaxTokenType.STRING -> currentColors.string
                SyntaxTokenType.NUMBER -> currentColors.number
                SyntaxTokenType.COMMENT -> currentColors.comment
                SyntaxTokenType.TYPE -> currentColors.type
                SyntaxTokenType.FUNCTION -> currentColors.function
                SyntaxTokenType.VARIABLE -> currentColors.variable
                SyntaxTokenType.CONSTANT -> currentColors.constant
                SyntaxTokenType.OPERATOR -> currentColors.operator
                SyntaxTokenType.CLASS_NAME -> currentColors.className
                SyntaxTokenType.PARAMETER -> currentColors.parameter
                SyntaxTokenType.PROPERTY -> currentColors.property
                SyntaxTokenType.REGEX -> currentColors.regex
                SyntaxTokenType.TAG -> currentColors.tag
                SyntaxTokenType.ATTRIBUTE_NAME -> currentColors.attributeName
                SyntaxTokenType.ATTRIBUTE_VALUE -> currentColors.attributeValue
                SyntaxTokenType.PLAIN -> currentColors.foreground
            }
        }
    }

    enum class SyntaxTokenType {
        KEYWORD, STRING, NUMBER, COMMENT, TYPE, FUNCTION,
        VARIABLE, CONSTANT, OPERATOR, CLASS_NAME, PARAMETER,
        PROPERTY, REGEX, TAG, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, PLAIN
    }

    inner class TerminalManager(private val context: Context) {

        private val terminals = mutableListOf<TerminalSession>()
        private var activeTerminalIndex = 0

        fun initialize() {
            terminals.clear()
        }

        fun createTerminal(name: String = "Terminal"): TerminalSession {
            val session = TerminalSession(
                id = System.currentTimeMillis().toString(),
                name = name
            )
            terminals.add(session)
            activeTerminalIndex = terminals.size - 1
            return session
        }

        fun getActiveTerminal(): TerminalSession? =
            if (terminals.isNotEmpty() && activeTerminalIndex < terminals.size)
                terminals[activeTerminalIndex] else null

        fun getTerminal(id: String): TerminalSession? =
            terminals.find { it.id == id }

        fun getAllTerminals(): List<TerminalSession> = terminals.toList()

        fun setActiveTerminal(index: Int) {
            if (index in terminals.indices) {
                activeTerminalIndex = index
            }
        }

        fun killTerminal(id: String) {
            val terminal = terminals.find { it.id == id } ?: return
            terminal.close()
            terminals.remove(terminal)
            if (activeTerminalIndex >= terminals.size) {
                activeTerminalIndex = (terminals.size - 1).coerceAtLeast(0)
            }
        }

        fun killAllTerminals() {
            terminals.forEach { it.close() }
            terminals.clear()
            activeTerminalIndex = 0
        }

        fun onTermuxBridgeReady() {
            terminals.forEach { it.onBridgeReady() }
        }

        fun shutdown() {
            killAllTerminals()
        }
    }

    data class TerminalSession(
        val id: String,
        val name: String,
        val isActive: Boolean = true
    ) {
        private val outputBuffer = StringBuilder()
        private var bridgeReady = false

        fun write(text: String) {
            outputBuffer.append(text)
        }

        fun writeln(text: String) {
            outputBuffer.append(text).append('\n')
        }

        fun getOutput(): String = outputBuffer.toString()

        fun clear() {
            outputBuffer.clear()
        }

        fun executeCommand(command: String): String {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                process.waitFor()
                val result = if (output.isNotEmpty()) output else error
                outputBuffer.append("$ $command\n").append(result)
                if (!result.endsWith("\n")) outputBuffer.append("\n")
                return result
            } catch (e: Exception) {
                val errorMsg = "Error executing command: ${e.message}"
                outputBuffer.append("$ $command\n").append(errorMsg).append("\n")
                return errorMsg
            }
        }

        fun onBridgeReady() {
            bridgeReady = true
        }

        fun close() {
            outputBuffer.clear()
        }
    }

    companion object {
        private const val TAG = "VSCodeApp"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_THEME = "app_theme"
        private const val KEY_ENABLED_PLUGINS = "enabled_plugins"

        lateinit var instance: VSCodeApp
            private set

        fun getInstance(): VSCodeApp = instance
    }
}