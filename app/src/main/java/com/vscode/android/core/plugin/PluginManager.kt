package com.vscode.android.core.plugin

import android.content.Context
import android.content.SharedPreferences
import com.vscode.android.VSCodeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String = "",
    var enabled: Boolean = true,
    val builtIn: Boolean = false,
    val extensionPoints: MutableList<PluginExtensionPoint> = mutableListOf(),
    val contributes: MutableMap<String, Any> = mutableMapOf()
)

data class PluginCommand(
    val id: String,
    val title: String,
    val category: String = "",
    val keybinding: String = "",
    val handler: suspend (Map<String, Any>?) -> Unit
)

data class PluginMenuContribution(
    val id: String,
    val command: String,
    val title: String,
    val whenClause: String = "",
    val group: String = "",
    val icon: String = ""
)

data class MarketplacePlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val publisher: String,
    val iconUrl: String = "",
    val downloadUrl: String = "",
    val rating: Float = 0f,
    val downloadCount: Int = 0,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val isInstalled: Boolean = false
)

interface PluginExtensionPoint {
    val pluginId: String
    suspend fun onActivate(context: PluginActivationContext)
    suspend fun onDeactivate()
    fun getCommands(): List<PluginCommand>
    fun getLanguages(): List<String>
    fun getMenus(): List<PluginMenuContribution>
}

data class PluginActivationContext(
    val plugin: PluginInfo,
    val api: PluginAPI,
    val context: Context
)

class PluginManager(private val context: Context) {

    private val plugins = ConcurrentHashMap<String, PluginInfo>()
    private val enabledPlugins = ConcurrentHashMap<String, Boolean>()
    private val extensionPoints = ConcurrentHashMap<String, MutableList<PluginExtensionPoint>>()
    private val commands = ConcurrentHashMap<String, PluginCommand>()
    private val menuContributions = mutableListOf<PluginMenuContribution>()
    private val pluginLoader = PluginLoader(context)
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = mutableListOf<PluginManagerListener>()
    private val marketplace = mutableListOf<MarketplacePlugin>()
    private var pluginAPI: PluginAPI? = null

    fun initialize() {
        val savedEnabled = preferences.getStringSet(KEY_ENABLED_PLUGINS, emptySet())
        savedEnabled?.forEach { id -> enabledPlugins[id] = true }
        loadBuiltinPlugins()
        registerBuiltinExtensionPoints()
        loadMarketplaceCatalog()
        loadPluginStates()
        notifyPluginsLoaded()
    }

    fun setPluginAPI(api: PluginAPI) {
        this.pluginAPI = api
    }

    fun getPluginAPI(): PluginAPI? = pluginAPI

