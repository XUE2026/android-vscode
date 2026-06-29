package com.vscode.android.core.language

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File

class KotlinSupport(private val context: Context) : CompletionProvider, HoverProvider, DiagnosticProvider, FormattingProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun provideCompletions(content: String, position: Position): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()

        completions.addAll(KEYWORDS.map { keyword ->
            CompletionItem(
                label = keyword,
                insertText = keyword,
                detail = "Kotlin keyword",
                kind = CompletionItemKind.KEYWORD,
                sortText = "1_$keyword"
            )
        })

        completions.addAll(BUILTIN_TYPES.map { type ->
            CompletionItem(
                label = type,
                insertText = type,
                detail = "Kotlin built-in type",
                kind = CompletionItemKind.CLASS,
                sortText = "2_$type"
            )
        })

        completions.addAll(STANDARD_LIBRARY.map { lib ->
            CompletionItem(
                label = lib,
                insertText = lib,
                detail = "Kotlin standard library class/function",
                kind = CompletionItemKind.CLASS,
                sortText = "3_$lib"
            )
        })

        completions.addAll(SCOPE_FUNCTIONS.map { func ->
            CompletionItem(
                label = func,
                insertText = "$func { $1 }",
                detail = "Scope function",
                kind = CompletionItemKind.FUNCTION,
                sortText = "4_$func"
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

        val keywordInfo = KOTLIN_KEYWORD_INFO[word]
        if (keywordInfo != null) {
            return HoverResult("**$word** (Kotlin keyword)\n\n$keywordInfo")
        }

        val stdlibInfo = KOTLIN_STDLIB_INFO[word]
        if (stdlibInfo != null) {
            return HoverResult("**$word** (Kotlin standard library)\n\n$stdlibInfo")
        }

        return null
    }

    override fun provideDiagnostics(filePath: String, content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = content.lines()

        for ((lineIndex, line) in lines.withIndex()) {
            if (line.trim().isEmpty()) continue

            if (line.trimStart().startsWith("fun ") && !line.contains("=") && !line.contains("{") && !line.trimEnd().endsWith("{")) {
                val trimmed = line.trimStart()
                if (!trimmed.contains("abstract") && !trimmed.contains("expect")) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(lineIndex, line.length - 1, lineIndex, line.length),
                        message = "Function declaration must have a body or be abstract",
                        severity = DiagnosticSeverity.ERROR,
                        source = "kotlin"
                    ))
                }
            }

            if (content.contains("(") || content.contains(")")) {
                val openCount = content.count { it == '(' }
                val closeCount = content.count { it == ')' }
                if (openCount != closeCount) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(0, 0, lines.size - 1, lines.last().length),
                        message = "Unbalanced parentheses: $openCount open, $closeCount close",
                        severity = DiagnosticSeverity.ERROR,
                        source = "kotlin"
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
                        source = "kotlin"
                    ))
                }
            }

            if (line.trimStart().contains("!!") && line.trimStart().contains(".")) {
                diagnostics.add(Diagnostic(
                    range = TextRange(lineIndex, line.indexOf("!!"), lineIndex, line.indexOf("!!") + 2),
                    message = "Using '!!' (not-null assertion) is discouraged; consider using safe calls or proper null checks",
                    severity = DiagnosticSeverity.WARNING,
                    source = "kotlin"
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

            if (line.trimStart().startsWith("fun ") && i > 0) {
                val prevLine = lines[i - 1].trimStart()
                if (prevLine.isNotEmpty() && !prevLine.endsWith("{") && !prevLine.endsWith("(") &&
                    !prevLine.endsWith(",") && !prevLine.endsWith("=")) {
                    edits.add(TextEdit(
                        range = TextRange(i, 0, i, 0),
                        newText = "\n"
                    ))
                }
            }
        }

        return edits
    }

    fun detectGradleKotlinProject(rootPath: String): Boolean {
        return File(rootPath, "build.gradle.kts").exists() ||
               File(rootPath, "settings.gradle.kts").exists()
    }

    fun detectKotlinDSL(rootPath: String): Boolean {
        val buildFile = File(rootPath, "build.gradle.kts")
        if (buildFile.exists()) {
            try {
                val content = buildFile.readText()
                return content.contains("kotlin(") || content.contains("kotlin {") ||
                       content.contains("id(\"org.jetbrains.kotlin")
            } catch (_: Exception) {}
        }
        return false
    }

    suspend fun compileFile(filePath: String, classpath: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        try {
            val command = mutableListOf("kotlinc", filePath, "-include-runtime", "-d",
                filePath.removeSuffix(".kt") + ".jar")
            if (classpath.isNotEmpty()) {
                command.add("-classpath")
                command.add(classpath.joinToString(":"))
            }
            val process = Runtime.getRuntime().exec(command.toTypedArray())
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (error.isNotEmpty()) error else if (output.isNotEmpty()) output else "Compilation successful"
        } catch (e: Exception) {
            "Error compiling Kotlin: ${e.message}"
        }
    }

    suspend fun runJar(jarPath: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("java", "-jar", jarPath))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error running Kotlin JAR: ${e.message}"
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

    fun getRunConfiguration(filePath: String): Map<String, Any> {
        val className = File(filePath).nameWithoutExtension
        return mapOf(
            "type" to "kotlin",
            "name" to "Kotlin: $className",
            "mainClass" to "${className}Kt",
            "program" to filePath,
            "args" to emptyList<String>(),
            "jvmArgs" to emptyList<String>()
        )
    }

    fun getAndroidKotlinConfiguration(): Map<String, Any> {
        return mapOf(
            "type" to "android",
            "name" to "Android (Kotlin): Run",
            "module" to "app",
            "variant" to "debug",
            "target" to "device",
            "language" to "kotlin"
        )
    }

    fun getKotlinExtensions(): List<String> {
        return listOf(
            "kotlinx.coroutines",
            "kotlinx.serialization",
            "kotlinx.datetime",
            "kotlinx.html",
            "kotlinx.io"
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
            "as", "as?", "break", "class", "continue", "do", "else", "false",
            "for", "fun", "if", "in", "!in", "interface", "is", "!is", "null",
            "object", "package", "return", "super", "this", "throw", "true",
            "try", "typealias", "typeof", "val", "var", "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field",
            "file", "finally", "get", "import", "init", "param", "property",
            "receiver", "set", "setparam", "where", "actual", "abstract",
            "annotation", "companion", "const", "crossinline", "data",
            "enum", "expect", "external", "final", "infix", "inline",
            "inner", "internal", "lateinit", "noinline", "open", "operator",
            "out", "override", "private", "protected", "public", "reified",
            "sealed", "suspend", "tailrec", "vararg", "field", "it",
            "also", "apply", "let", "run", "with", "assert"
        )

        val BUILTIN_TYPES = setOf(
            "Any", "Nothing", "Unit", "Int", "Long", "Short", "Byte",
            "Double", "Float", "Boolean", "Char", "String", "Array",
            "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
            "Sequence", "Pair", "Triple", "Range", "IntRange", "LongRange",
            "CharRange", "Progression", "IntProgression", "LongProgression",
            "CharProgression", "Regex", "Comparable", "Enum", "Throwable",
            "Exception", "RuntimeException", "IllegalArgumentException",
            "IllegalStateException", "NullPointerException", "IndexOutOfBoundsException",
            "NoSuchElementException", "NumberFormatException", "UnsupportedOperationException",
            "Result", "Function", "Lazy", "Comparator", "Iterator", "Iterable",
            "Collection", "Map.Entry", "ClosedRange", "UInt", "ULong", "UShort", "UByte",
            "UIntArray", "ULongArray", "UShortArray", "UByteArray"
        )

        val STANDARD_LIBRARY = setOf(
            "println", "print", "readLine", "readln", "error",
            "require", "requireNotNull", "check", "checkNotNull",
            "TODO", "runCatching", "with", "also", "let", "apply", "run",
            "takeIf", "takeUnless", "repeat", "lazy", "lazyOf",
            "filter", "map", "flatMap", "flatten", "forEach", "forEachIndexed",
            "fold", "reduce", "groupBy", "associate", "associateBy",
            "sortedBy", "sortedByDescending", "distinct", "distinctBy",
            "take", "takeWhile", "takeLast", "drop", "dropWhile", "dropLast",
            "first", "firstOrNull", "last", "lastOrNull", "single", "singleOrNull",
            "find", "findLast", "indexOf", "indexOfFirst", "indexOfLast",
            "any", "all", "none", "count", "sum", "max", "min", "average",
            "joinToString", "joinTo", "plus", "minus", "chunked", "windowed",
            "zip", "unzip", "partition", "scan", "runningFold", "runningReduce",
            "toList", "toSet", "toMap", "toMutableList", "toMutableSet", "toMutableMap",
            "filterNotNull", "mapNotNull", "filterIsInstance",
            "isNullOrEmpty", "isNullOrBlank", "orEmpty",
            "substring", "split", "replace", "trim", "trimIndent", "trimMargin",
            "toInt", "toLong", "toDouble", "toFloat", "toBoolean", "toByte", "toShort",
            "toString", "toRegex", "toBigDecimal", "toBigInteger",
            "capitalize", "decapitalize", "reversed", "random",
            "contains", "startsWith", "endsWith", "removePrefix", "removeSuffix",
            "padStart", "padEnd", "removeRange", "removeSurrounding",
            "isEmpty", "isNotEmpty", "isBlank", "isNotBlank",
            "buildString", "buildList", "buildSet", "buildMap",
            "measureTimeMillis", "measureNanoTime", "measureTimedValue",
            "withLock", "use", "close", "copyOf", "copyOfRange",
            "sort", "sortBy", "sortDescending", "sortByDescending",
            "binarySearch", "fill", "shuffle", "asSequence", "asIterable",
            "component1", "component2", "component3", "component4", "component5",
            "to", "from", "until", "downTo", "step"
        )

        val SCOPE_FUNCTIONS = setOf("let", "run", "with", "apply", "also")

        val KOTLIN_KEYWORD_INFO = mapOf(
            "val" to "Declares a read-only (immutable) local variable or property.",
            "var" to "Declares a mutable local variable or property.",
            "fun" to "Declares a function.",
            "class" to "Declares a class.",
            "object" to "Declares a singleton object.",
            "interface" to "Declares an interface.",
            "data class" to "Declares a data class with auto-generated equals, hashCode, toString, copy, and componentN functions.",
            "sealed" to "Restricts class hierarchies to a fixed set of subtypes.",
            "enum" to "Declares an enumeration class.",
            "when" to "Multi-way branch expression (replaces switch).",
            "if" to "Conditional expression (returns a value in Kotlin).",
            "for" to "Iteration over ranges, arrays, or iterables.",
            "while" to "Loop while a condition is true.",
            "do" to "Loop that executes at least once.",
            "return" to "Returns from the nearest enclosing function or anonymous function.",
            "break" to "Terminates the nearest enclosing loop.",
            "continue" to "Proceeds to the next step of the nearest enclosing loop.",
            "try" to "Exception handling block (returns a value in Kotlin).",
            "catch" to "Catches a specific exception type.",
            "finally" to "Block that always executes after try/catch.",
            "throw" to "Throws an exception.",
            "is" to "Type check operator.",
            "!is" to "Negated type check operator.",
            "as" to "Unsafe cast operator.",
            "as?" to "Safe (nullable) cast operator.",
            "in" to "Membership test / iteration.",
            "!in" to "Negated membership test.",
            "null" to "Null reference.",
            "this" to "Reference to the current receiver.",
            "super" to "Reference to the superclass.",
            "it" to "Implicit name of a single parameter in lambda expressions.",
            "by" to "Delegation operator.",
            "lateinit" to "Allows a non-null property to be initialized later.",
            "lazy" to "Delegates property initialization to a lazy block.",
            "suspend" to "Marks a function as a coroutine suspending function.",
            "inline" to "Inlines a function at the call site.",
            "noinline" to "Prevents inlining of a specific lambda parameter.",
            "crossinline" to "Prevents non-local returns in an inline lambda.",
            "reified" to "Makes a type parameter accessible at runtime in inline functions.",
            "operator" to "Marks a function as overloading an operator.",
            "infix" to "Allows a function to be called with infix notation.",
            "tailrec" to "Enables tail recursion optimization.",
            "external" to "Marks a declaration as implemented in native code.",
            "annotation" to "Declares an annotation class.",
            "typealias" to "Declares a type alias.",
            "companion" to "Declares a companion object inside a class.",
            "init" to "Initializer block inside a class.",
            "constructor" to "Declares a primary or secondary constructor.",
            "open" to "Allows a class or member to be overridden.",
            "final" to "Prevents overriding (default in Kotlin).",
            "abstract" to "Marks a class or member as abstract.",
            "override" to "Overrides a superclass member.",
            "private" to "Visibility modifier - visible only within the file/class.",
            "protected" to "Visibility modifier - visible within class and subclasses.",
            "internal" to "Visibility modifier - visible within the same module.",
            "public" to "Visibility modifier - visible everywhere (default).",
            "const" to "Compile-time constant.",
            "vararg" to "Allows a variable number of arguments.",
            "expect" to "Declares a platform-specific declaration (multiplatform).",
            "actual" to "Provides the platform-specific implementation (multiplatform).",
            "dynamic" to "Turns off Kotlin's type checker (JavaScript target).",
            "where" to "Specifies multiple upper bounds for a type parameter.",
            "assert" to "Throws an AssertionError if the value is false."
        )

        val KOTLIN_STDLIB_INFO = mapOf(
            "let" to "Calls the specified function block with `this` value as its argument and returns its result.",
            "run" to "Calls the specified function block with `this` value as its receiver and returns its result.",
            "with" to "Calls the specified function block with the given receiver and returns its result.",
            "apply" to "Calls the specified function block with `this` value as its receiver and returns `this` value.",
            "also" to "Calls the specified function block with `this` value as its argument and returns `this` value.",
            "takeIf" to "Returns `this` value if it satisfies the given predicate, or `null` otherwise.",
            "takeUnless" to "Returns `this` value if it does NOT satisfy the given predicate, or `null` otherwise.",
            "TODO" to "Always throws NotImplementedError, indicating an operation is not implemented.",
            "require" to "Throws IllegalArgumentException if the value is false.",
            "requireNotNull" to "Throws IllegalArgumentException if the value is null.",
            "check" to "Throws IllegalStateException if the value is false.",
            "checkNotNull" to "Throws IllegalStateException if the value is null.",
            "error" to "Throws IllegalStateException with the given message.",
            "runCatching" to "Calls the function and returns its result wrapped in Result, catching exceptions.",
            "repeat" to "Executes the given function action specified number of times.",
            "lazy" to "Creates a new instance of Lazy that uses the specified initialization function.",
            "buildString" to "Creates a string by applying the specified builder action.",
            "buildList" to "Creates a list by applying the specified builder action.",
            "buildSet" to "Creates a set by applying the specified builder action.",
            "buildMap" to "Creates a map by applying the specified builder action.",
            "use" to "Executes the given block function on this resource and then closes it.",
            "measureTimeMillis" to "Executes the block and returns elapsed time in milliseconds.",
            "measureNanoTime" to "Executes the block and returns elapsed time in nanoseconds.",
            "withLock" to "Executes the given action under the lock."
        )

        val COMMON_SNIPPETS = listOf(
            SnippetInfo("main", "fun main() {\n    \${1:// code}\n}", "Main function"),
            SnippetInfo("fun", "fun \${1:name}(\${2:params}): \${3:ReturnType} {\n    \${4:// body}\n}", "Function declaration"),
            SnippetInfo("class", "class \${1:ClassName}(\${2:params}) {\n    \${3:// body}\n}", "Class declaration"),
            SnippetInfo("data class", "data class \${1:ClassName}(\n    val \${2:property}: \${3:Type}\${4:}\n)", "Data class"),
            SnippetInfo("object", "object \${1:Name} {\n    \${2:// body}\n}", "Object (singleton)"),
            SnippetInfo("interface", "interface \${1:Name} {\n    \${2:// body}\n}", "Interface"),
            SnippetInfo("val", "val \${1:name}: \${2:Type} = \${3:value}", "Read-only property"),
            SnippetInfo("var", "var \${1:name}: \${2:Type} = \${3:value}", "Mutable property"),
            SnippetInfo("when", "when (\${1:expr}) {\n    \${2:value} -> \${3:// action}\n    else -> \${4:// default}\n}", "When expression"),
            SnippetInfo("if", "if (\${1:condition}) {\n    \${2:// code}\n} else {\n    \${3:// code}\n}", "If-else expression"),
            SnippetInfo("for", "for (\${1:item} in \${2:collection}) {\n    \${3:// code}\n}", "For loop"),
            SnippetInfo("fori", "for (\${1:i} in \${2:0} until \${3:n}) {\n    \${4:// code}\n}", "For loop with index"),
            SnippetInfo("try", "try {\n    \${1:// code}\n} catch (\${2:e}: \${3:Exception}) {\n    \${4:// handle}\n}", "Try-catch block"),
            SnippetInfo("nullcheck", "\${1:value}?.let { \${2:it} }", "Null check with let"),
            SnippetInfo("nullcheck2", "\${1:value} ?: \${2:default}", "Elvis operator"),
            SnippetInfo("lazy", "val \${1:name} by lazy { \${2:// initializer} }", "Lazy property"),
            SnippetInfo("companion", "companion object {\n    \${1:// members}\n}", "Companion object"),
            SnippetInfo("extension", "fun \${1:Type}.\${2:name}(\${3:params}): \${4:ReturnType} {\n    \${5:// body}\n}", "Extension function"),
            SnippetInfo("coroutine", "suspend fun \${1:name}() {\n    \${2:// code}\n}", "Coroutine function"),
            SnippetInfo("launch", "CoroutineScope(Dispatchers.\${1:IO}).launch {\n    \${2:// code}\n}", "Coroutine launch"),
            SnippetInfo("sealed", "sealed class \${1:Name} {\n    \${2:// subclasses}\n}", "Sealed class"),
            SnippetInfo("enum", "enum class \${1:Name} {\n    \${2:VALUES},\n    ;\n}", "Enum class"),
            SnippetInfo("init", "init {\n    \${1:// initialization}\n}", "Init block")
        )
    }
}