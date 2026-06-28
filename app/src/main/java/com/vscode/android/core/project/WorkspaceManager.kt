package com.vscode.android.core.project

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

class WorkspaceManager(private val context: Context) {

    private val currentWorkspace = Workspace(
        id = "default",
        name = "Default Workspace",
        folders = mutableListOf()
    )

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listeners = mutableListOf<WorkspaceListener>()

    private var workspaceFilePath: String? = null
    private var isDirty = false

    fun loadWorkspace(workspaceFilePath: String): Workspace {
        try {
            val file = File(workspaceFilePath)
            if (!file.exists() || !file.name.endsWith(".code-workspace")) {
                return currentWorkspace
            }

            val json = file.readText()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(json, type) ?: emptyMap()

            this.workspaceFilePath = workspaceFilePath

            val folders = (data["folders"] as? List<Map<String, Any>>)?.mapNotNull { folderData ->
                val path = folderData["path"] as? String ?: return@mapNotNull null
                val name = folderData["name"] as? String ?: File(path).name
                val resolvedPath = if (File(path).isAbsolute) path
                    else File(file.parentFile, path).canonicalPath

                WorkspaceFolder(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    path = resolvedPath,
                    uri = folderData["uri"] as? String ?: "file://$resolvedPath"
                )
            } ?: emptyList()

            val settings = data["settings"] as? Map<String, Any> ?: emptyMap()
            val extensions = data["extensions"] as? Map<String, Any> ?: emptyMap()
            val launchData = data["launch"] as? Map<String, Any>

            val workspace = Workspace(
                id = file.absolutePath,
                name = file.nameWithoutExtension,
                folders = folders.toMutableList(),
                settings = settings.toMutableMap(),
                extensions = extensions.toMutableMap(),
                workspaceFilePath = workspaceFilePath
            )

            if (launchData != null) {
                workspace.launch = launchData.toMutableMap()
            }

            currentWorkspace.folders.clear()
            currentWorkspace.folders.addAll(folders)
            currentWorkspace.settings.clear()
            currentWorkspace.settings.putAll(settings)
            currentWorkspace.extensions.clear()
            currentWorkspace.extensions.putAll(extensions)
            currentWorkspace.name = file.nameWithoutExtension
            currentWorkspace.workspaceFilePath = workspaceFilePath

            notifyWorkspaceLoaded(workspace)
            return workspace
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load workspace: $workspaceFilePath", e)
            return currentWorkspace
        }
    }