    private fun loadBuiltinPlugins() {
        registerPlugin(PluginInfo(
            id = "vscode.python",
            name = "Python",
            description = "Python language support with syntax highlighting, code completion, and debugging",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "python", "extensions" to listOf(".py", ".pyw", ".pyi"))),
                "grammars" to emptyList<Any>(),
                "snippets" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.java",
            name = "Java",
            description = "Java language support with code completion, Gradle/Maven integration",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "java", "extensions" to listOf(".java", ".jav"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.cpp",
            name = "C/C++",
            description = "C/C++ language support with CMake/Makefile integration",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "cpp", "extensions" to listOf(".cpp", ".cc", ".cxx", ".c++", ".hpp", ".hxx", ".h"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.kotlin",
            name = "Kotlin",
            description = "Kotlin language support with Android development integration",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "kotlin", "extensions" to listOf(".kt", ".kts"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.javascript",
            name = "JavaScript",
            description = "JavaScript language support with ES6+ syntax",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "javascript", "extensions" to listOf(".js", ".jsx", ".mjs", ".cjs"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.typescript",
            name = "TypeScript",
            description = "TypeScript language support with type checking",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "typescript", "extensions" to listOf(".ts", ".tsx", ".mts", ".cts"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.html",
            name = "HTML",
            description = "HTML language support with Emmet and CSS integration",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "html", "extensions" to listOf(".html", ".htm", ".shtml"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.css",
            name = "CSS",
            description = "CSS/SCSS/Less language support",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "css", "extensions" to listOf(".css", ".scss", ".less"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.json",
            name = "JSON",
            description = "JSON language support with schema validation",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "json", "extensions" to listOf(".json", ".jsonc", ".json5"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.xml",
            name = "XML",
            description = "XML language support with XSD validation",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "xml", "extensions" to listOf(".xml", ".xsl", ".xsd", ".plist", ".svg"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.markdown",
            name = "Markdown",
            description = "Markdown language support with preview",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "languages" to listOf(mapOf("id" to "markdown", "extensions" to listOf(".md", ".markdown", ".mdown", ".mkd"))),
                "grammars" to emptyList<Any>()
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.git",
            name = "Git",
            description = "Git integration with source control features",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "commands" to listOf(
                    mapOf("command" to "git.init", "title" to "Git: Initialize Repository"),
                    mapOf("command" to "git.commit", "title" to "Git: Commit"),
                    mapOf("command" to "git.push", "title" to "Git: Push"),
                    mapOf("command" to "git.pull", "title" to "Git: Pull"),
                    mapOf("command" to "git.branch", "title" to "Git: Create Branch"),
                    mapOf("command" to "git.stage", "title" to "Git: Stage Changes"),
                    mapOf("command" to "git.unstage", "title" to "Git: Unstage Changes"),
                    mapOf("command" to "git.discard", "title" to "Git: Discard Changes")
                ),
                "menus" to listOf(
                    mapOf("command" to "git.commit", "group" to "navigation"),
                    mapOf("command" to "git.push", "group" to "navigation"),
                    mapOf("command" to "git.pull", "group" to "navigation"),
                    mapOf("command" to "git.branch", "group" to "navigation")
                )
            )
        ))

        registerPlugin(PluginInfo(
            id = "vscode.terminal",
            name = "Terminal",
            description = "Integrated terminal with shell support",
            version = "1.0.0",
            author = "Android VSCode Team",
            builtIn = true,
            extensionPoints = mutableListOf(),
            contributes = mutableMapOf(
                "commands" to listOf(
                    mapOf("command" to "terminal.new", "title" to "Terminal: Create New Terminal"),
                    mapOf("command" to "terminal.kill", "title" to "Terminal: Kill Terminal"),
                    mapOf("command" to "terminal.clear", "title" to "Terminal: Clear"),
                    mapOf("command" to "terminal.split", "title" to "Terminal: Split Terminal")
                ),
                "menus" to listOf(
                    mapOf("command" to "terminal.new", "group" to "terminal"),
                    mapOf("command" to "terminal.kill", "group" to "terminal"),
                    mapOf("command" to "terminal.clear", "group" to "terminal")
                )
            )
        ))
    }

    private fun registerBuiltinExtensionPoints() {
        val pythonEP = BuiltinPythonExtensionPoint()
        registerExtensionPoint("vscode.python", pythonEP)
        plugins["vscode.python"]?.extensionPoints?.add(pythonEP)

        val javaEP = BuiltinJavaExtensionPoint()
        registerExtensionPoint("vscode.java", javaEP)
        plugins["vscode.java"]?.extensionPoints?.add(javaEP)

        val cppEP = BuiltinCppExtensionPoint()
        registerExtensionPoint("vscode.cpp", cppEP)
        plugins["vscode.cpp"]?.extensionPoints?.add(cppEP)

        val kotlinEP = BuiltinKotlinExtensionPoint()
        registerExtensionPoint("vscode.kotlin", kotlinEP)
        plugins["vscode.kotlin"]?.extensionPoints?.add(kotlinEP)

        val jsEP = BuiltinJavaScriptExtensionPoint()
        registerExtensionPoint("vscode.javascript", jsEP)
        plugins["vscode.javascript"]?.extensionPoints?.add(jsEP)

        val tsEP = BuiltinTypeScriptExtensionPoint()
        registerExtensionPoint("vscode.typescript", tsEP)
        plugins["vscode.typescript"]?.extensionPoints?.add(tsEP)

        val htmlEP = BuiltinHtmlExtensionPoint()
        registerExtensionPoint("vscode.html", htmlEP)
        plugins["vscode.html"]?.extensionPoints?.add(htmlEP)

        val cssEP = BuiltinCssExtensionPoint()
        registerExtensionPoint("vscode.css", cssEP)
        plugins["vscode.css"]?.extensionPoints?.add(cssEP)

        val jsonEP = BuiltinJsonExtensionPoint()
        registerExtensionPoint("vscode.json", jsonEP)
        plugins["vscode.json"]?.extensionPoints?.add(jsonEP)

        val xmlEP = BuiltinXmlExtensionPoint()
        registerExtensionPoint("vscode.xml", xmlEP)
        plugins["vscode.xml"]?.extensionPoints?.add(xmlEP)

        val mdEP = BuiltinMarkdownExtensionPoint()
        registerExtensionPoint("vscode.markdown", mdEP)
        plugins["vscode.markdown"]?.extensionPoints?.add(mdEP)

        val gitEP = BuiltinGitExtensionPoint()
        registerExtensionPoint("vscode.git", gitEP)
        plugins["vscode.git"]?.extensionPoints?.add(gitEP)

        val terminalEP = BuiltinTerminalExtensionPoint()
        registerExtensionPoint("vscode.terminal", terminalEP)
        plugins["vscode.terminal"]?.extensionPoints?.add(terminalEP)
    }

    private fun loadMarketplaceCatalog() {
        marketplace.addAll(listOf(
            MarketplacePlugin(
                id = "vscode.python",
                name = "Python",
                description = "Python language support with IntelliSense, debugging, and testing",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("python", "debugger", "formatter", "linter"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.java",
                name = "Java",
                description = "Java language support with Gradle and Maven integration",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("java", "gradle", "maven"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.cpp",
                name = "C/C++",
                description = "C/C++ language support with CMake integration",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("c", "cpp", "cmake", "makefile"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.kotlin",
                name = "Kotlin",
                description = "Kotlin language support for Android development",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("kotlin", "android"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.javascript",
                name = "JavaScript",
                description = "JavaScript/Node.js language support",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("javascript", "node", "npm"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.typescript",
                name = "TypeScript",
                description = "TypeScript language support with type checking",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("typescript", "javascript"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.html",
                name = "HTML",
                description = "HTML language support with Emmet",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("html", "emmet"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.css",
                name = "CSS",
                description = "CSS/SCSS/Less language support",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("css", "scss", "less"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.json",
                name = "JSON",
                description = "JSON language support with schema validation",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("json"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.xml",
                name = "XML",
                description = "XML language support",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("xml"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.markdown",
                name = "Markdown",
                description = "Markdown language support with preview",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Programming Languages",
                tags = listOf("markdown"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.git",
                name = "Git",
                description = "Git integration with source control",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Other",
                tags = listOf("git", "scm"),
                isInstalled = true
            ),
            MarketplacePlugin(
                id = "vscode.terminal",
                name = "Terminal",
                description = "Integrated terminal for Android",
                version = "1.0.0",
                author = "Android VSCode Team",
                publisher = "vscode",
                category = "Other",
                tags = listOf("terminal"),
                isInstalled = true
            )
        ))
    }

    private fun loadPluginStates() {
        for (pluginId in plugins.keys) {
            val enabled = preferences.getBoolean("plugin_${pluginId}_enabled", true)
            if (enabled) {
                enabledPlugins[pluginId] = true
                plugins[pluginId]?.enabled = true
            } else {
                enabledPlugins.remove(pluginId)
                plugins[pluginId]?.enabled = false
            }
        }
    }

    fun registerPlugin(plugin: PluginInfo) {
        plugins[plugin.id] = plugin
        if (!preferences.contains("plugin_${plugin.id}_enabled")) {
            enabledPlugins[plugin.id] = true
            plugin.enabled = true
        } else {
            val enabled = preferences.getBoolean("plugin_${plugin.id}_enabled", true)
            plugin.enabled = enabled
            if (enabled) {
                enabledPlugins[plugin.id] = true
            }
        }
        notifyPluginRegistered(plugin)
    }

    fun unregisterPlugin(pluginId: String) {
        val plugin = plugins.remove(pluginId) ?: return
        enabledPlugins.remove(pluginId)
        extensionPoints.remove(pluginId)
        commands.entries.removeAll { it.key.startsWith("$pluginId.") }
        menuContributions.removeAll { it.id.startsWith(pluginId) }
        notifyPluginUnregistered(plugin)
    }

    fun getPlugin(id: String): PluginInfo? = plugins[id]

    fun getAllPlugins(): List<PluginInfo> = plugins.values.toList()

    fun getEnabledPlugins(): List<PluginInfo> = plugins.values.filter { enabledPlugins.containsKey(it.id) }

    fun getDisabledPlugins(): List<PluginInfo> = plugins.values.filter { !enabledPlugins.containsKey(it.id) }

    fun isPluginEnabled(id: String): Boolean = enabledPlugins.containsKey(id)

    fun enablePlugin(id: String): Boolean {
        val plugin = plugins[id] ?: return false
        enabledPlugins[id] = true
        plugin.enabled = true
        preferences.edit().putBoolean("plugin_${id}_enabled", true).apply()
        saveEnabledPlugins()

        scope.launch {
            try {
                val api = pluginAPI
                if (api != null) {
                    val ctx = PluginActivationContext(plugin, api, context)
                    extensionPoints[id]?.forEach { it.onActivate(ctx) }
                }
                withContext(Dispatchers.Main) {
                    notifyPluginEnabled(plugin)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to activate plugin: $id", e)
            }
        }
        return true
    }

    fun disablePlugin(id: String): Boolean {
        val plugin = plugins[id] ?: return false
        enabledPlugins.remove(id)
        plugin.enabled = false
        preferences.edit().putBoolean("plugin_${id}_enabled", false).apply()
        saveEnabledPlugins()

        scope.launch {
            try {
                extensionPoints[id]?.forEach { it.onDeactivate() }
                withContext(Dispatchers.Main) {
                    notifyPluginDisabled(plugin)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to deactivate plugin: $id", e)
            }
        }
        return true
    }

    private fun saveEnabledPlugins() {
        preferences.edit().putStringSet(KEY_ENABLED_PLUGINS, enabledPlugins.keys.toSet()).apply()
    }

    fun loadPlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        if (!plugin.enabled) return false

        scope.launch {
            try {
                val api = pluginAPI
                if (api != null) {
                    val ctx = PluginActivationContext(plugin, api, context)
                    extensionPoints[pluginId]?.forEach { it.onActivate(ctx) }
                    withContext(Dispatchers.Main) {
                        notifyPluginLoaded(plugin)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load plugin: $pluginId", e)
            }
        }
        return true
    }

    fun unloadPlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false

        scope.launch {
            try {
                extensionPoints[pluginId]?.forEach { it.onDeactivate() }
                withContext(Dispatchers.Main) {
                    notifyPluginUnloaded(plugin)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to unload plugin: $pluginId", e)
            }
        }
        return true
    }

    fun loadAllEnabledPlugins() {
        for (pluginId in enabledPlugins.keys) {
            loadPlugin(pluginId)
        }
    }

    fun unloadAllPlugins() {
        for (pluginId in plugins.keys) {
            unloadPlugin(pluginId)
        }
    }

    fun registerExtensionPoint(pluginId: String, extensionPoint: PluginExtensionPoint) {
        extensionPoints.getOrPut(pluginId) { mutableListOf() }.add(extensionPoint)
        extensionPoint.getCommands().forEach { cmd ->
            commands["$pluginId.${cmd.id}"] = cmd
        }
        extensionPoint.getMenus().forEach { menu ->
            menuContributions.add(menu)
        }
    }

    fun unregisterExtensionPoint(pluginId: String) {
        extensionPoints.remove(pluginId)
        commands.entries.removeAll { it.key.startsWith("$pluginId.") }
        menuContributions.removeAll { it.id.startsWith(pluginId) }
    }

    fun getExtensionPoints(pluginId: String): List<PluginExtensionPoint> {
        return extensionPoints[pluginId]?.toList() ?: emptyList()
    }

    fun getAllExtensionPoints(): Map<String, List<PluginExtensionPoint>> {
        return extensionPoints.toMap()
    }

    fun registerCommand(pluginId: String, command: PluginCommand) {
        commands["$pluginId.${command.id}"] = command
    }

    fun unregisterCommand(pluginId: String, commandId: String) {
        commands.remove("$pluginId.$commandId")
    }

    suspend fun executeCommand(commandId: String, args: Map<String, Any>? = null): Boolean {
        val command = commands[commandId] ?: return false
        try {
            command.handler(args)
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to execute command: $commandId", e)
            return false
        }
    }

    fun getCommands(): List<PluginCommand> = commands.values.toList()

    fun getCommandsForPlugin(pluginId: String): List<PluginCommand> {
        return commands.filter { it.key.startsWith("$pluginId.") }.values.toList()
    }

    fun addMenuContribution(contribution: PluginMenuContribution) {
        menuContributions.add(contribution)
    }

    fun removeMenuContribution(contributionId: String) {
        menuContributions.removeAll { it.id == contributionId }
    }

    fun getMenuContributions(): List<PluginMenuContribution> = menuContributions.toList()

    fun getMenuContributionsForGroup(group: String): List<PluginMenuContribution> {
        return menuContributions.filter { it.group == group }
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
            "scss" to "vscode.css",
            "less" to "vscode.css",
            "json" to "vscode.json",
            "xml" to "vscode.xml",
            "markdown" to "vscode.markdown"
        )
        val pluginId = languagePluginIds[languageId]
        return if (pluginId != null) listOfNotNull(plugins[pluginId]) else emptyList()
    }

    fun getMarketplace(): List<MarketplacePlugin> = marketplace.toList()

    fun searchMarketplace(query: String): List<MarketplacePlugin> {
        val lowerQuery = query.lowercase()
        return marketplace.filter { plugin ->
            plugin.name.lowercase().contains(lowerQuery) ||
            plugin.description.lowercase().contains(lowerQuery) ||
            plugin.tags.any { it.lowercase().contains(lowerQuery) } ||
            plugin.publisher.lowercase().contains(lowerQuery)
        }
    }

    fun getMarketplaceByCategory(category: String): List<MarketplacePlugin> {
        return marketplace.filter { it.category == category }
    }

    fun getMarketplacePlugin(id: String): MarketplacePlugin? {
        return marketplace.find { it.id == id }
    }

    fun installMarketplacePlugin(pluginId: String): Boolean {
        val marketplacePlugin = marketplace.find { it.id == pluginId } ?: return false
        val existingPlugin = plugins[pluginId]
        if (existingPlugin != null) return false

        val newPlugin = PluginInfo(
            id = marketplacePlugin.id,
            name = marketplacePlugin.name,
            description = marketplacePlugin.description,
            version = marketplacePlugin.version,
            author = marketplacePlugin.author,
            builtIn = false,
            enabled = true
        )
        registerPlugin(newPlugin)
        enablePlugin(pluginId)
        return true
    }

    fun uninstallPlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        if (plugin.builtIn) return false
        disablePlugin(pluginId)
        unregisterPlugin(pluginId)
        return true
    }

    fun addListener(listener: PluginManagerListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: PluginManagerListener) {
        listeners.remove(listener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    private fun notifyPluginsLoaded() {
        listeners.forEach { it.onPluginsLoaded() }
    }

    private fun notifyPluginRegistered(plugin: PluginInfo) {
        listeners.forEach { it.onPluginRegistered(plugin) }
    }

    private fun notifyPluginUnregistered(plugin: PluginInfo) {
        listeners.forEach { it.onPluginUnregistered(plugin) }
    }

    private fun notifyPluginEnabled(plugin: PluginInfo) {
        listeners.forEach { it.onPluginEnabled(plugin) }
    }

    private fun notifyPluginDisabled(plugin: PluginInfo) {
        listeners.forEach { it.onPluginDisabled(plugin) }
    }

    private fun notifyPluginLoaded(plugin: PluginInfo) {
        listeners.forEach { it.onPluginLoaded(plugin) }
    }

    private fun notifyPluginUnloaded(plugin: PluginInfo) {
        listeners.forEach { it.onPluginUnloaded(plugin) }
    }

    fun shutdown() {
        unloadAllPlugins()
        plugins.clear()
        enabledPlugins.clear()
        extensionPoints.clear()
        commands.clear()
        menuContributions.clear()
        marketplace.clear()
        listeners.clear()
    }

    interface PluginManagerListener {
        fun onPluginsLoaded() {}
        fun onPluginRegistered(plugin: PluginInfo) {}
        fun onPluginUnregistered(plugin: PluginInfo) {}
        fun onPluginEnabled(plugin: PluginInfo) {}
        fun onPluginDisabled(plugin: PluginInfo) {}
        fun onPluginLoaded(plugin: PluginInfo) {}
        fun onPluginUnloaded(plugin: PluginInfo) {}
    }

    companion object {
        private const val TAG = "PluginManager"
        private const val PREFS_NAME = "vscode_plugin_prefs"
        private const val KEY_ENABLED_PLUGINS = "enabled_plugins"
    }
}

// Built-in Extension Points

class BuiltinPythonExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.python"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-Python", "Python extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("python.run", "Python: Run File", "python") { args ->
            val filePath = args?.get("filePath") as? String ?: return@PluginCommand
            val process = Runtime.getRuntime().exec(arrayOf("python", filePath))
            process.waitFor()
        },
        PluginCommand("python.debug", "Python: Debug File", "python") { args -> },
        PluginCommand("python.createVenv", "Python: Create Virtual Environment", "python") { args -> },
        PluginCommand("python.pipInstall", "Python: pip install", "python") { args -> },
        PluginCommand("python.selectInterpreter", "Python: Select Interpreter", "python") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("python")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("python.run", "python.run", "Run Python File", group = "editor/context"),
        PluginMenuContribution("python.debug", "python.debug", "Debug Python File", group = "editor/context")
    )
}

class BuiltinJavaExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.java"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-Java", "Java extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("java.compile", "Java: Compile", "java") { args -> },
        PluginCommand("java.run", "Java: Run", "java") { args -> },
        PluginCommand("java.debug", "Java: Debug", "java") { args -> },
        PluginCommand("java.gradle.build", "Java: Gradle Build", "java") { args -> },
        PluginCommand("java.maven.build", "Java: Maven Build", "java") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("java")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("java.run", "java.run", "Run Java", group = "editor/context"),
        PluginMenuContribution("java.compile", "java.compile", "Compile Java", group = "editor/context")
    )
}

class BuiltinCppExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.cpp"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-Cpp", "C/C++ extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("cpp.compile", "C/C++: Compile", "cpp") { args -> },
        PluginCommand("cpp.run", "C/C++: Run", "cpp") { args -> },
        PluginCommand("cpp.debug", "C/C++: Debug", "cpp") { args -> },
        PluginCommand("cpp.cmake.build", "C/C++: CMake Build", "cpp") { args -> },
        PluginCommand("cpp.make.build", "C/C++: Make Build", "cpp") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("cpp", "c")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("cpp.run", "cpp.run", "Run C/C++", group = "editor/context"),
        PluginMenuContribution("cpp.compile", "cpp.compile", "Compile C/C++", group = "editor/context")
    )
}

class BuiltinKotlinExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.kotlin"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-Kotlin", "Kotlin extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("kotlin.compile", "Kotlin: Compile", "kotlin") { args -> },
        PluginCommand("kotlin.run", "Kotlin: Run", "kotlin") { args -> },
        PluginCommand("kotlin.debug", "Kotlin: Debug", "kotlin") { args -> },
        PluginCommand("kotlin.gradle.build", "Kotlin: Gradle Build", "kotlin") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("kotlin")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("kotlin.run", "kotlin.run", "Run Kotlin", group = "editor/context"),
        PluginMenuContribution("kotlin.compile", "kotlin.compile", "Compile Kotlin", group = "editor/context")
    )
}

class BuiltinJavaScriptExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.javascript"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-JS", "JavaScript extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("js.run", "JavaScript: Run", "javascript") { args -> },
        PluginCommand("js.debug", "JavaScript: Debug", "javascript") { args -> },
        PluginCommand("js.npm.install", "JavaScript: npm install", "javascript") { args -> },
        PluginCommand("js.npm.build", "JavaScript: npm run build", "javascript") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("javascript")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("js.run", "js.run", "Run JavaScript", group = "editor/context")
    )
}

class BuiltinTypeScriptExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.typescript"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-TS", "TypeScript extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("ts.compile", "TypeScript: Compile", "typescript") { args -> },
        PluginCommand("ts.run", "TypeScript: Run", "typescript") { args -> },
        PluginCommand("ts.check", "TypeScript: Type Check", "typescript") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("typescript")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("ts.compile", "ts.compile", "Compile TypeScript", group = "editor/context")
    )
}

class BuiltinHtmlExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.html"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-HTML", "HTML extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("html.preview", "HTML: Preview", "html") { args -> },
        PluginCommand("html.format", "HTML: Format Document", "html") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("html")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("html.preview", "html.preview", "Preview HTML", group = "editor/context"),
        PluginMenuContribution("html.format", "html.format", "Format HTML", group = "editor/context")
    )
}

class BuiltinCssExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.css"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-CSS", "CSS extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("css.format", "CSS: Format Document", "css") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("css", "scss", "less")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("css.format", "css.format", "Format CSS", group = "editor/context")
    )
}

class BuiltinJsonExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.json"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-JSON", "JSON extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("json.format", "JSON: Format Document", "json") { args -> },
        PluginCommand("json.validate", "JSON: Validate", "json") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("json")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("json.format", "json.format", "Format JSON", group = "editor/context")
    )
}

class BuiltinXmlExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.xml"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-XML", "XML extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("xml.format", "XML: Format Document", "xml") { args -> },
        PluginCommand("xml.validate", "XML: Validate", "xml") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("xml")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("xml.format", "xml.format", "Format XML", group = "editor/context")
    )
}

class BuiltinMarkdownExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.markdown"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-MD", "Markdown extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("markdown.preview", "Markdown: Preview", "markdown") { args -> },
        PluginCommand("markdown.export.html", "Markdown: Export to HTML", "markdown") { args -> }
    )
    override fun getLanguages(): List<String> = listOf("markdown")
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("markdown.preview", "markdown.preview", "Preview Markdown", group = "editor/context")
    )
}

class BuiltinGitExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.git"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-Git", "Git extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("git.init", "Git: Initialize Repository", "git") { args -> },
        PluginCommand("git.commit", "Git: Commit", "git") { args -> },
        PluginCommand("git.push", "Git: Push", "git") { args -> },
        PluginCommand("git.pull", "Git: Pull", "git") { args -> },
        PluginCommand("git.branch", "Git: Create Branch", "git") { args -> },
        PluginCommand("git.stage", "Git: Stage All Changes", "git") { args -> },
        PluginCommand("git.unstage", "Git: Unstage All Changes", "git") { args -> },
        PluginCommand("git.discard", "Git: Discard All Changes", "git") { args -> },
        PluginCommand("git.clone", "Git: Clone Repository", "git") { args -> }
    )
    override fun getLanguages(): List<String> = emptyList()
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("git.commit", "git.commit", "Commit", group = "scm/title"),
        PluginMenuContribution("git.push", "git.push", "Push", group = "scm/title"),
        PluginMenuContribution("git.pull", "git.pull", "Pull", group = "scm/title"),
        PluginMenuContribution("git.branch", "git.branch", "Branch", group = "scm/title")
    )
}

