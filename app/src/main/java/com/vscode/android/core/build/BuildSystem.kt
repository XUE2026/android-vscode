package com.vscode.android.core.build

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class BuildSystem {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val buildTasks = ConcurrentHashMap<String, BuildTask>()
    private val buildHistory = CopyOnWriteArrayList<BuildResult>()
    private val runningTasks = ConcurrentHashMap<String, Job>()
    private val listeners = CopyOnWriteArrayList<BuildEventListener>()
    private val cancelledTasks = ConcurrentHashMap<String, AtomicBoolean>()

    fun detectBuildSystem(projectRoot: String): BuildType {
        val root = File(projectRoot)
        if (!root.exists() || !root.isDirectory) return BuildType.CUSTOM

        val files = root.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        return when {
            files.contains("build.gradle.kts") || files.contains("build.gradle") -> BuildType.GRADLE
            files.contains("pom.xml") -> BuildType.MAVEN
            files.contains("Makefile") -> BuildType.MAKE
            files.contains("CMakeLists.txt") -> BuildType.CMAKE
            files.contains("package.json") -> BuildType.NPM
            files.contains("Cargo.toml") -> BuildType.CARGO
            files.contains("go.mod") -> BuildType.GO
            files.contains("requirements.txt") || files.contains("setup.py") ||
                files.contains("pyproject.toml") -> BuildType.PYTHON
            else -> detectBuildSystemFromSource(root)
        }
    }

    fun detectBuildConfigurations(projectRoot: String): List<BuildConfiguration> {
        val configs = mutableListOf<BuildConfiguration>()
        val root = File(projectRoot)
        if (!root.exists()) return configs

        val files = root.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        if (files.contains("build.gradle.kts") || files.contains("build.gradle")) {
            configs.add(BuildConfiguration(
                language = "java",
                buildCommand = "./gradlew assembleDebug",
                runCommand = null,
                debugCommand = null,
                outputDir = "$projectRoot/app/build/outputs/apk",
                sourceDir = "$projectRoot/app/src/main/java"
            ))
            configs.add(BuildConfiguration(
                language = "java",
                buildCommand = "./gradlew build",
                runCommand = null,
                debugCommand = null,
                outputDir = "$projectRoot/build/libs",
                sourceDir = "$projectRoot/src/main/java"
            ))
        }

        if (files.contains("pom.xml")) {
            configs.add(BuildConfiguration(
                language = "java",
                buildCommand = "mvn clean package",
                runCommand = null,
                debugCommand = null,
                outputDir = "$projectRoot/target",
                sourceDir = "$projectRoot/src/main/java"
            ))
        }

        if (files.contains("Makefile")) {
            configs.add(BuildConfiguration(
                language = "cpp",
                buildCommand = "make",
                runCommand = null,
                debugCommand = null,
                outputDir = projectRoot,
                sourceDir = projectRoot
            ))
        }

        if (files.contains("CMakeLists.txt")) {
            configs.add(BuildConfiguration(
                language = "cpp",
                buildCommand = "cmake --build build",
                runCommand = null,
                debugCommand = null,
                outputDir = "$projectRoot/build",
                sourceDir = projectRoot
            ))
        }

        if (files.contains("package.json")) {
            configs.add(BuildConfiguration(
                language = "javascript",
                buildCommand = "npm run build",
                runCommand = "node .",
                debugCommand = null,
                outputDir = "$projectRoot/dist",
                sourceDir = "$projectRoot/src"
            ))
        }

        if (files.contains("Cargo.toml")) {
            configs.add(BuildConfiguration(
                language = "rust",
                buildCommand = "cargo build",
                runCommand = "cargo run",
                debugCommand = null,
                outputDir = "$projectRoot/target/debug",
                sourceDir = "$projectRoot/src"
            ))
        }

        if (files.contains("go.mod")) {
            configs.add(BuildConfiguration(
                language = "go",
                buildCommand = "go build ./...",
                runCommand = null,
                debugCommand = null,
                outputDir = projectRoot,
                sourceDir = projectRoot
            ))
        }

        if (files.any { it.endsWith(".py") } || files.contains("requirements.txt") ||
            files.contains("setup.py") || files.contains("pyproject.toml")) {
            configs.add(BuildConfiguration(
                language = "python",
                buildCommand = "python3 -m py_compile *.py",
                runCommand = null,
                debugCommand = null,
                outputDir = "$projectRoot/__pycache__",
                sourceDir = projectRoot
            ))
        }

        if (files.any { it.endsWith(".kt") }) {
            configs.add(BuildConfiguration(
                language = "kotlin",
                buildCommand = "kotlinc *.kt -include-runtime -d app.jar",
                runCommand = "java -jar app.jar",
                debugCommand = null,
                outputDir = projectRoot,
                sourceDir = projectRoot
            ))
        }

        return configs
    }

    suspend fun executeBuildTask(task: BuildTask): BuildResult = withContext(Dispatchers.IO) {
        val taskId = task.id
        val cancelled = AtomicBoolean(false)
        cancelledTasks[taskId] = cancelled

        notifyBuildStart(task)
        val startTime = System.currentTimeMillis()

        try {
            val fullCommand = buildCommandString(task)
            val process = ProcessBuilder()
                .command("sh", "-c", fullCommand)
                .directory(File(task.workingDir))
                .redirectErrorStream(true)
                .apply {
                    if (task.envVars.isNotEmpty()) {
                        environment().putAll(task.envVars)
                    }
                }
                .start()

            val output = StringBuilder()
            val errors = StringBuilder()
            val reader = InputStreamReader(process.inputStream).buffered()

            val buffer = CharArray(4096)
            var charsRead: Int
            while (isActive && !cancelled.get()) {
                charsRead = reader.read(buffer)
                if (charsRead == -1) break
                val text = String(buffer, 0, charsRead)
                output.append(text)
                notifyBuildProgress(task, text)
            }

            if (cancelled.get()) {
                process.destroyForcibly()
                reader.close()
                val result = BuildResult(
                    taskId = taskId,
                    success = false,
                    exitCode = -1,
                    output = output.toString(),
                    errors = "Build cancelled",
                    duration = System.currentTimeMillis() - startTime
                )
                buildHistory.add(0, result)
                notifyBuildComplete(task, result)
                return@withContext result
            }

            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startTime

            val parsedErrors = parseBuildErrors(output.toString())
            val result = BuildResult(
                taskId = taskId,
                success = exitCode == 0,
                exitCode = exitCode,
                output = output.toString(),
                errors = parsedErrors,
                duration = duration
            )

            buildHistory.add(0, result)
            runningTasks.remove(taskId)
            cancelledTasks.remove(taskId)

            if (exitCode == 0) {
                notifyBuildComplete(task, result)
            } else {
                notifyBuildError(task, result)
            }

            return@withContext result
        } catch (e: Exception) {
            val result = BuildResult(
                taskId = taskId,
                success = false,
                exitCode = -1,
                output = "",
                errors = e.message ?: "Unknown error",
                duration = System.currentTimeMillis() - startTime
            )
            buildHistory.add(0, result)
            runningTasks.remove(taskId)
            cancelledTasks.remove(taskId)
            notifyBuildError(task, result)
            return@withContext result
        }
    }

    fun executeBuildTaskAsync(task: BuildTask, callback: ((BuildResult) -> Unit)? = null) {
        val job = scope.launch {
            val result = executeBuildTask(task)
            callback?.invoke(result)
        }
        runningTasks[task.id] = job
    }

    fun cancelBuildTask(taskId: String) {
        cancelledTasks[taskId]?.set(true)
        runningTasks[taskId]?.cancel()
        runningTasks.remove(taskId)
    }

    fun cancelAllBuilds() {
        cancelledTasks.values.forEach { it.set(true) }
        runningTasks.values.forEach { it.cancel() }
        runningTasks.clear()
        cancelledTasks.clear()
    }

    fun isBuildRunning(taskId: String): Boolean {
        return runningTasks.containsKey(taskId) && !cancelledTasks.getOrDefault(taskId, AtomicBoolean(false)).get()
    }

    fun getRunningTasks(): List<BuildTask> {
        return buildTasks.values.filter { runningTasks.containsKey(it.id) }
    }

    fun getBuildHistory(): List<BuildResult> = buildHistory.toList()

    fun getBuildHistoryForLanguage(language: String): List<BuildResult> {
        return buildHistory.filter { result ->
            buildTasks[result.taskId]?.language == language
        }
    }

    fun clearBuildHistory() {
        buildHistory.clear()
    }

    fun createBuildTask(
        name: String,
        type: BuildType,
        command: String,
        workingDir: String,
        language: String,
        args: List<String> = emptyList(),
        envVars: Map<String, String> = emptyMap()
    ): BuildTask {
        val task = BuildTask(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
            command = command,
            workingDir = workingDir,
            language = language,
            args = args,
            envVars = envVars
        )
        buildTasks[task.id] = task
        return task
    }

    fun getTask(taskId: String): BuildTask? = buildTasks[taskId]

    fun removeTask(taskId: String) {
        buildTasks.remove(taskId)
        runningTasks.remove(taskId)
        cancelledTasks.remove(taskId)
    }

    fun getPredefinedTasksForLanguage(language: String, projectDir: String): List<BuildTask> {
        return when (language.lowercase()) {
            "python" -> listOf(
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Check Syntax",
                    type = BuildType.PYTHON,
                    command = "python3 -m py_compile",
                    workingDir = projectDir,
                    language = "python"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Run Python Script",
                    type = BuildType.PYTHON,
                    command = "python3",
                    workingDir = projectDir,
                    language = "python"
                )
            )
            "java" -> listOf(
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Compile Java",
                    type = BuildType.JAVA,
                    command = "javac",
                    workingDir = projectDir,
                    language = "java"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Run Java",
                    type = BuildType.JAVA,
                    command = "java",
                    workingDir = projectDir,
                    language = "java"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Gradle Build",
                    type = BuildType.GRADLE,
                    command = "./gradlew build",
                    workingDir = projectDir,
                    language = "java"
                )
            )
            "cpp", "c++", "c" -> listOf(
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Compile with g++",
                    type = BuildType.CPP,
                    command = "g++",
                    workingDir = projectDir,
                    language = "cpp"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Compile with clang++",
                    type = BuildType.CPP,
                    command = "clang++",
                    workingDir = projectDir,
                    language = "cpp"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Make Build",
                    type = BuildType.MAKE,
                    command = "make",
                    workingDir = projectDir,
                    language = "cpp"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "CMake Build",
                    type = BuildType.CMAKE,
                    command = "cmake --build build",
                    workingDir = projectDir,
                    language = "cpp"
                )
            )
            "kotlin" -> listOf(
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Compile Kotlin",
                    type = BuildType.KOTLIN,
                    command = "kotlinc",
                    workingDir = projectDir,
                    language = "kotlin"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Gradle Build",
                    type = BuildType.GRADLE,
                    command = "./gradlew build",
                    workingDir = projectDir,
                    language = "kotlin"
                )
            )
            "android" -> listOf(
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Assemble Debug APK",
                    type = BuildType.GRADLE,
                    command = "./gradlew assembleDebug",
                    workingDir = projectDir,
                    language = "android"
                ),
                BuildTask(
                    id = UUID.randomUUID().toString(),
                    name = "Assemble Release APK",
                    type = BuildType.GRADLE,
                    command = "./gradlew assembleRelease",
                    workingDir = projectDir,
                    language = "android"
                )
            )
            else -> emptyList()
        }
    }

    fun addListener(listener: BuildEventListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: BuildEventListener) {
        listeners.remove(listener)
    }

    fun shutdown() {
        cancelAllBuilds()
        listeners.clear()
        buildHistory.clear()
        buildTasks.clear()
    }

    private fun buildCommandString(task: BuildTask): String {
        val sb = StringBuilder()
        if (task.envVars.isNotEmpty()) {
            task.envVars.forEach { (k, v) -> sb.append("export $k=\"$v\"; ") }
        }
        sb.append(task.command)
        if (task.args.isNotEmpty()) {
            sb.append(" ").append(task.args.joinToString(" "))
        }
        return sb.toString()
    }

    private fun parseBuildErrors(output: String): String {
        val errorLines = mutableListOf<String>()
        val errorPatterns = listOf(
            Regex("error:", RegexOption.IGNORE_CASE),
            Regex("ERROR:", RegexOption.IGNORE_CASE),
            Regex("FAILURE:", RegexOption.IGNORE_CASE),
            Regex("BUILD FAILED", RegexOption.IGNORE_CASE),
            Regex("^\\s*\\^\\s*$"),
            Regex(": error:", RegexOption.IGNORE_CASE),
            Regex("Exception in thread", RegexOption.IGNORE_CASE),
            Regex("Caused by:", RegexOption.IGNORE_CASE),
            Regex("\\[ERROR\\]", RegexOption.IGNORE_CASE),
            Regex("error\\s+[A-Z]+\\d+:", RegexOption.IGNORE_CASE),
            Regex("warning:", RegexOption.IGNORE_CASE),
            Regex("WARNING:", RegexOption.IGNORE_CASE),
            Regex("note:", RegexOption.IGNORE_CASE),
            Regex("\\[WARNING\\]", RegexOption.IGNORE_CASE)
        )

        for (line in output.lines()) {
            for (pattern in errorPatterns) {
                if (pattern.containsMatchIn(line)) {
                    errorLines.add(line)
                    break
                }
            }
        }
        return errorLines.joinToString("\n")
    }

    private fun notifyBuildStart(task: BuildTask) {
        listeners.forEach { it.onBuildStart(task) }
    }

    private fun notifyBuildProgress(task: BuildTask, output: String) {
        listeners.forEach { it.onBuildProgress(task, output) }
    }

    private fun notifyBuildComplete(task: BuildTask, result: BuildResult) {
        listeners.forEach { it.onBuildComplete(task, result) }
    }

    private fun notifyBuildError(task: BuildTask, result: BuildResult) {
        listeners.forEach { it.onBuildError(task, result) }
    }

    data class BuildTask(
        val id: String,
        val name: String,
        val type: BuildType,
        val command: String,
        val workingDir: String,
        val language: String,
        val args: List<String> = emptyList(),
        val envVars: Map<String, String> = emptyMap()
    )

    data class BuildConfiguration(
        val language: String,
        val buildCommand: String,
        val runCommand: String? = null,
        val debugCommand: String? = null,
        val outputDir: String = "",
        val sourceDir: String = ""
    )

    data class BuildResult(
        val taskId: String,
        val success: Boolean,
        val exitCode: Int,
        val output: String,
        val errors: String,
        val duration: Long
    )

    enum class BuildType {
        GRADLE, MAVEN, MAKE, CMAKE, NPM, CARGO, GO, PYTHON, JAVA, CPP, KOTLIN, ANDROID, CUSTOM
    }

    interface BuildEventListener {
        fun onBuildStart(task: BuildTask) {}
        fun onBuildProgress(task: BuildTask, output: String) {}
        fun onBuildComplete(task: BuildTask, result: BuildResult) {}
        fun onBuildError(task: BuildTask, result: BuildResult) {}
    }
}