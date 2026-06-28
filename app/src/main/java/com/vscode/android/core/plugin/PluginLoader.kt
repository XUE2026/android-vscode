package com.vscode.android.core.plugin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileFilter
import java.util.concurrent.ConcurrentHashMap

data class PluginManifest(
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val publisher: String = "",
    val main: String = "",
    val engines: Map<String, String> = emptyMap(),
    val activationEvents: List<String> = emptyList(),
    val contributes: Map<String, Any> = emptyMap(),
    val dependencies: Map<String, String> = emptyMap(),
    val extensionDependencies: List<String> = emptyList(),
    val icon: String = "",
    val categories: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val repository: Map<String, String> = emptyMap(),
    val bugs: Map<String, String> = emptyMap(),
    val homepage: String = "",
    val license: String = "",
    val enabledApiProposals: List<String> = emptyList()
)

data class PluginEvent(
    val eventType: PluginEventType,
    val pluginId: String,
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class PluginEventType {
    PLUGIN_LOADED,
    PLUGIN_UNLOADED,
    PLUGIN_ACTIVATED,
    PLUGIN_DEACTIVATED,
    PLUGIN_ERROR,
    DEPENDENCY_RESOLVED,
    DEPENDENCY_FAILED,
    MANIFEST_PARSED,
    MANIFEST_INVALID,
    ASSET_LOADED,
    ASSET_FAILED,
    SANDBOX_VIOLATION
}

enum class PluginStatus {
    NOT_LOADED,
    LOADING,
    LOADED,
    ACTIVATED,
    ERROR,
    DISABLED
}

data class PluginLoadResult(
    val success: Boolean,
    val pluginId: String,
    val status: PluginStatus,
    val error: String? = null,
    val manifest: PluginManifest? = null
)

data class PluginDependencyNode(
    val pluginId: String,
    val version: String,
    val resolved: Boolean = false,
    val dependencies: MutableList<PluginDependencyNode> = mutableListOf()
)

class PluginLoader(private val context: Context) {

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadedPlugins = ConcurrentHashMap<String, PluginLoadResult>()
    private val manifestCache = ConcurrentHashMap<String, PluginManifest>()
    private val eventListeners = mutableListOf<PluginEventListener>()
    private val sandboxManager = PluginSandboxManager()
    private val dependencyGraph = mutableMapOf<String, PluginDependencyNode>()

    private val pluginsDir: File
        get() {
            val dir = File(context.filesDir, "plugins")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val assetsPluginsDir: String = "plugins"

    fun loadPluginFromAssets(pluginId: String): PluginLoadResult {
        return try {
            val manifest = loadManifestFromAssets(pluginId)
                ?: return PluginLoadResult(false, pluginId, PluginStatus.ERROR, "Manifest not found for $pluginId")

            if (!validateManifest(manifest)) {
                return PluginLoadResult(false, pluginId, PluginStatus.ERROR, "Invalid manifest for $pluginId")
            }

            if (!validatePluginSignature(pluginId)) {
                return PluginLoadResult(false, pluginId, PluginStatus.ERROR, "Plugin signature validation failed: $pluginId")
            }

            if (!sandboxManager.checkSandboxPermissions(pluginId, manifest)) {
                return PluginLoadResult(false, pluginId, PluginStatus.ERROR, "Sandbox permission denied: $pluginId")
            }

            manifestCache[pluginId] = manifest
            val result = PluginLoadResult(true, pluginId, PluginStatus.LOADED, manifest = manifest)
            loadedPlugins[pluginId] = result
            dispatchEvent(PluginEvent(PluginEventType.PLUGIN_LOADED, pluginId))
            result
        } catch (e: Exception) {
            val error = "Failed to load plugin from assets: $pluginId - ${e.message}"
            android.util.Log.e(TAG, error, e)
            PluginLoadResult(false, pluginId, PluginStatus.ERROR, error)
        }
    }

    fun loadPluginFromFile(pluginPath: String): PluginLoadResult {
        return try {
            val pluginFile = File(pluginPath)
            if (!pluginFile.exists()) {
                return PluginLoadResult(false, pluginPath, PluginStatus.ERROR, "Plugin file not found: $pluginPath")
            }

            val manifest = parseManifestFromFile(pluginFile)
                ?: return PluginLoadResult(false, pluginPath, PluginStatus.ERROR, "Cannot parse manifest from: $pluginPath")

            if (!validateManifest(manifest)) {
                return PluginLoadResult(false, pluginPath, PluginStatus.ERROR, "Invalid manifest")
            }

            val pluginId = generatePluginId(manifest)
            if (!sandboxManager.checkSandboxPermissions(pluginId, manifest)) {
                return PluginLoadResult(false, pluginId, PluginStatus.ERROR, "Sandbox permission denied")
            }

            manifestCache[pluginId] = manifest
            val result = PluginLoadResult(true, pluginId, PluginStatus.LOADED, manifest = manifest)
            loadedPlugins[pluginId] = result
            dispatchEvent(PluginEvent(PluginEventType.PLUGIN_LOADED, pluginId))
            result
        } catch (e: Exception) {
            PluginLoadResult(false, pluginPath, PluginStatus.ERROR, e.message)
        }
    }

    fun discoverPluginsInAssets(): List<String> {
        val pluginIds = mutableListOf<String>()
        try {
            val assetFiles = context.assets.list(assetsPluginsDir) ?: return pluginIds
            for (entry in assetFiles) {
                try {
                    val manifestPath = "$assetsPluginsDir/$entry/package.json"
                    context.assets.open(manifestPath).use { stream ->
                        val json = stream.bufferedReader().readText()
                        val manifest = gson.fromJson(json, PluginManifest::class.java)
                        if (manifest != null) {
                            val pluginId = generatePluginId(manifest)
                            manifestCache[pluginId] = manifest
                            pluginIds.add(pluginId)
                        }
                    }
                } catch (_: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to discover plugins in assets", e)
        }
        return pluginIds
    }

    fun discoverPluginsInDirectory(dirPath: String): List<String> {
        val pluginIds = mutableListOf<String>()
        try {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) return pluginIds

            val pluginDirs = dir.listFiles(FileFilter { it.isDirectory && !it.name.startsWith(".") })
                ?: return pluginIds

            for (pluginDir in pluginDirs) {
                val manifestFile = File(pluginDir, "package.json")
                if (manifestFile.exists()) {
                    try {
                        val manifest = parseManifestFromFile(manifestFile)
                        if (manifest != null) {
                            val pluginId = generatePluginId(manifest)
                            manifestCache[pluginId] = manifest
                            pluginIds.add(pluginId)
                        }
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to discover plugins in $dirPath", e)
        }
        return pluginIds
    }

    fun loadManifestFromAssets(pluginId: String): PluginManifest? {
        return try {
            val manifestPath = "$assetsPluginsDir/$pluginId/package.json"
            context.assets.open(manifestPath).use { stream ->
                val json = stream.bufferedReader().readText()
                val type = object : TypeToken<PluginManifest>() {}.type
                gson.fromJson(json, type)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load manifest for $pluginId", e)
            null
        }
    }

    fun parseManifestFromFile(file: File): PluginManifest? {
        return try {
            val json = file.readText()
            val type = object : TypeToken<PluginManifest>() {}.type
            val manifest = gson.fromJson<PluginManifest>(json, type)
            dispatchEvent(PluginEvent(PluginEventType.MANIFEST_PARSED, file.name))
            manifest
        } catch (e: Exception) {
            dispatchEvent(PluginEvent(PluginEventType.MANIFEST_INVALID, file.name,
                mapOf("error" to (e.message ?: "Unknown error"))))
            null
        }
    }

    fun parseManifestFromJson(json: String): PluginManifest? {
        return try {
            val type = object : TypeToken<PluginManifest>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun validateManifest(manifest: PluginManifest): Boolean {
        if (manifest.name.isBlank()) return false
        if (manifest.version.isBlank()) return false
        if (!isValidVersion(manifest.version)) return false
        if (manifest.engines.isNotEmpty()) {
            val vscodeEngine = manifest.engines["vscode"]
            if (vscodeEngine != null && !isValidEngineVersion(vscodeEngine)) return false
        }
        return true
    }

    private fun isValidVersion(version: String): Boolean {
        return version.matches(Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$"))
    }

    private fun isValidEngineVersion(version: String): Boolean {
        return version.matches(Regex("^[\\^~]?\\d+\\.\\d+\\.\\d+$"))
    }

    fun validatePluginSignature(pluginId: String): Boolean {
        return try {
            val sigFile = "$assetsPluginsDir/$pluginId/.signature"
            context.assets.open(sigFile).use { stream ->
                val signature = stream.bufferedReader().readText().trim()
                signature.isNotEmpty()
            }
        } catch (_: Exception) {
            true
        }
    }

    fun resolveDependencies(pluginId: String): List<PluginDependencyNode> {
        val resolved = mutableListOf<PluginDependencyNode>()
        val manifest = manifestCache[pluginId] ?: return resolved

        for ((depId, depVersion) in manifest.dependencies) {
            val node = PluginDependencyNode(depId, depVersion)
            if (resolveDependency(depId, depVersion)) {
                node.resolved = true
                dispatchEvent(PluginEvent(PluginEventType.DEPENDENCY_RESOLVED, pluginId,
                    mapOf("dependency" to depId, "version" to depVersion)))
            } else {
                node.resolved = false
                dispatchEvent(PluginEvent(PluginEventType.DEPENDENCY_FAILED, pluginId,
                    mapOf("dependency" to depId, "version" to depVersion)))
            }
            resolved.add(node)
        }

        dependencyGraph[pluginId] = PluginDependencyNode(pluginId, manifest.version, true, resolved)
        return resolved
    }

    fun resolveDependency(dependencyId: String, version: String): Boolean {
        val depManifest = manifestCache[dependencyId]
        if (depManifest != null) {
            return isVersionCompatible(depManifest.version, version)
        }
        return try {
            val loadedFromAssets = loadManifestFromAssets(dependencyId)
            loadedFromAssets != null
        } catch (_: Exception) {
            false
        }
    }

    private fun isVersionCompatible(actual: String, required: String): Boolean {
        return try {
            val actualParts = actual.split(".").map { it.toIntOrNull() ?: 0 }
            val requiredParts = required.replace("^", "").replace("~", "").split(".")
                .map { it.toIntOrNull() ?: 0 }

            for (i in 0 until minOf(actualParts.size, requiredParts.size)) {
                if (actualParts[i] > requiredParts[i]) return true
                if (actualParts[i] < requiredParts[i]) return false
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    fun getDependencyGraph(pluginId: String): PluginDependencyNode? {
        return dependencyGraph[pluginId]
    }

    fun getLoadOrder(pluginIds: List<String>): List<String> {
        val visited = mutableSetOf<String>()
        val order = mutableListOf<String>()

        fun visit(id: String) {
            if (id in visited) return
            visited.add(id)
            val manifest = manifestCache[id] ?: return
            for (depId in manifest.dependencies.keys) {
                visit(depId)
            }
            order.add(id)
        }

        for (id in pluginIds) {
            visit(id)
        }

        return order
    }

    fun loadPluginWithDependencies(pluginId: String): List<PluginLoadResult> {
        val results = mutableListOf<PluginLoadResult>()
        val manifest = manifestCache[pluginId] ?: return results

        for ((depId, _) in manifest.dependencies) {
            if (loadedPlugins.containsKey(depId)) continue
            val result = loadPluginFromAssets(depId)
            results.add(result)
            if (result.success) {
                results.addAll(loadPluginWithDependencies(depId))
            }
        }

        val result = loadPluginFromAssets(pluginId)
        results.add(result)
        return results
    }

    fun getLoadedPlugins(): List<PluginLoadResult> = loadedPlugins.values.toList()

    fun isPluginLoaded(pluginId: String): Boolean = loadedPlugins.containsKey(pluginId)

    fun getPluginStatus(pluginId: String): PluginStatus {
        return loadedPlugins[pluginId]?.status ?: PluginStatus.NOT_LOADED
    }

    fun getPluginManifest(pluginId: String): PluginManifest? = manifestCache[pluginId]

    fun getAllManifests(): Map<String, PluginManifest> = manifestCache.toMap()

    fun unloadPlugin(pluginId: String): Boolean {
        val removed = loadedPlugins.remove(pluginId) != null
        if (removed) {
            dispatchEvent(PluginEvent(PluginEventType.PLUGIN_UNLOADED, pluginId))
        }
        return removed
    }

    fun unloadAllPlugins() {
        loadedPlugins.clear()
        manifestCache.clear()
        dependencyGraph.clear()
    }

    fun addEventListener(listener: PluginEventListener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener)
        }
    }

    fun removeEventListener(listener: PluginEventListener) {
        eventListeners.remove(listener)
    }

    private fun dispatchEvent(event: PluginEvent) {
        eventListeners.forEach { it.onPluginEvent(event) }
    }

    private fun generatePluginId(manifest: PluginManifest): String {
        val publisher = manifest.publisher.ifBlank { "local" }
        return "${publisher}.${manifest.name}"
    }

    fun getPluginAssetsPath(pluginId: String): String {
        return "$assetsPluginsDir/$pluginId"
    }

    fun readPluginAsset(pluginId: String, assetPath: String): String? {
        return try {
            val fullPath = "$assetsPluginsDir/$pluginId/$assetPath"
            context.assets.open(fullPath).use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun readPluginAssetBytes(pluginId: String, assetPath: String): ByteArray? {
        return try {
            val fullPath = "$assetsPluginsDir/$pluginId/$assetPath"
            context.assets.open(fullPath).use { stream ->
                stream.readBytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun listPluginAssets(pluginId: String): List<String> {
        return try {
            val basePath = "$assetsPluginsDir/$pluginId"
            context.assets.list(basePath)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun shutdown() {
        unloadAllPlugins()
        eventListeners.clear()
        sandboxManager.clear()
    }

    interface PluginEventListener {
        fun onPluginEvent(event: PluginEvent)
    }

    companion object {
        private const val TAG = "PluginLoader"
    }
}

class PluginSandboxManager {

    private val sandboxPermissions = ConcurrentHashMap<String, MutableSet<String>>()
    private val blockedPermissions = setOf(
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    )

    private val allowedSystemAccess = setOf(
        "file.read",
        "file.write",
        "file.create",
        "file.delete",
        "process.exec",
        "network.http",
        "network.https",
        "clipboard.read",
        "clipboard.write",
        "notification.show"
    )

    fun checkSandboxPermissions(pluginId: String, manifest: PluginManifest): Boolean {
        val requested = extractRequestedPermissions(manifest)
        for (permission in requested) {
            if (permission in blockedPermissions) {
                android.util.Log.w("PluginSandbox", "Blocked permission $permission for plugin $pluginId")
                return false
            }
        }
        sandboxPermissions[pluginId] = requested.toMutableSet()
        return true
    }

    private fun extractRequestedPermissions(manifest: PluginManifest): Set<String> {
        val permissions = mutableSetOf<String>()
        permissions.addAll(allowedSystemAccess)
        val contributes = manifest.contributes
        if (contributes.containsKey("commands")) {
            permissions.add("command.register")
        }
        if (contributes.containsKey("menus")) {
            permissions.add("menu.register")
        }
        if (contributes.containsKey("languages")) {
            permissions.add("language.register")
        }
        if (contributes.containsKey("grammars")) {
            permissions.add("grammar.register")
        }
        if (contributes.containsKey("keybindings")) {
            permissions.add("keybinding.register")
        }
        return permissions
    }

    fun hasPermission(pluginId: String, permission: String): Boolean {
        return sandboxPermissions[pluginId]?.contains(permission) ?: false
    }

    fun grantPermission(pluginId: String, permission: String) {
        sandboxPermissions.getOrPut(pluginId) { mutableSetOf() }.add(permission)
    }

    fun revokePermission(pluginId: String, permission: String) {
        sandboxPermissions[pluginId]?.remove(permission)
    }

    fun getPermissions(pluginId: String): Set<String> {
        return sandboxPermissions[pluginId]?.toSet() ?: emptySet()
    }

    fun clear() {
        sandboxPermissions.clear()
    }
}