package com.vscode.android.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Locale

object FileUtils {

    private const val TAG = "FileUtils"
    private const val BUFFER_SIZE = 8192

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "xml", "html", "htm", "css", "js", "ts", "jsx", "tsx",
        "json", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
        "java", "kt", "kts", "groovy", "scala", "py", "rb", "php", "go", "rs",
        "c", "cpp", "cc", "cxx", "h", "hpp", "hh", "hxx", "swift", "m", "mm",
        "cs", "fs", "vb", "sql", "sh", "bash", "zsh", "fish", "ps1", "bat",
        "cmake", "make", "gradle", "dockerfile", "gitignore", "editorconfig",
        "csv", "tsv", "log", "svg", "rss", "atom", "vtt", "srt", "manifest",
        "plist", "entitlements", "xcconfig", "pbxproj", "storyboard", "xib",
        "tex", "bib", "rst", "asciidoc", "adoc", "org", "nim", "dart", "ex",
        "exs", "erl", "hrl", "hs", "lhs", "elm", "vue", "svelte", "scss",
        "sass", "less", "styl", "coffee", "litcoffee", "pug", "jade", "haml",
        "slim", "twig", "jinja", "jinja2", "mustache", "handlebars", "ejs",
        "erb", "rhtml", "volt", "blade", "latte", "diff", "patch"
    )

    private val MIME_TYPES = mapOf(
        "txt" to "text/plain",
        "md" to "text/markdown",
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "ts" to "application/typescript",
        "json" to "application/json",
        "xml" to "application/xml",
        "yaml" to "text/yaml",
        "yml" to "text/yaml",
        "pdf" to "application/pdf",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon",
        "bmp" to "image/bmp",
        "webp" to "image/webp",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "zip" to "application/zip",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "jar" to "application/java-archive",
        "apk" to "application/vnd.android.package-archive",
        "dex" to "application/octet-stream",
        "class" to "application/java-vm",
        "so" to "application/octet-stream",
        "a" to "application/x-archive",
        "o" to "application/octet-stream",
        "exe" to "application/x-msdownload",
        "dll" to "application/x-msdownload",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "eot" to "application/vnd.ms-fontobject",
        "csv" to "text/csv",
        "sql" to "application/sql",
        "sh" to "application/x-sh",
        "bat" to "application/x-bat",
        "ps1" to "application/x-powershell",
        "java" to "text/x-java-source",
        "kt" to "text/x-kotlin",
        "py" to "text/x-python",
        "rb" to "text/x-ruby",
        "c" to "text/x-c",
        "cpp" to "text/x-c++",
        "h" to "text/x-c",
        "hpp" to "text/x-c++",
        "go" to "text/x-go",
        "rs" to "text/x-rust",
        "swift" to "text/x-swift"
    )

    fun readFile(path: String): String {
        return readFile(File(path))
    }

    fun readFile(file: File): String {
        if (!file.exists()) return ""
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                val encoding = detectEncoding(file)
                file.readText(encoding)
            } catch (e2: Exception) {
                file.readBytes().toString(Charsets.UTF_8)
            }
        }
    }

    fun writeFile(path: String, content: String): Boolean {
        return writeFile(File(path), content)
    }

    fun writeFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.let { ensureDirectory(it) }
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write file: ${file.absolutePath}", e)
            false
        }
    }

    fun readFileLines(path: String): List<String> {
        return readFileLines(File(path))
    }

    fun readFileLines(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                val encoding = detectEncoding(file)
                file.readLines(encoding)
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    fun getFileExtension(path: String): String {
        val fileName = getFileName(path)
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) fileName.substring(dotIndex + 1).lowercase(Locale.ROOT) else ""
    }

    fun getFileName(path: String): String {
        val normalized = normalizePath(path)
        val lastSeparator = normalized.lastIndexOf('/')
        return if (lastSeparator >= 0) normalized.substring(lastSeparator + 1) else normalized
    }

    fun getFileNameWithoutExtension(path: String): String {
        val fileName = getFileName(path)
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) fileName.substring(0, dotIndex) else fileName
    }

    fun getMimeType(path: String): String {
        val extension = getFileExtension(path)
        return MIME_TYPES[extension] ?: "application/octet-stream"
    }

    fun isTextFile(path: String): Boolean {
        return isTextFile(File(path))
    }

    fun isTextFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        val extension = getFileExtension(file.name)
        if (extension.isNotEmpty() && TEXT_EXTENSIONS.contains(extension)) {
            return true
        }
        return try {
            val bytes = file.readBytes().take(8192).toByteArray()
            isTextContent(bytes)
        } catch (_: Exception) {
            false
        }
    }

    fun isBinaryFile(path: String): Boolean {
        return !isTextFile(path)
    }

    fun isBinaryFile(file: File): Boolean {
        return !isTextFile(file)
    }

    fun getFileSize(path: String): Long {
        return getFileSize(File(path))
    }

    fun getFileSize(file: File): Long {
        return if (file.exists() && file.isFile) file.length() else 0L
    }

    fun humanReadableSize(bytes: Long): String {
        if (bytes < 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024.0 && unitIndex < units.size - 1) {
            size /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${bytes.toInt()} B"
        } else {
            "%.1f %s".format(size, units[unitIndex])
        }
    }

    fun copyFile(src: String, dest: String): Boolean {
        return copyFile(File(src), File(dest))
    }

    fun copyFile(src: File, dest: File): Boolean {
        return try {
            if (!src.exists() || !src.isFile) return false
            dest.parentFile?.let { ensureDirectory(it) }
            src.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to copy file: ${src.absolutePath} -> ${dest.absolutePath}", e)
            false
        }
    }

    fun moveFile(src: String, dest: String): Boolean {
        return moveFile(File(src), File(dest))
    }

    fun moveFile(src: File, dest: File): Boolean {
        return try {
            if (!src.exists()) return false
            dest.parentFile?.let { ensureDirectory(it) }
            if (dest.exists()) {
                dest.delete()
            }
            src.renameTo(dest)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to move file: ${src.absolutePath} -> ${dest.absolutePath}", e)
            false
        }
    }

    fun ensureDirectory(path: String): Boolean {
        return ensureDirectory(File(path))
    }

    fun ensureDirectory(file: File): Boolean {
        return if (file.exists()) {
            file.isDirectory
        } else {
            file.mkdirs()
        }
    }

    fun listFiles(path: String, filter: ((File) -> Boolean)? = null): List<File> {
        return listFiles(File(path), filter)
    }

    fun listFiles(dir: File, filter: ((File) -> Boolean)? = null): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return if (filter != null) files.filter(filter) else files.toList()
    }

    fun searchFiles(rootPath: String, query: String): List<File> {
        return searchFiles(File(rootPath), query)
    }

    fun searchFiles(rootDir: File, query: String): List<File> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
        val results = mutableListOf<File>()
        val lowerQuery = query.lowercase(Locale.ROOT)

        try {
            rootDir.walkTopDown().forEach { file ->
                if (file.name.lowercase(Locale.ROOT).contains(lowerQuery)) {
                    results.add(file)
                }
            }
        } catch (_: Exception) {
            // Ignore access errors
        }

        return results
    }

    fun getLineEnding(content: String): String {
        return when {
            content.contains("\r\n") -> "\r\n"
            content.contains("\r") -> "\r"
            else -> "\n"
        }
    }

    fun detectEncoding(bytes: ByteArray): Charset {
        if (bytes.isEmpty()) return Charsets.UTF_8

        if (bytes.size >= 3 && bytes[0].toInt() == 0xEF && bytes[1].toInt() == 0xBB && bytes[2].toInt() == 0xBF) {
            return Charsets.UTF_8
        }

        if (bytes.size >= 2 && bytes[0].toInt() == 0xFE && bytes[1].toInt() == 0xFF) {
            return Charsets.UTF_16BE
        }
        if (bytes.size >= 2 && bytes[0].toInt() == 0xFF && bytes[1].toInt() == 0xFE) {
            return Charsets.UTF_16LE
        }

        if (bytes.size >= 4 && bytes[0].toInt() == 0x00 && bytes[1].toInt() == 0x00 &&
            bytes[2].toInt() == 0xFE && bytes[3].toInt() == 0xFF) {
            return Charset.forName("UTF-32BE")
        }
        if (bytes.size >= 4 && bytes[0].toInt() == 0xFF && bytes[1].toInt() == 0xFE &&
            bytes[2].toInt() == 0x00 && bytes[3].toInt() == 0x00) {
            return Charset.forName("UTF-32LE")
        }

        return try {
            val sample = bytes.take(4096).toByteArray()
            val text = String(sample, Charsets.UTF_8)
            if (text.contains('\uFFFD')) {
                Charset.forName("windows-1252")
            } else {
                Charsets.UTF_8
            }
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    fun detectEncoding(file: File): Charset {
        return try {
            val bytes = file.readBytes().take(4096).toByteArray()
            detectEncoding(bytes)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    fun isHiddenFile(file: File): Boolean {
        return try {
            file.isHidden || file.name.startsWith(".")
        } catch (_: Exception) {
            file.name.startsWith(".")
        }
    }

    fun getRelativePath(basePath: String, fullPath: String): String {
        val base = normalizePath(basePath).trimEnd('/')
        val full = normalizePath(fullPath)

        if (!full.startsWith(base)) return full

        var relative = full.substring(base.length)
        if (relative.startsWith("/")) {
            relative = relative.substring(1)
        }
        return relative.ifEmpty { "." }
    }

    fun normalizePath(path: String): String {
        var normalized = path.replace("\\", "/")
        normalized = normalized.replace("/+".toRegex(), "/")
        normalized = normalized.replace("/./", "/")

        val parts = normalized.split("/").toMutableList()
        var i = 0
        while (i < parts.size) {
            if (parts[i] == ".." && i > 0 && parts[i - 1] != "..") {
                parts.removeAt(i)
                parts.removeAt(i - 1)
                i--
            } else {
                i++
            }
        }

        return parts.joinToString("/")
    }

    fun isParentDirectory(parent: String, child: String): Boolean {
        val normalizedParent = normalizePath(parent).trimEnd('/') + "/"
        val normalizedChild = normalizePath(child).trimEnd('/') + "/"
        return normalizedChild.startsWith(normalizedParent)
    }

    fun getFileCount(dir: File): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        return try {
            dir.walkTopDown().count { it.isFile }
        } catch (_: Exception) {
            0
        }
    }

    fun getDirectorySize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        return try {
            dir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
        } catch (_: Exception) {
            0L
        }
    }

    fun deleteDirectory(dir: File): Boolean {
        if (!dir.exists()) return true
        return try {
            dir.deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }

    fun createTempFile(prefix: String = "temp", suffix: String = ".tmp", directory: File? = null): File? {
        return try {
            val dir = directory ?: File(System.getProperty("java.io.tmpdir", "/tmp"))
            File.createTempFile(prefix, suffix, dir)
        } catch (_: Exception) {
            null
        }
    }

    fun getFileLastModified(path: String): Long {
        return getFileLastModified(File(path))
    }

    fun getFileLastModified(file: File): Long {
        return if (file.exists()) file.lastModified() else 0L
    }

    fun isFileReadable(path: String): Boolean {
        return File(path).canRead()
    }

    fun isFileWritable(path: String): Boolean {
        val file = File(path)
        if (file.exists()) return file.canWrite()
        return file.parentFile?.canWrite() ?: false
    }

    fun isFileExecutable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }

    fun getFileUri(file: File): String {
        return file.toURI().toString()
    }

    private fun isTextContent(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            if (unsigned == 0x00) return false
        }
        return true
    }
}