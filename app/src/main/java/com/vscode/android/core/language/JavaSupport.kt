package com.vscode.android.core.language

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File

class JavaSupport(private val context: Context) : CompletionProvider, HoverProvider, DiagnosticProvider, FormattingProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun provideCompletions(content: String, position: Position): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()

        completions.addAll(KEYWORDS.map { keyword ->
            CompletionItem(
                label = keyword,
                insertText = keyword,
                detail = "Java keyword",
                kind = CompletionItemKind.KEYWORD,
                sortText = "1_$keyword"
            )
        })

        completions.addAll(BUILTIN_TYPES.map { type ->
            CompletionItem(
                label = type,
                insertText = type,
                detail = "Java built-in type/class",
                kind = CompletionItemKind.CLASS,
                sortText = "2_$type"
            )
        })

        completions.addAll(COMMON_CLASSES.map { cls ->
            CompletionItem(
                label = cls,
                insertText = cls,
                detail = "Java common class",
                kind = CompletionItemKind.CLASS,
                sortText = "3_$cls"
            )
        })

        completions.addAll(COMMON_METHODS.map { method ->
            CompletionItem(
                label = method,
                insertText = "$method($1)",
                detail = "Common method",
                kind = CompletionItemKind.METHOD,
                sortText = "4_$method"
            )
        })

        completions.addAll(COMMON_SNIPPETS.map { snippet ->
            CompletionItem(
                label = snippet.label,
                insertText = snippet.insertText,
                detail = snippet.detail,
                kind = CompletionItemKind.SNIPPET,
                sortText = "5_${snippet.label}"
            )
        })

        val word = getWordAtPosition(content, position)
        if (word != null) {
            return completions.filter {
                it.label.startsWith(word, ignoreCase = true)
            }
        }

        return completions
    }

    override fun provideHover(content: String, position: Position): HoverResult? {
        val word = getWordAtPosition(content, position) ?: return null

        val keywordInfo = JAVA_KEYWORD_INFO[word]
        if (keywordInfo != null) {
            return HoverResult("**$word** (Java keyword)\n\n$keywordInfo")
        }

        val classInfo = JAVA_CLASS_INFO[word]
        if (classInfo != null) {
            return HoverResult("**$word** (Java class)\n\n$classInfo")
        }

        return null
    }

    override fun provideDiagnostics(filePath: String, content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = content.lines()

        for ((lineIndex, line) in lines.withIndex()) {
            if (line.trim().isEmpty()) continue

            if (line.trimStart().startsWith("import ") && !line.trimEnd().endsWith(";")) {
                diagnostics.add(Diagnostic(
                    range = TextRange(lineIndex, line.length - 1, lineIndex, line.length),
                    message = "Import statement should end with ';'",
                    severity = DiagnosticSeverity.WARNING,
                    source = "java"
                ))
            }

            if (content.contains("(") || content.contains(")")) {
                val openCount = content.count { it == '(' }
                val closeCount = content.count { it == ')' }
                if (openCount != closeCount) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(0, 0, lines.size - 1, lines.last().length),
                        message = "Unbalanced parentheses: $openCount open, $closeCount close",
                        severity = DiagnosticSeverity.ERROR,
                        source = "java"
                    ))
                }
            }

            if (content.contains("{") || content.contains("}")) {
                val openCount = content.count { it == '{' }
                val closeCount = content.count { it == '}' }
                if (openCount != closeCount) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(0, 0, lines.size - 1, lines.last().length),
                        message = "Unbalanced braces: $openCount open, $closeCount close",
                        severity = DiagnosticSeverity.ERROR,
                        source = "java"
                    ))
                }
            }

            if (content.contains("public class ") && !content.contains("public static void main")) {
                diagnostics.add(Diagnostic(
                    range = TextRange(0, 0, 0, 0),
                    message = "No main method found in class",
                    severity = DiagnosticSeverity.INFORMATION,
                    source = "java"
                ))
            }
        }

        return diagnostics
    }

    override fun provideFormatting(content: String, options: Map<String, Any>): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val lines = content.lines().toMutableList()

        for (i in lines.indices) {
            val line = lines[i]
            if (line.trimStart().isEmpty()) continue

            if (line.trimStart().startsWith("if ") || line.trimStart().startsWith("for ") ||
                line.trimStart().startsWith("while ")) {
                val trimmed = line.trimStart()
                if (trimmed.contains("(") && !trimmed.contains(") ")) {
                    val fixed = trimmed.replace("(", " (").replace("  ", " ")
                    if (fixed != trimmed) {
                        edits.add(TextEdit(
                            range = TextRange(i, 0, i, line.length),
                            newText = " ".repeat(line.length - line.trimStart().length) + fixed
                        ))
                    }
                }
            }
        }

        return edits
    }

    fun detectPackageStructure(rootPath: String): Map<String, String> {
        val packages = mutableMapOf<String, String>()
        try {
            val rootDir = File(rootPath)
            val srcDir = File(rootDir, "src/main/java")
            if (srcDir.exists()) {
                findJavaFiles(srcDir, srcDir, packages)
            } else {
                findJavaFiles(rootDir, rootDir, packages)
            }
        } catch (_: Exception) {}
        return packages
    }

    private fun findJavaFiles(baseDir: File, currentDir: File, packages: MutableMap<String, String>) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                findJavaFiles(baseDir, file, packages)
            } else if (file.name.endsWith(".java")) {
                val relativePath = file.relativeTo(baseDir).path
                val packageName = relativePath
                    .substringBeforeLast("/")
                    .replace("/", ".")
                packages[file.absolutePath] = packageName
            }
        }
    }

    fun detectGradleProject(rootPath: String): Boolean {
        return File(rootPath, "build.gradle").exists() ||
               File(rootPath, "build.gradle.kts").exists() ||
               File(rootPath, "settings.gradle").exists() ||
               File(rootPath, "settings.gradle.kts").exists()
    }

    fun detectMavenProject(rootPath: String): Boolean {
        return File(rootPath, "pom.xml").exists()
    }

    fun detectGradleWrapper(rootPath: String): Boolean {
        return File(rootPath, "gradlew").exists()
    }

    suspend fun compileFile(filePath: String, classpath: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        try {
            val command = mutableListOf("javac")
            if (classpath.isNotEmpty()) {
                command.add("-cp")
                command.add(classpath.joinToString(":"))
            }
            command.add(filePath)
            val process = Runtime.getRuntime().exec(command.toTypedArray())
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (error.isNotEmpty()) error else "Compilation successful"
        } catch (e: Exception) {
            "Compilation error: ${e.message}"
        }
    }

    suspend fun runClass(className: String, classpath: String = "."): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("java", "-cp", classpath, className))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error running Java class: ${e.message}"
        }
    }

    suspend fun gradleBuild(rootPath: String, task: String = "assembleDebug"): String = withContext(Dispatchers.IO) {
        try {
            val gradlew = if (File(rootPath, "gradlew").exists()) "./gradlew" else "gradle"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cd $rootPath && $gradlew $task"))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Gradle build error: ${e.message}"
        }
    }

    fun getRunConfiguration(className: String, classpath: String = "."): Map<String, Any> {
        return mapOf(
            "type" to "java",
            "name" to "Java: $className",
            "mainClass" to className,
            "classpath" to classpath,
            "args" to emptyList<String>(),
            "vmArgs" to emptyList<String>()
        )
    }

    fun getAndroidRunConfiguration(): Map<String, Any> {
        return mapOf(
            "type" to "android",
            "name" to "Android: Run",
            "module" to "app",
            "variant" to "debug",
            "target" to "device"
        )
    }

    private fun getWordAtPosition(text: String, position: Position): String? {
        val lines = text.lines()
        if (position.line >= lines.size) return null
        val line = lines[position.line]
        if (position.character >= line.length) return null

        var start = position.character
        while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_' || line[start - 1] == '$')) {
            start--
        }
        var end = position.character
        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_' || line[end] == '$')) {
            end++
        }
        return if (start < end) line.substring(start, end) else null
    }

    data class SnippetInfo(
        val label: String,
        val insertText: String,
        val detail: String
    )

    companion object {
        val KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null",
            "var", "record", "sealed", "permits", "yield", "module", "requires",
            "exports", "opens", "to", "uses", "provides", "with", "transitive"
        )

        val BUILTIN_TYPES = setOf(
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Byte",
            "Short", "Character", "Object", "Class", "Enum", "Thread", "Runnable",
            "Comparable", "Serializable", "Cloneable", "Iterable", "Collection",
            "List", "Set", "Map", "Queue", "Deque", "Stack", "Vector",
            "ArrayList", "LinkedList", "HashSet", "TreeSet", "HashMap", "TreeMap",
            "LinkedHashMap", "Hashtable", "PriorityQueue", "ArrayDeque",
            "StringBuilder", "StringBuffer", "Math", "System", "Runtime",
            "Process", "Exception", "RuntimeException", "Error", "Throwable",
            "IOException", "FileNotFoundException", "NullPointerException",
            "IllegalArgumentException", "IllegalStateException", "IndexOutOfBoundsException",
            "NumberFormatException", "ClassCastException", "UnsupportedOperationException",
            "ConcurrentModificationException", "NoSuchElementException", "Optional"
        )

        val COMMON_CLASSES = setOf(
            "File", "InputStream", "OutputStream", "Reader", "Writer",
            "BufferedReader", "BufferedWriter", "FileReader", "FileWriter",
            "Scanner", "PrintWriter", "PrintStream", "DataInputStream", "DataOutputStream",
            "ObjectInputStream", "ObjectOutputStream", "ByteArrayInputStream", "ByteArrayOutputStream",
            "StringReader", "StringWriter", "RandomAccessFile",
            "Socket", "ServerSocket", "DatagramSocket", "URL", "HttpURLConnection",
            "Date", "Calendar", "LocalDate", "LocalTime", "LocalDateTime",
            "Instant", "Duration", "Period", "DateTimeFormatter", "SimpleDateFormat",
            "Pattern", "Matcher", "Random", "SecureRandom", "BigInteger", "BigDecimal",
            "Stream", "Collectors", "Comparator", "Function", "Predicate",
            "Consumer", "Supplier", "Optional", "CompletableFuture",
            "AtomicInteger", "AtomicLong", "AtomicBoolean", "AtomicReference",
            "CountDownLatch", "CyclicBarrier", "Semaphore", "ExecutorService",
            "Executors", "ThreadPoolExecutor", "ReentrantLock", "Condition",
            "Future", "FutureTask", "Callable", "Phaser", "Exchanger"
        )

        val COMMON_METHODS = setOf(
            "equals", "hashCode", "toString", "clone", "finalize",
            "getClass", "notify", "notifyAll", "wait",
            "compareTo", "compare", "length", "charAt", "substring",
            "indexOf", "lastIndexOf", "contains", "startsWith", "endsWith",
            "replace", "replaceAll", "trim", "toLowerCase", "toUpperCase",
            "split", "join", "format", "valueOf", "parseInt", "parseDouble",
            "add", "remove", "get", "set", "size", "isEmpty", "clear",
            "containsKey", "containsValue", "put", "putAll", "keySet", "values",
            "entrySet", "forEach", "stream", "filter", "map", "reduce",
            "collect", "sorted", "distinct", "limit", "skip", "findFirst",
            "anyMatch", "allMatch", "noneMatch", "flatMap", "groupingBy",
            "orElse", "orElseGet", "orElseThrow", "ifPresent", "isPresent"
        )

        val JAVA_KEYWORD_INFO = mapOf(
            "public" to "Access modifier - visible to all classes.",
            "private" to "Access modifier - visible only within the same class.",
            "protected" to "Access modifier - visible within package and subclasses.",
            "static" to "Belongs to the class rather than instances.",
            "final" to "Cannot be changed or overridden.",
            "abstract" to "Cannot be instantiated, must be subclassed.",
            "class" to "Defines a class.",
            "interface" to "Defines an interface.",
            "extends" to "Indicates class inheritance.",
            "implements" to "Indicates interface implementation.",
            "new" to "Creates a new object instance.",
            "return" to "Returns a value from a method.",
            "void" to "Indicates a method returns no value.",
            "this" to "Reference to the current object.",
            "super" to "Reference to the parent class.",
            "try" to "Begins an exception handling block.",
            "catch" to "Catches an exception.",
            "finally" to "Always executes after try/catch.",
            "throw" to "Throws an exception.",
            "throws" to "Declares exceptions a method may throw.",
            "if" to "Conditional execution.",
            "else" to "Alternative conditional execution.",
            "switch" to "Multi-way branch statement.",
            "for" to "Loop statement.",
            "while" to "Loop while condition is true.",
            "do" to "Loop that executes at least once.",
            "break" to "Exits a loop or switch.",
            "continue" to "Skips to next iteration.",
            "synchronized" to "Thread synchronization.",
            "volatile" to "Variable may be modified by multiple threads.",
            "transient" to "Variable should not be serialized.",
            "enum" to "Defines an enumeration type.",
            "package" to "Declares the package name.",
            "import" to "Imports a class or package.",
            "instanceof" to "Tests if an object is an instance of a type.",
            "record" to "Defines a record class (Java 14+).",
            "sealed" to "Restricts which classes may extend/implement (Java 17+).",
            "var" to "Local variable type inference (Java 10+)."
        )

        val JAVA_CLASS_INFO = mapOf(
            "String" to "Immutable sequence of characters.",
            "Integer" to "Wrapper class for int primitive.",
            "Long" to "Wrapper class for long primitive.",
            "Double" to "Wrapper class for double primitive.",
            "Boolean" to "Wrapper class for boolean primitive.",
            "ArrayList" to "Resizable-array implementation of List.",
            "HashMap" to "Hash table based implementation of Map.",
            "HashSet" to "Hash table based implementation of Set.",
            "LinkedList" to "Doubly-linked list implementation of List and Deque.",
            "StringBuilder" to "Mutable sequence of characters (not thread-safe).",
            "StringBuffer" to "Mutable sequence of characters (thread-safe).",
            "Object" to "Root of the class hierarchy.",
            "System" to "System-level utilities (in, out, err, gc, etc.).",
            "Math" to "Mathematical functions and constants.",
            "Thread" to "A thread of execution.",
            "File" to "Abstract representation of file and directory pathnames.",
            "Optional" to "Container object which may or may not contain a non-null value.",
            "Stream" to "Sequence of elements supporting sequential and parallel operations.",
            "Exception" to "Base class for checked exceptions.",
            "RuntimeException" to "Base class for unchecked exceptions."
        )

        val COMMON_SNIPPETS = listOf(
            SnippetInfo("main", "public static void main(String[] args) {\n    \${1:// code}\n}", "Main method"),
            SnippetInfo("class", "public class \${1:ClassName} {\n    \${2:// code}\n}", "Class definition"),
            SnippetInfo("interface", "public interface \${1:InterfaceName} {\n    \${2:// methods}\n}", "Interface definition"),
            SnippetInfo("for", "for (int \${1:i} = \${2:0}; \${1:i} < \${3:max}; \${1:i}++) {\n    \${4:// code}\n}", "For loop"),
            SnippetInfo("foreach", "for (\${1:Type} \${2:item} : \${3:collection}) {\n    \${4:// code}\n}", "Enhanced for loop"),
            SnippetInfo("if", "if (\${1:condition}) {\n    \${2:// code}\n}", "If statement"),
            SnippetInfo("ifelse", "if (\${1:condition}) {\n    \${2:// code}\n} else {\n    \${3:// code}\n}", "If-else statement"),
            SnippetInfo("try", "try {\n    \${1:// code}\n} catch (\${2:Exception} \${3:e}) {\n    \${4:// handle}\n}", "Try-catch block"),
            SnippetInfo("sout", "System.out.println(\${1:});", "Print to console"),
            SnippetInfo("soutf", "System.out.printf(\"\${1:%s}%n\", \${2:args});", "Formatted print"),
            SnippetInfo("getset", "public \${1:Type} get\${2:Property}() {\n    return \${3:field};\n}\n\npublic void set\${2:Property}(\${1:Type} \${3:field}) {\n    this.\${3:field} = \${3:field};\n}", "Getter and setter"),
            SnippetInfo("equals", "@Override\npublic boolean equals(Object o) {\n    if (this == o) return true;\n    if (o == null || getClass() != o.getClass()) return false;\n    \${1:ClassName} that = (\${1:ClassName}) o;\n    return \${2:true};\n}", "Equals method"),
            SnippetInfo("hashCode", "@Override\npublic int hashCode() {\n    return Objects.hash(\${1:fields});\n}", "HashCode method"),
            SnippetInfo("lambda", "(\${1:params}) -> \${2:expression}", "Lambda expression"),
            SnippetInfo("stream", "\${1:collection}.stream().\${2:map}(\${3:e -> e})\${4:}", "Stream operation")
        )
    }
}