class BuiltinTerminalExtensionPoint : PluginExtensionPoint {
    override val pluginId = "vscode.terminal"
    override suspend fun onActivate(context: PluginActivationContext) {
        android.util.Log.d("Plugin-Terminal", "Terminal extension activated")
    }
    override suspend fun onDeactivate() {}
    override fun getCommands(): List<PluginCommand> = listOf(
        PluginCommand("terminal.new", "Terminal: Create New Terminal", "terminal") { args -> },
        PluginCommand("terminal.kill", "Terminal: Kill Active Terminal", "terminal") { args -> },
        PluginCommand("terminal.clear", "Terminal: Clear", "terminal") { args -> },
        PluginCommand("terminal.split", "Terminal: Split", "terminal") { args -> },
        PluginCommand("terminal.selectAll", "Terminal: Select All", "terminal") { args -> },
        PluginCommand("terminal.copy", "Terminal: Copy Selection", "terminal") { args -> },
        PluginCommand("terminal.paste", "Terminal: Paste", "terminal") { args -> }
    )
    override fun getLanguages(): List<String> = emptyList()
    override fun getMenus(): List<PluginMenuContribution> = listOf(
        PluginMenuContribution("terminal.new", "terminal.new", "New Terminal", group = "terminal"),
        PluginMenuContribution("terminal.kill", "terminal.kill", "Kill Terminal", group = "terminal"),
        PluginMenuContribution("terminal.clear", "terminal.clear", "Clear Terminal", group = "terminal")
    )
}