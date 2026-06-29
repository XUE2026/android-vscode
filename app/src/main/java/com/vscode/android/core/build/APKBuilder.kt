package com.vscode.android.core.build

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.UUID
import com.vscode.android.core.build.BuildSystem.BuildResult
import java.util.jar.JarFile
import java.util.zip.ZipFile

class APKBuilder(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = mutableListOf<APKBuildListener>()

    private var androidSdkPath: String? = null
    private var gradleProjectRoot: String? = null
    private var gradleWrapperPath: String? = null

    data class APKInfo(
        val path: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val minSdk: Int,
        val targetSdk: Int,
        val sizeBytes: Long,
        val permissions: List<String>,
        val signatureInfo: String,
        val buildVariant: String
    )

    data class GradleDependency(
        val group: String,
        val artifact: String,
        val version: String? = null
    )

    suspend fun detectGradleProject(projectRoot: String): Boolean = withContext(Dispatchers.IO) {
        val root = File(projectRoot)
        if (!root.exists() || !root.isDirectory) return@withContext false

        val hasBuildGradle = root.listFiles()?.any {
            it.name == "build.gradle" || it.name == "build.gradle.kts"
        } ?: false

        if (!hasBuildGradle) return@withContext false

        gradleProjectRoot = projectRoot

        val gradlewFile = File(projectRoot, "gradlew")
        if (gradlewFile.exists() && gradlewFile.canExecute()) {
            gradleWrapperPath = gradlewFile.absolutePath
        } else {
            val gradlewBat = File(projectRoot, "gradlew.bat")
            if (gradlewBat.exists()) {
                gradleWrapperPath = gradlewBat.absolutePath
            }
        }

        detectAndroidSdkPath()
        return@withContext true
    }

    suspend fun buildAPK(variant: String = "debug"): BuildResult = withContext(Dispatchers.IO) {
        val projectRoot = gradleProjectRoot ?: return@withContext BuildResult(
            taskId = "",
            success = false,
            exitCode = -1,
            output = "",
            errors = "Gradle project not detected",
            duration = 0
        )

        val gradleCommand = when (variant.lowercase()) {
            "debug" -> "assembleDebug"
            "release" -> "assembleRelease"
            else -> "assemble${variant.replaceFirstChar { it.uppercaseChar() }}"
        }

        val gradlew = gradleWrapperPath ?: "$projectRoot/gradlew"
        val command = if (File(gradlew).exists()) {
            "$gradlew $gradleCommand"
        } else {
            "gradle $gradleCommand"
        }

        notifyBuildStarted(variant)

        val startTime = System.currentTimeMillis()
        try {
            val process = ProcessBuilder()
                .command("sh", "-c", command)
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .apply {
                    androidSdkPath?.let { environment().put("ANDROID_HOME", it) }
                }
                .start()

            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
                notifyBuildProgress(line!!)
            }

            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startTime

            val result = BuildResult(
                taskId = UUID.randomUUID().toString(),
                success = exitCode == 0,
                exitCode = exitCode,
                output = output.toString(),
                errors = if (exitCode != 0) extractErrors(output.toString()) else "",
                duration = duration
            )

            if (exitCode == 0) {
                notifyBuildCompleted(variant, result)
            } else {
                notifyBuildFailed(variant, result)
            }

            return@withContext result
        } catch (e: Exception) {
            val result = BuildResult(
                taskId = UUID.randomUUID().toString(),
                success = false,
                exitCode = -1,
                output = "",
                errors = e.message ?: "Unknown error",
                duration = System.currentTimeMillis() - startTime
            )
            notifyBuildFailed(variant, result)
            return@withContext result
        }
    }

    suspend fun buildAPKAsync(variant: String = "debug") {
        scope.launch {
            buildAPK(variant)
        }
    }

    suspend fun installAPK(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return@withContext false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                return@withContext false
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            return@withContext true
        } catch (e: Exception) {
            android.util.Log.e("APKBuilder", "Failed to install APK", e)
            return@withContext false
        }
    }

    suspend fun installViaPm(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return@withContext false

        try {
            val process = Runtime.getRuntime().exec(arrayOf("pm", "install", "-r", apkPath))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            return@withContext output.contains("Success")
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun signAPK(apkPath: String, keystore: KeystoreConfig): Boolean = withContext(Dispatchers.IO) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return@withContext false

        try {
            val alignedPath = apkPath.replace(".apk", "-aligned.apk")
            val signedPath = apkPath.replace(".apk", "-signed.apk")

            val alignCmd = "zipalign -v -p 4 \"$apkPath\" \"$alignedPath\""
            val alignProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", alignCmd))
            alignProcess.waitFor()

            val signCmd = buildString {
                append("apksigner sign --ks \"${keystore.path}\"")
                append(" --ks-pass pass:${keystore.password}")
                append(" --ks-key-alias ${keystore.keyAlias}")
                append(" --key-pass pass:${keystore.keyPassword}")
                append(" --out \"$signedPath\"")
                append(" \"$alignedPath\"")
            }
            val signProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", signCmd))
            val exitCode = signProcess.waitFor()

            if (exitCode == 0 && File(signedPath).exists()) {
                File(alignedPath).delete()
                apkFile.delete()
                File(signedPath).renameTo(apkFile)
                return@withContext true
            }

            return@withContext false
        } catch (e: Exception) {
            android.util.Log.e("APKBuilder", "Failed to sign APK", e)
            return@withContext false
        }
    }

    suspend fun getAPKInfo(apkPath: String): APKInfo? = withContext(Dispatchers.IO) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return@withContext null

        try {
            var packageName = ""
            var versionName = ""
            var versionCode = 0
            var minSdk = 0
            var targetSdk = 0
            val permissions = mutableListOf<String>()

            try {
                val aaptProcess = Runtime.getRuntime().exec(arrayOf("aapt", "dump", "badging", apkPath))
                val aaptOutput = aaptProcess.inputStream.bufferedReader().readText()
                aaptProcess.waitFor()

                val packageMatch = Regex("package: name='(\\S+)' versionCode='(\\d+)' versionName='(\\S+)'")
                    .find(aaptOutput)
                if (packageMatch != null) {
                    packageName = packageMatch.groupValues[1]
                    versionCode = packageMatch.groupValues[2].toIntOrNull() ?: 0
                    versionName = packageMatch.groupValues[3]
                }

                val sdkMatch = Regex("sdkVersion:'(\\d+)'").find(aaptOutput)
                sdkMatch?.let { minSdk = it.groupValues[1].toIntOrNull() ?: 0 }

                val targetSdkMatch = Regex("targetSdkVersion:'(\\d+)'").find(aaptOutput)
                targetSdkMatch?.let { targetSdk = it.groupValues[1].toIntOrNull() ?: 0 }

                Regex("uses-permission: name='(\\S+)'").findAll(aaptOutput).forEach { match ->
                    permissions.add(match.groupValues[1])
                }
            } catch (_: Exception) {
                // aapt not available, try alternative
            }

            if (packageName.isEmpty()) {
                try {
                    ZipFile(apkFile).use { zip ->
                        val entry = zip.getEntry("AndroidManifest.xml")
                        if (entry != null) {
                            packageName = "com.unknown.package"
                            versionName = "1.0"
                        }
                    }
                } catch (_: Exception) {
                    // Ignore
                }
            }

            val sizeBytes = apkFile.length()
            val signatureInfo = getSignatureInfo(apkFile)

            val variant = when {
                apkPath.contains("debug", ignoreCase = true) -> "debug"
                apkPath.contains("release", ignoreCase = true) -> "release"
                else -> "unknown"
            }

            return@withContext APKInfo(
                path = apkPath,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                sizeBytes = sizeBytes,
                permissions = permissions,
                signatureInfo = signatureInfo,
                buildVariant = variant
            )
        } catch (e: Exception) {
            android.util.Log.e("APKBuilder", "Failed to get APK info", e)
            return@withContext null
        }
    }

    suspend fun listBuildVariants(): List<String> = withContext(Dispatchers.IO) {
        val projectRoot = gradleProjectRoot ?: return@withContext listOf("debug", "release")

        try {
            val gradlew = gradleWrapperPath ?: "$projectRoot/gradlew"
            val process = ProcessBuilder()
                .command("sh", "-c", "$gradlew tasks --all 2>/dev/null | grep -i assemble")
                .directory(File(projectRoot))
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val variants = mutableListOf<String>()
            val pattern = Regex("assemble(\\w+)", RegexOption.IGNORE_CASE)
            pattern.findAll(output).forEach { match ->
                val variant = match.groupValues[1].lowercase()
                if (variant !in variants) {
                    variants.add(variant)
                }
            }

            if (variants.isEmpty()) {
                variants.addAll(listOf("debug", "release"))
            }

            return@withContext variants
        } catch (_: Exception) {
            return@withContext listOf("debug", "release")
        }
    }

    fun getAPKOutputPath(variant: String = "debug"): String {
        val projectRoot = gradleProjectRoot ?: return ""
        val appModule = File(projectRoot, "app")
        val baseDir = if (appModule.exists()) appModule else File(projectRoot)
        return "$baseDir/build/outputs/apk/$variant"
    }

    suspend fun findBuiltAPKs(variant: String = "debug"): List<File> = withContext(Dispatchers.IO) {
        val outputDir = File(getAPKOutputPath(variant))
        if (!outputDir.exists()) return@withContext emptyList()

        return@withContext outputDir.walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .toList()
    }

    suspend fun cleanBuild(): Boolean = withContext(Dispatchers.IO) {
        val projectRoot = gradleProjectRoot ?: return@withContext false

        try {
            val gradlew = gradleWrapperPath ?: "$projectRoot/gradlew"
            val process = ProcessBuilder()
                .command("sh", "-c", "$gradlew clean")
                .directory(File(projectRoot))
                .start()

            val exitCode = process.waitFor()

            val buildDir = File(projectRoot, "build")
            if (buildDir.exists()) {
                buildDir.deleteRecursively()
            }
            val appBuildDir = File(projectRoot, "app/build")
            if (appBuildDir.exists()) {
                appBuildDir.deleteRecursively()
            }

            return@withContext exitCode == 0
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun addDependency(group: String, artifact: String, version: String? = null): Boolean {
        val projectRoot = gradleProjectRoot ?: return false
        return modifyBuildGradle(projectRoot) { lines ->
            val dependencyLine = if (version != null) {
                "    implementation '$group:$artifact:$version'"
            } else {
                "    implementation '$group:$artifact'"
            }

            val dependenciesBlock = lines.indexOfFirst {
                it.trimStart().startsWith("dependencies") && it.trimStart().endsWith("{")
            }

            if (dependenciesBlock >= 0) {
                val insertIndex = dependenciesBlock + 1
                val mutableLines = lines.toMutableList()
                mutableLines.add(insertIndex, dependencyLine)
                mutableLines
            } else {
                val mutableLines = lines.toMutableList()
                mutableLines.add("")
                mutableLines.add("dependencies {")
                mutableLines.add(dependencyLine)
                mutableLines.add("}")
                mutableLines
            }
        }
    }

    suspend fun removeDependency(group: String, artifact: String): Boolean {
        val projectRoot = gradleProjectRoot ?: return false
        return modifyBuildGradle(projectRoot) { lines ->
            lines.filter { line ->
                val trimmed = line.trim()
                !(trimmed.contains("'$group:$artifact") || trimmed.contains("\"$group:$artifact"))
            }
        }
    }

    suspend fun updateMinSdk(sdk: Int): Boolean {
        val projectRoot = gradleProjectRoot ?: return false
        return modifyBuildGradle(projectRoot) { lines ->
            lines.map { line ->
                if (line.trimStart().startsWith("minSdk") && line.contains("=")) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    "$indent minSdk = $sdk"
                } else {
                    line
                }
            }
        }
    }

    suspend fun updateTargetSdk(sdk: Int): Boolean {
        val projectRoot = gradleProjectRoot ?: return false
        return modifyBuildGradle(projectRoot) { lines ->
            lines.map { line ->
                if (line.trimStart().startsWith("targetSdk") && line.contains("=")) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    "$indent targetSdk = $sdk"
                } else {
                    line
                }
            }
        }
    }

    suspend fun updateVersionCode(code: Int): Boolean {
        val projectRoot = gradleProjectRoot ?: return false
        return modifyBuildGradle(projectRoot) { lines ->
            lines.map { line ->
                if (line.trimStart().startsWith("versionCode") && line.contains("=")) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    "$indent versionCode = $code"
                } else {
                    line
                }
            }
        }
    }

    suspend fun updateVersionName(name: String): Boolean {
        val projectRoot = gradleProjectRoot ?: return false
        return modifyBuildGradle(projectRoot) { lines ->
            lines.map { line ->
                if (line.trimStart().startsWith("versionName") && line.contains("=")) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    "$indent versionName = \"$name\""
                } else {
                    line
                }
            }
        }
    }

    fun getGradleProjectRoot(): String? = gradleProjectRoot

    fun addListener(listener: APKBuildListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: APKBuildListener) {
        listeners.remove(listener)
    }

    fun shutdown() {
        listeners.clear()
    }

    private suspend fun modifyBuildGradle(projectRoot: String, modifier: (List<String>) -> List<String>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val buildFile = findBuildGradleFile(projectRoot) ?: return@withContext false
                val lines = buildFile.readLines()
                val modified = modifier(lines)
                buildFile.writeText(modified.joinToString("\n"))
                return@withContext true
            } catch (e: Exception) {
                android.util.Log.e("APKBuilder", "Failed to modify build.gradle", e)
                return@withContext false
            }
        }

    private fun findBuildGradleFile(projectRoot: String): File? {
        val appKts = File(projectRoot, "app/build.gradle.kts")
        if (appKts.exists()) return appKts

        val appGroovy = File(projectRoot, "app/build.gradle")
        if (appGroovy.exists()) return appGroovy

        val rootKts = File(projectRoot, "build.gradle.kts")
        if (rootKts.exists()) return rootKts

        val rootGroovy = File(projectRoot, "build.gradle")
        if (rootGroovy.exists()) return rootGroovy

        return null
    }

    private fun detectAndroidSdkPath() {
        val possiblePaths = listOf(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            System.getProperty("android.sdk.path"),
            "/usr/local/lib/android/sdk",
            "/opt/android-sdk",
            System.getenv("HOME")?.let { "$it/Android/Sdk" }
        )

        for (path in possiblePaths) {
            if (path != null && File(path).exists() && File(path, "platforms").exists()) {
                androidSdkPath = path
                return
            }
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "sdkmanager"))
            val output = process.inputStream.bufferedReader().readText().trim()
            if (output.isNotEmpty()) {
                val sdkPath = File(output).parentFile?.parentFile?.absolutePath
                if (sdkPath != null && File(sdkPath, "platforms").exists()) {
                    androidSdkPath = sdkPath
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun getSignatureInfo(apkFile: File): String {
        try {
            JarFile(apkFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") ||
                            name.endsWith(".EC"))) {
                        return when {
                            name.contains("CERT") -> "Debug Certificate"
                            else -> "Signed: $name"
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return "Unsigned"
    }

    private fun extractErrors(output: String): String {
        return output.lines().filter { line ->
            line.contains("error", ignoreCase = true) ||
                line.contains("FAILURE", ignoreCase = true) ||
                line.contains("BUILD FAILED", ignoreCase = true)
        }.joinToString("\n")
    }

    private fun notifyBuildStarted(variant: String) {
        listeners.forEach { it.onBuildStarted(variant) }
    }

    private fun notifyBuildProgress(message: String) {
        listeners.forEach { it.onBuildProgress(message) }
    }

    private fun notifyBuildCompleted(variant: String, result: BuildResult) {
        listeners.forEach { it.onBuildCompleted(variant, result) }
    }

    private fun notifyBuildFailed(variant: String, result: BuildResult) {
        listeners.forEach { it.onBuildFailed(variant, result) }
    }

    data class KeystoreConfig(
        val path: String,
        val password: String,
        val keyAlias: String,
        val keyPassword: String
    )

    interface APKBuildListener {
        fun onBuildStarted(variant: String) {}
        fun onBuildProgress(message: String) {}
        fun onBuildCompleted(variant: String, result: BuildResult) {}
        fun onBuildFailed(variant: String, result: BuildResult) {}
    }
}