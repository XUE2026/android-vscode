package com.vscode.android.core.project

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit

class FileSystemManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchers = mutableMapOf<String, WatchService>()
    private val watchedDirs = mutableMapOf<String, String>()
    private val listeners = mutableListOf<FileSystemListener>()
    private val watchJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    private val fileIcons = mapOf(
        "kt" to "kotlin",
        "kts" to "kotlin",
        "java" to "java",
        "py" to "python",
        "pyw" to "python",
        "js" to "javascript",
        "jsx" to "react",
        "mjs" to "javascript",
        "ts" to "typescript",
        "tsx" to "react",
        "html" to "html",
        "htm" to "html",
        "css" to "css",
        "scss" to "sass",
        "less" to "less",
        "json" to "json",
        "xml" to "xml",
        "plist" to "xml",
        "md" to "markdown",
        "markdown" to "markdown",
        "yml" to "yaml",
        "yaml" to "yaml",
        "gradle" to "gradle",
        "sh" to "shell",
        "bash" to "shell",
        "zsh" to "shell",
        "sql" to "sql",
        "go" to "go",
        "rs" to "rust",
        "swift" to "swift",
        "dart" to "dart",
        "rb" to "ruby",
        "php" to "php",
        "cpp" to "cpp",
        "cc" to "cpp",
        "cxx" to "cpp",
        "c" to "c",
        "h" to "c_header",
        "hpp" to "cpp_header",
        "png" to "image",
        "jpg" to "image",
        "jpeg" to "image",
        "gif" to "image",
        "svg" to "svg",
        "ico" to "image",
        "webp" to "image",
        "bmp" to "image",
        "mp3" to "audio",
        "wav" to "audio",
        "ogg" to "audio",
        "flac" to "audio",
        "mp4" to "video",
        "avi" to "video",
        "mkv" to "video",
        "mov" to "video",
        "pdf" to "pdf",
        "zip" to "zip",
        "tar" to "zip",
        "gz" to "zip",
        "rar" to "zip",
        "7z" to "zip",
        "ttf" to "font",
        "otf" to "font",
        "woff" to "font",
        "woff2" to "font",
        "eot" to "font",
        "lock" to "lock",
        "env" to "gear",
        "gitignore" to "git",
        "dockerfile" to "docker",
        "dockerignore" to "docker",
        "properties" to "settings",
        "cfg" to "settings",
        "conf" to "settings",
        "ini" to "settings",
        "toml" to "settings",
        "csv" to "table",
        "tsv" to "table",
        "log" to "log",
        "txt" to "document",
        "readme" to "info",
        "license" to "certificate",
        "changelog" to "history",
        "makefile" to "makefile",
        "cmake" to "cmake",
        "cmakelists.txt" to "cmake"
    )

    fun getFileIconKey(extension: String): String {
        return fileIcons[extension.lowercase()] ?: "default"
    }

    fun getFileIconKeyForFile(fileName: String): String {
        val lowerName = fileName.lowercase()
        if (lowerName == "makefile") return "makefile"
        if (lowerName == "cmakelists.txt") return "cmake"
        if (lowerName == "dockerfile") return "docker"
        if (lowerName == "license") return "certificate"
        if (lowerName == "readme.md" || lowerName == "readme.txt") return "info"
        if (lowerName == "changelog.md" || lowerName == "changelog.txt") return "history"

        val ext = fileName.substringAfterLast('.', "")
        return fileIcons[ext.lowercase()] ?: "default"
    }

    fun listFiles(directoryPath: String, showHidden: Boolean = false): List<FileInfo> {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { showHidden || !it.isHidden }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { fileToInfo(it) }
    }

    fun listFilesRecursive(
        directoryPath: String,
        showHidden: Boolean = false,
        maxDepth: Int = 10
    ): List<FileInfo> {
        val result = mutableListOf<FileInfo>()
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) return result

        fun walk(currentDir: File, depth: Int) {
            if (depth > maxDepth) return
            val files = currentDir.listFiles() ?: return
            for (file in files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })) {
                if (!showHidden && file.isHidden) continue
                result.add(fileToInfo(file))
                if (file.isDirectory) {
                    walk(file, depth + 1)
                }
            }
        }

        walk(dir, 0)
        return result
    }

    fun getFileTree(directoryPath: String, showHidden: Boolean = false): FileTreeNode {
        val dir = File(directoryPath)
        return buildFileTree(dir, showHidden)
    }

    private fun buildFileTree(dir: File, showHidden: Boolean): FileTreeNode {
        val node = FileTreeNode(
            name = dir.name,
            path = dir.absolutePath,
            isDirectory = dir.isDirectory,
            parentPath = dir.parent
        )

        if (dir.isDirectory) {
            val children = dir.listFiles()?.filter { showHidden || !it.isHidden } ?: emptyArray()
            node.children = children
                .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .map { buildFileTree(it, showHidden) }
                .toMutableList()
            node.childCount = children.size
            node.isExpandable = children.any { it.isDirectory }
        }

        return node
    }

    fun readFile(filePath: String): String {
        return try {
            File(filePath).readText()
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read file: $filePath", e)
            throw e
        }
    }

    fun readFileBytes(filePath: String): ByteArray {
        return try {
            File(filePath).readBytes()
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read file bytes: $filePath", e)
            throw e
        }
    }

    fun writeFile(filePath: String, content: String, encoding: String = "UTF-8"): Boolean {
        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(content, java.nio.charset.Charset.forName(encoding))
            true
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to write file: $filePath", e)
            false
        }
    }

    fun writeFileBytes(filePath: String, data: ByteArray): Boolean {
        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to write file bytes: $filePath", e)
            false
        }
    }

    fun createFile(filePath: String, content: String = ""): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) return false
            file.parentFile?.mkdirs()
            file.createNewFile()
            if (content.isNotEmpty()) {
                file.writeText(content)
            }
            notifyFileCreated(filePath, file.isDirectory)
            true
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to create file: $filePath", e)
            false
        }
    }

    fun createDirectory(dirPath: String): Boolean {
        return try {
            val dir = File(dirPath)
            if (dir.exists()) return false
            val created = dir.mkdirs()
            if (created) {
                notifyFileCreated(dirPath, true)
            }
            created
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create directory: $dirPath", e)
            false
        }
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false
            val deleted = file.deleteRecursively()
            if (deleted) {
                notifyFileDeleted(filePath)
            }
            deleted
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to delete file: $filePath", e)
            false
        }
    }

    fun renameFile(oldPath: String, newPath: String): Boolean {
        return try {
            val oldFile = File(oldPath)
            val newFile = File(newPath)
            if (!oldFile.exists()) return false
            if (newFile.exists()) return false

            newFile.parentFile?.mkdirs()
            val renamed = oldFile.renameTo(newFile)
            if (renamed) {
                notifyFileRenamed(oldPath, newPath)
            }
            renamed
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to rename file: $oldPath -> $newPath", e)
            false
        }
    }

    fun copyFile(sourcePath: String, destinationPath: String): Boolean {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) return false

            val destFile = File(destinationPath)
            destFile.parentFile?.mkdirs()

            if (sourceFile.isDirectory) {
                sourceFile.copyRecursively(destFile, overwrite = true)
            } else {
                sourceFile.copyTo(destFile, overwrite = true)
            }
            notifyFileCreated(destinationPath, sourceFile.isDirectory)
            true
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to copy file: $sourcePath -> $destinationPath", e)
            false
        }
    }

    fun moveFile(sourcePath: String, destinationPath: String): Boolean {
        return renameFile(sourcePath, destinationPath)
    }

    fun fileExists(filePath: String): Boolean = File(filePath).exists()

    fun isDirectory(filePath: String): Boolean = File(filePath).isDirectory

    fun isFile(filePath: String): Boolean = File(filePath).isFile

    fun getFileSize(filePath: String): Long {
        return try {
            File(filePath).length()
        } catch (e: Exception) {
            0
        }
    }

    fun getFileSizeFormatted(filePath: String): String {
        val bytes = getFileSize(filePath)
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }

    fun getLastModified(filePath: String): Long {
        return try {
            File(filePath).lastModified()
        } catch (e: Exception) {
            0
        }
    }

    fun getParentPath(filePath: String): String? {
        return File(filePath).parent
    }

    fun getFileName(filePath: String): String {
        return File(filePath).name
    }

    fun getFileExtension(filePath: String): String {
        return File(filePath).extension
    }

    fun searchFiles(
        rootPath: String,
        query: String,
        recursive: Boolean = true,
        caseSensitive: Boolean = false,
        maxResults: Int = 100
    ): List<FileInfo> {
        val results = mutableListOf<FileInfo>()
        val rootDir = File(rootPath)
        if (!rootDir.exists()) return results

        val searchQuery = if (caseSensitive) query else query.lowercase()

        fun search(dir: File) {
            if (results.size >= maxResults) return
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (results.size >= maxResults) return
                val name = if (caseSensitive) file.name else file.name.lowercase()
                if (name.contains(searchQuery)) {
                    results.add(fileToInfo(file))
                }
                if (recursive && file.isDirectory) {
                    search(file)
                }
            }
        }

        search(rootDir)
        return results
    }

    fun watchDirectory(directoryPath: String) {
        if (watchedDirs.containsValue(directoryPath)) return

        try {
            val watchService = FileSystems.getDefault().newWatchService()
            val path = File(directoryPath).toPath()

            path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            )

            watchers[directoryPath] = watchService
            watchedDirs[directoryPath] = directoryPath

            val job = scope.launch {
                while (isValid) {
                    try {
                        val key = watchService.poll(1, TimeUnit.SECONDS) ?: continue
                        for (event in key.pollEvents()) {
                            val kind = event.kind()
                            if (kind == StandardWatchEventKinds.OVERFLOW) continue

                            val filename = event.context() as? Path ?: continue
                            val fullPath = File(directoryPath, filename.toString()).absolutePath

                            when (kind) {
                                StandardWatchEventKinds.ENTRY_CREATE -> notifyFileCreated(fullPath, false)
                                StandardWatchEventKinds.ENTRY_MODIFY -> notifyFileModified(fullPath)
                                StandardWatchEventKinds.ENTRY_DELETE -> notifyFileDeleted(fullPath)
                            }
                        }
                        key.reset()
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            watchJobs[directoryPath] = job
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to watch directory: $directoryPath", e)
        }
    }

    fun unwatchDirectory(directoryPath: String) {
        watchJobs[directoryPath]?.cancel()
        watchers[directoryPath]?.close()
        watchers.remove(directoryPath)
        watchedDirs.remove(directoryPath)
        watchJobs.remove(directoryPath)
    }

    fun getGitStatus(filePath: String): GitFileStatus {
        val parent = File(filePath).parentFile ?: return GitFileStatus.UNMODIFIED
        val gitDir = findGitDir(parent) ?: return GitFileStatus.UNMODIFIED

        try {
            val relativePath = File(filePath).relativeTo(gitDir.parentFile).path
            val process = Runtime.getRuntime().exec(
                arrayOf("git", "-C", gitDir.parentFile.absolutePath, "status", "--porcelain", relativePath)
            )
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (output.isEmpty()) return GitFileStatus.UNMODIFIED

            val statusCode = output.substring(0, 2.coerceAtMost(output.length))
            return when {
                statusCode.contains("M") -> GitFileStatus.MODIFIED
                statusCode.contains("A") -> GitFileStatus.ADDED
                statusCode.contains("D") -> GitFileStatus.DELETED
                statusCode.contains("R") -> GitFileStatus.RENAMED
                statusCode.contains("?") -> GitFileStatus.UNTRACKED
                statusCode.contains("U") -> GitFileStatus.CONFLICT
                else -> GitFileStatus.UNMODIFIED
            }
        } catch (_: Exception) {
            return GitFileStatus.UNMODIFIED
        }
    }

    fun getGitBranch(rootPath: String): String? {
        try {
            val gitDir = findGitDir(File(rootPath)) ?: return null
            val headFile = File(gitDir, "HEAD")
            if (!headFile.exists()) return null

            val headContent = headFile.readText().trim()
            if (headContent.startsWith("ref: refs/heads/")) {
                return headContent.removePrefix("ref: refs/heads/")
            }
            return headContent.take(7)
        } catch (_: Exception) {
            return null
        }
    }

    private fun findGitDir(startDir: File): File? {
        var current = startDir
        while (true) {
            val gitDir = File(current, ".git")
            if (gitDir.exists() && gitDir.isDirectory) return gitDir
            val parent = current.parentFile ?: return null
            if (parent == current) return null
            current = parent
        }
    }

    fun addListener(listener: FileSystemListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: FileSystemListener) {
        listeners.remove(listener)
    }

    private fun fileToInfo(file: File): FileInfo {
        return FileInfo(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0,
            lastModified = file.lastModified(),
            isHidden = file.isHidden,
            isReadable = file.canRead(),
            isWritable = file.canWrite(),
            isExecutable = file.canExecute(),
            iconKey = getFileIconKeyForFile(file.name),
            extension = if (file.isFile) file.extension else ""
        )
    }

    private fun notifyFileCreated(path: String, isDirectory: Boolean) {
        listeners.forEach { it.onFileCreated(path, isDirectory) }
    }

    private fun notifyFileModified(path: String) {
        listeners.forEach { it.onFileModified(path) }
    }

    private fun notifyFileDeleted(path: String) {
        listeners.forEach { it.onFileDeleted(path) }
    }

    private fun notifyFileRenamed(oldPath: String, newPath: String) {
        listeners.forEach { it.onFileRenamed(oldPath, newPath) }
    }

    companion object {
        private const val TAG = "FileSystemManager"
    }

    interface FileSystemListener {
        fun onFileCreated(path: String, isDirectory: Boolean) {}
        fun onFileModified(path: String) {}
        fun onFileDeleted(path: String) {}
        fun onFileRenamed(oldPath: String, newPath: String) {}
    }
}

data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val isHidden: Boolean = false,
    val isReadable: Boolean = true,
    val isWritable: Boolean = true,
    val isExecutable: Boolean = false,
    val iconKey: String = "default",
    val extension: String = ""
)

data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val parentPath: String? = null,
    var children: MutableList<FileTreeNode> = mutableListOf(),
    var childCount: Int = 0,
    var isExpandable: Boolean = false,
    var isExpanded: Boolean = false,
    val iconKey: String = "default"
)

enum class GitFileStatus {
    UNMODIFIED, MODIFIED, ADDED, DELETED, RENAMED, UNTRACKED, CONFLICT, IGNORED
}