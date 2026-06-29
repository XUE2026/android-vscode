package com.vscode.android.core.project

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectManager(
    private val context: Context,
    private val fileSystemManager: FileSystemManager,
    private val workspaceManager: WorkspaceManager
) {

    private val projects = mutableListOf<ProjectInfo>()
    private val recentProjects = mutableListOf<ProjectInfo>()
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = mutableListOf<ProjectListener>()

    private var activeProjectId: String? = null

    fun initialize() {
        loadRecentProjects()
        loadWorkspaceProjects()
    }

    fun openProject(rootPath: String): ProjectInfo {
        val existing = projects.find { it.rootPath == rootPath }
        if (existing != null) {
            setActiveProject(existing.id)
            return existing
        }

        val rootDir = File(rootPath)
        val project = ProjectInfo(
            id = UUID.randomUUID().toString(),
            name = rootDir.name,
            rootPath = rootPath,
            isOpen = true
        )

        loadProjectSettings(project)
        projects.add(project)
        setActiveProject(project.id)
        addToRecentProjects(project)
        saveRecentProjects()

        scope.launch(Dispatchers.IO) {
            fileSystemManager.watchDirectory(rootPath)
        }

        notifyProjectOpened(project)
        return project
    }

    fun closeProject(projectId: String) {
        val project = projects.find { it.id == projectId } ?: return
        projects.remove(project)
        fileSystemManager.unwatchDirectory(project.rootPath)
        if (activeProjectId == projectId) {
            activeProjectId = projects.firstOrNull()?.id
        }
        notifyProjectClosed(project)
    }

    fun getActiveProject(): ProjectInfo? {
        return activeProjectId?.let { projects.find { p -> p.id == it } }
    }

    fun setActiveProject(projectId: String) {
        activeProjectId = projectId
        val project = projects.find { it.id == projectId }
        if (project != null) {
            notifyProjectActivated(project)
        }
    }

    fun getOpenProjects(): List<ProjectInfo> = projects.toList()

    fun getRecentProjects(): List<ProjectInfo> = recentProjects.toList()

    fun getProjectById(id: String): ProjectInfo? = projects.find { it.id == id } ?: recentProjects.find { it.id == id }

    fun getProjectSettings(projectId: String): Map<String, Any> {
        val project = getProjectById(projectId) ?: return emptyMap()
        return loadProjectSettingsFile(project.rootPath)
    }

    fun updateProjectSettings(projectId: String, settings: Map<String, Any>) {
        val project = getProjectById(projectId) ?: return
        saveProjectSettingsFile(project.rootPath, settings)
        project.settings = settings.toMutableMap()
        notifyProjectSettingsChanged(project)
    }

    fun getSetting(projectId: String, key: String, defaultValue: Any? = null): Any? {
        val project = getProjectById(projectId)
        if (project != null) {
            return project.settings[key] ?: defaultValue
        }

        val globalSettings = loadGlobalSettings()
        return globalSettings[key] ?: defaultValue
    }

    private fun loadProjectSettings(project: ProjectInfo) {
        project.settings.putAll(loadProjectSettingsFile(project.rootPath))
    }

    private fun loadProjectSettingsFile(rootPath: String): MutableMap<String, Any> {
        try {
            val vscodeDir = File(rootPath, ".vscode")
            val settingsFile = File(vscodeDir, "settings.json")
            if (settingsFile.exists()) {
                val json = settingsFile.readText()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                return gson.fromJson(json, type) ?: mutableMapOf()
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return mutableMapOf()
    }

    private fun saveProjectSettingsFile(rootPath: String, settings: Map<String, Any>) {
        try {
            val vscodeDir = File(rootPath, ".vscode")
            if (!vscodeDir.exists()) vscodeDir.mkdirs()
            val settingsFile = File(vscodeDir, "settings.json")
            settingsFile.writeText(gson.toJson(settings))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save project settings", e)
        }
    }

    private fun loadGlobalSettings(): Map<String, Any> {
        try {
            val json = preferences.getString(KEY_GLOBAL_SETTINGS, "{}") ?: "{}"
            val type = object : TypeToken<Map<String, Any>>() {}.type
            return gson.fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) {
            return emptyMap()
        }
    }

    fun saveGlobalSetting(key: String, value: Any) {
        val settings = loadGlobalSettings().toMutableMap()
        settings[key] = value
        preferences.edit().putString(KEY_GLOBAL_SETTINGS, gson.toJson(settings)).apply()
    }

    fun getGlobalSetting(key: String, defaultValue: Any? = null): Any? {
        return loadGlobalSettings()[key] ?: defaultValue
    }

    private fun addToRecentProjects(project: ProjectInfo) {
        recentProjects.removeAll { it.rootPath == project.rootPath }
        recentProjects.add(0, project)
        if (recentProjects.size > MAX_RECENT_PROJECTS) {
            recentProjects.removeAt(recentProjects.lastIndex)
        }
    }

    private fun loadRecentProjects() {
        try {
            val json = preferences.getString(KEY_RECENT_PROJECTS, "[]") ?: "[]"
            val type = object : TypeToken<List<ProjectInfo>>() {}.type
            val list: List<ProjectInfo> = gson.fromJson(json, type) ?: emptyList()
            recentProjects.clear()
            recentProjects.addAll(list)
        } catch (_: Exception) {
            // Ignore parse errors
        }
    }

    private fun saveRecentProjects() {
        preferences.edit().putString(KEY_RECENT_PROJECTS, gson.toJson(recentProjects)).apply()
    }

    private fun loadWorkspaceProjects() {
        try {
            val workspaceFolders = workspaceManager.getWorkspaceFolders()
            for (folder in workspaceFolders) {
                val project = ProjectInfo(
                    id = UUID.randomUUID().toString(),
                    name = folder.name,
                    rootPath = folder.path,
                    isOpen = false
                )
                projects.add(project)
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun getBuildConfigurations(projectId: String): List<BuildConfiguration> {
        val project = getProjectById(projectId) ?: return emptyList()
        return loadBuildConfigurations(project.rootPath)
    }

    private fun loadBuildConfigurations(rootPath: String): List<BuildConfiguration> {
        val configs = mutableListOf<BuildConfiguration>()

        try {
            val gradleFile = File(rootPath, "build.gradle.kts")
            val gradleFileGroovy = File(rootPath, "build.gradle")
            if (gradleFile.exists() || gradleFileGroovy.exists()) {
                configs.add(BuildConfiguration(
                    id = "gradle-build",
                    name = "Gradle Build",
                    type = BuildType.GRADLE,
                    command = "./gradlew assembleDebug"
                ))
            }

            val settingsGradleKts = File(rootPath, "settings.gradle.kts")
            val settingsGradle = File(rootPath, "settings.gradle")
            if (settingsGradleKts.exists() || settingsGradle.exists()) {
                configs.add(BuildConfiguration(
                    id = "gradle-clean",
                    name = "Gradle Clean",
                    type = BuildType.GRADLE,
                    command = "./gradlew clean"
                ))
            }

            val makefile = File(rootPath, "Makefile")
            if (makefile.exists()) {
                configs.add(BuildConfiguration(
                    id = "make-build",
                    name = "Make",
                    type = BuildType.MAKE,
                    command = "make"
                ))
            }

            val cmakeFile = File(rootPath, "CMakeLists.txt")
            if (cmakeFile.exists()) {
                configs.add(BuildConfiguration(
                    id = "cmake-build",
                    name = "CMake Build",
                    type = BuildType.CMAKE,
                    command = "cmake --build build"
                ))
            }

            val packageJson = File(rootPath, "package.json")
            if (packageJson.exists()) {
                configs.add(BuildConfiguration(
                    id = "npm-install",
                    name = "npm install",
                    type = BuildType.NPM,
                    command = "npm install"
                ))
                configs.add(BuildConfiguration(
                    id = "npm-build",
                    name = "npm build",
                    type = BuildType.NPM,
                    command = "npm run build"
                ))
            }

            val cargoToml = File(rootPath, "Cargo.toml")
            if (cargoToml.exists()) {
                configs.add(BuildConfiguration(
                    id = "cargo-build",
                    name = "Cargo Build",
                    type = BuildType.CARGO,
                    command = "cargo build"
                ))
            }

            val goMod = File(rootPath, "go.mod")
            if (goMod.exists()) {
                configs.add(BuildConfiguration(
                    id = "go-build",
                    name = "Go Build",
                    type = BuildType.GO,
                    command = "go build ./..."
                ))
            }

            val requirementsFile = File(rootPath, "requirements.txt")
            val setupPy = File(rootPath, "setup.py")
            val pyprojectToml = File(rootPath, "pyproject.toml")
            if (requirementsFile.exists() || setupPy.exists() || pyprojectToml.exists()) {
                configs.add(BuildConfiguration(
                    id = "pip-install",
                    name = "pip install",
                    type = BuildType.PIP,
                    command = "pip install -r requirements.txt"
                ))
            }

            val pomXml = File(rootPath, "pom.xml")
            if (pomXml.exists()) {
                configs.add(BuildConfiguration(
                    id = "maven-build",
                    name = "Maven Build",
                    type = BuildType.MAVEN,
                    command = "mvn clean package"
                ))
            }

            val launchJson = File(rootPath, ".vscode/launch.json")
            if (launchJson.exists()) {
                try {
                    val json = launchJson.readText()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val launchConfig: Map<String, Any> = gson.fromJson(json, type) ?: emptyMap()
                    val configurations = launchConfig["configurations"] as? List<Map<String, Any>>
                    configurations?.forEach { config ->
                        val name = config["name"] as? String ?: "Launch"
                        val program = config["program"] as? String ?: ""
                        val configType = config["type"] as? String ?: ""
                        configs.add(BuildConfiguration(
                            id = "launch-$name",
                            name = name,
                            type = BuildType.LAUNCH,
                            command = program
                        ))
                    }
                } catch (_: Exception) {
                    // Ignore parse errors
                }
            }
        } catch (_: Exception) {
            // Ignore
        }

        return configs
    }

    fun addListener(listener: ProjectListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: ProjectListener) {
        listeners.remove(listener)
    }

    fun shutdown() {
        projects.forEach { project ->
            fileSystemManager.unwatchDirectory(project.rootPath)
        }
        projects.clear()
        recentProjects.clear()
        listeners.clear()
    }

    private fun notifyProjectOpened(project: ProjectInfo) {
        listeners.forEach { it.onProjectOpened(project) }
    }

    private fun notifyProjectClosed(project: ProjectInfo) {
        listeners.forEach { it.onProjectClosed(project) }
    }

    private fun notifyProjectActivated(project: ProjectInfo) {
        listeners.forEach { it.onProjectActivated(project) }
    }

    private fun notifyProjectSettingsChanged(project: ProjectInfo) {
        listeners.forEach { it.onProjectSettingsChanged(project) }
    }

    companion object {
        private const val TAG = "ProjectManager"
        private const val PREFS_NAME = "vscode_project_prefs"
        private const val KEY_RECENT_PROJECTS = "recent_projects"
        private const val KEY_GLOBAL_SETTINGS = "global_settings"
        private const val MAX_RECENT_PROJECTS = 20
    }

    interface ProjectListener {
        fun onProjectOpened(project: ProjectInfo) {}
        fun onProjectClosed(project: ProjectInfo) {}
        fun onProjectActivated(project: ProjectInfo) {}
        fun onProjectSettingsChanged(project: ProjectInfo) {}
    }
}

data class ProjectInfo(
    val id: String,
    val name: String,
    val rootPath: String,
    val isOpen: Boolean = false,
    var settings: MutableMap<String, Any> = mutableMapOf()
)

data class BuildConfiguration(
    val id: String,
    val name: String,
    val type: BuildType,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

enum class BuildType {
    GRADLE, MAVEN, MAKE, CMAKE, NPM, CARGO, GO, PIP, LAUNCH, CUSTOM
}