    fun saveWorkspace(filePath: String? = null): Boolean {
        val targetPath = filePath ?: workspaceFilePath ?: return false

        try {
            val file = File(targetPath)
            if (!file.name.endsWith(".code-workspace")) {
                return false
            }

            val data = mutableMapOf<String, Any>()
            data["folders"] = currentWorkspace.folders.map { folder ->
                mapOf(
                    "path" to folder.path,
                    "name" to folder.name
                )
            }
            if (currentWorkspace.settings.isNotEmpty()) {
                data["settings"] = currentWorkspace.settings
            }
            if (currentWorkspace.extensions.isNotEmpty()) {
                data["extensions"] = currentWorkspace.extensions
            }
            if (currentWorkspace.launch.isNotEmpty()) {
                data["launch"] = currentWorkspace.launch
            }

            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(data))
            workspaceFilePath = targetPath
            isDirty = false

            notifyWorkspaceSaved(currentWorkspace)
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save workspace: $targetPath", e)
            return false
        }
    }

    fun addFolder(path: String, name: String? = null): WorkspaceFolder {
        val existing = currentWorkspace.folders.find { it.path == path }
        if (existing != null) return existing

        val folderName = name ?: File(path).name
        val folder = WorkspaceFolder(
            id = UUID.randomUUID().toString(),
            name = folderName,
            path = path,
            uri = "file://$path"
        )
        currentWorkspace.folders.add(folder)
        isDirty = true
        notifyFolderAdded(folder)
        return folder
    }

    fun removeFolder(folderId: String): Boolean {
        val folder = currentWorkspace.folders.find { it.id == folderId } ?: return false
        currentWorkspace.folders.remove(folder)
        isDirty = true
        notifyFolderRemoved(folder)
        return true
    }

    fun getWorkspaceFolders(): List<WorkspaceFolder> = currentWorkspace.folders.toList()

    fun getFolderById(folderId: String): WorkspaceFolder? =
        currentWorkspace.folders.find { it.id == folderId }

    fun getFolderByPath(path: String): WorkspaceFolder? =
        currentWorkspace.folders.find { it.path == path }

    fun getCurrentWorkspace(): Workspace = currentWorkspace

    fun getWorkspaceSettings(): Map<String, Any> = currentWorkspace.settings.toMap()

    fun updateWorkspaceSetting(key: String, value: Any) {
        currentWorkspace.settings[key] = value
        isDirty = true
        notifySettingsChanged(key, value)
    }

    fun removeWorkspaceSetting(key: String) {
        currentWorkspace.settings.remove(key)
        isDirty = true
        notifySettingsChanged(key, null)
    }

    fun getWorkspaceSetting(key: String, defaultValue: Any? = null): Any? {
        return currentWorkspace.settings[key] ?: defaultValue
    }

    fun getWorkspaceExtensions(): Map<String, Any> = currentWorkspace.extensions.toMap()

    fun addWorkspaceExtension(extensionId: String, config: Map<String, Any> = emptyMap()) {
        currentWorkspace.extensions[extensionId] = config
        isDirty = true
    }

    fun removeWorkspaceExtension(extensionId: String) {
        currentWorkspace.extensions.remove(extensionId)
        isDirty = true
    }

    fun getLaunchConfigurations(): List<Map<String, Any>> {
        val launch = currentWorkspace.launch
        val configurations = launch["configurations"] as? List<Map<String, Any>>
        return configurations ?: emptyList()
    }

    fun addLaunchConfiguration(config: Map<String, Any>) {
        val launch = currentWorkspace.launch
        val configurations = (launch["configurations"] as? MutableList<Map<String, Any>>) ?: mutableListOf()
        configurations.add(config)
        launch["configurations"] = configurations
        currentWorkspace.launch = launch
        isDirty = true
    }

    fun removeLaunchConfiguration(name: String) {
        val launch = currentWorkspace.launch
        val configurations = (launch["configurations"] as? MutableList<Map<String, Any>>) ?: return
        configurations.removeAll { it["name"] == name }
        launch["configurations"] = configurations
        currentWorkspace.launch = launch
        isDirty = true
    }

    fun hasWorkspaceFile(): Boolean = workspaceFilePath != null

    fun getWorkspaceFilePath(): String? = workspaceFilePath

    fun isWorkspaceDirty(): Boolean = isDirty

    fun closeWorkspace(): Boolean {
        if (isDirty && workspaceFilePath != null) {
            saveWorkspace()
        }
        currentWorkspace.folders.clear()
        currentWorkspace.settings.clear()
        currentWorkspace.extensions.clear()
        currentWorkspace.launch.clear()
        workspaceFilePath = null
        isDirty = false
        notifyWorkspaceClosed()
        return true
    }

    fun createWorkspaceFromFolders(
        folders: List<Pair<String, String?>>,
        workspaceFilePath: String
    ): Workspace {
        currentWorkspace.folders.clear()
        for ((path, name) in folders) {
            addFolder(path, name)
        }
        this.workspaceFilePath = workspaceFilePath
        saveWorkspace()
        return currentWorkspace
    }

    fun getTasks(): List<Map<String, Any>> {
        val tasks = currentWorkspace.settings["tasks"] as? Map<String, Any>
        val taskList = tasks?.get("tasks") as? List<Map<String, Any>>
        return taskList ?: emptyList()
    }

    fun addTask(task: Map<String, Any>) {
        val settings = currentWorkspace.settings
        val tasks = (settings["tasks"] as? MutableMap<String, Any>) ?: mutableMapOf()
        val taskList = (tasks["tasks"] as? MutableList<Map<String, Any>>) ?: mutableListOf()
        taskList.add(task)
        tasks["tasks"] = taskList
        settings["tasks"] = tasks
        currentWorkspace.settings = settings
        isDirty = true
    }

    fun removeTask(label: String) {
        val settings = currentWorkspace.settings
        val tasks = settings["tasks"] as? MutableMap<String, Any> ?: return
        val taskList = tasks["tasks"] as? MutableList<Map<String, Any>> ?: return
        taskList.removeAll { it["label"] == label }
        tasks["tasks"] = taskList
        settings["tasks"] = tasks
        currentWorkspace.settings = settings
        isDirty = true
    }

    fun addListener(listener: WorkspaceListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: WorkspaceListener) {
        listeners.remove(listener)
    }

    private fun notifyWorkspaceLoaded(workspace: Workspace) {
        listeners.forEach { it.onWorkspaceLoaded(workspace) }
    }

    private fun notifyWorkspaceSaved(workspace: Workspace) {
        listeners.forEach { it.onWorkspaceSaved(workspace) }
    }

    private fun notifyWorkspaceClosed() {
        listeners.forEach { it.onWorkspaceClosed() }
    }

    private fun notifyFolderAdded(folder: WorkspaceFolder) {
        listeners.forEach { it.onFolderAdded(folder) }
    }

    private fun notifyFolderRemoved(folder: WorkspaceFolder) {
        listeners.forEach { it.onFolderRemoved(folder) }
    }

    private fun notifySettingsChanged(key: String, value: Any?) {
        listeners.forEach { it.onWorkspaceSettingChanged(key, value) }
    }

    companion object {
        private const val TAG = "WorkspaceManager"
        private const val PREFS_NAME = "vscode_workspace_prefs"
    }

    interface WorkspaceListener {
        fun onWorkspaceLoaded(workspace: Workspace) {}
        fun onWorkspaceSaved(workspace: Workspace) {}
        fun onWorkspaceClosed() {}
        fun onFolderAdded(folder: WorkspaceFolder) {}
        fun onFolderRemoved(folder: WorkspaceFolder) {}
        fun onWorkspaceSettingChanged(key: String, value: Any?) {}
    }
}

data class Workspace(
    val id: String,
    var name: String,
    val folders: MutableList<WorkspaceFolder>,
    var settings: MutableMap<String, Any> = mutableMapOf(),
    var extensions: MutableMap<String, Any> = mutableMapOf(),
    var launch: MutableMap<String, Any> = mutableMapOf(),
    var workspaceFilePath: String? = null
)

data class WorkspaceFolder(
    val id: String,
    val name: String,
    val path: String,
    val uri: String? = null
)