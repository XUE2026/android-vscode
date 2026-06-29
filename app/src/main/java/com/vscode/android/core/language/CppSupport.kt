package com.vscode.android.core.language

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File

class CppSupport(private val context: Context) : CompletionProvider, HoverProvider, DiagnosticProvider, FormattingProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun provideCompletions(content: String, position: Position): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()

        completions.addAll(KEYWORDS.map { keyword ->
            CompletionItem(
                label = keyword,
                insertText = keyword,
                detail = "C/C++ keyword",
                kind = CompletionItemKind.KEYWORD,
                sortText = "1_$keyword"
            )
        })

        completions.addAll(BUILTIN_TYPES.map { type ->
            CompletionItem(
                label = type,
                insertText = type,
                detail = "Built-in type/class",
                kind = CompletionItemKind.CLASS,
                sortText = "2_$type"
            )
        })

        completions.addAll(STL_CLASSES.map { cls ->
            CompletionItem(
                label = cls,
                insertText = cls,
                detail = "STL class",
                kind = CompletionItemKind.CLASS,
                sortText = "3_$cls"
            )
        })

        completions.addAll(STL_FUNCTIONS.map { func ->
            CompletionItem(
                label = func,
                insertText = "$func($1)",
                detail = "STL function",
                kind = CompletionItemKind.FUNCTION,
                sortText = "4_$func"
            )
        })

        completions.addAll(COMMON_HEADERS.map { header ->
            CompletionItem(
                label = header,
                insertText = "#include $header",
                detail = "Standard header",
                kind = CompletionItemKind.MODULE,
                sortText = "5_$header"
            )
        })

        completions.addAll(COMMON_SNIPPETS.map { snippet ->
            CompletionItem(
                label = snippet.label,
                insertText = snippet.insertText,
                detail = snippet.detail,
                kind = CompletionItemKind.SNIPPET,
                sortText = "6_${snippet.label}"
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

        val keywordInfo = CPP_KEYWORD_INFO[word]
        if (keywordInfo != null) {
            return HoverResult("**$word** (C/C++ keyword)\n\n$keywordInfo")
        }

        val stlInfo = CPP_STL_INFO[word]
        if (stlInfo != null) {
            return HoverResult("**$word** (STL)\n\n$stlInfo")
        }

        return null
    }

    override fun provideDiagnostics(filePath: String, content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = content.lines()

        for ((lineIndex, line) in lines.withIndex()) {
            if (line.trim().isEmpty()) continue

            if (line.trimStart().startsWith("#include ")) {
                val header = line.trimStart().removePrefix("#include ").trim()
                if (!header.startsWith("<") && !header.startsWith("\"")) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(lineIndex, line.indexOf(header), lineIndex, line.length),
                        message = "Include directive should use <header> or \"header\" syntax",
                        severity = DiagnosticSeverity.ERROR,
                        source = "cpp"
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
                        source = "cpp"
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
                        source = "cpp"
                    ))
                }
            }

            if (line.trimEnd().endsWith(";") && line.trimStart().startsWith("#")) {
                diagnostics.add(Diagnostic(
                    range = TextRange(lineIndex, line.length - 1, lineIndex, line.length),
                    message = "Preprocessor directives should not end with ';'",
                    severity = DiagnosticSeverity.WARNING,
                    source = "cpp"
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
                line.trimStart().startsWith("while ") || line.trimStart().startsWith("switch ")) {
                val trimmed = line.trimStart()
                if (trimmed.contains("(") && !trimmed.contains(") {")) {
                    val fixed = trimmed.replace("(", "(").replace("  ", " ")
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

    fun detectHeaderFiles(rootPath: String): List<String> {
        val headers = mutableListOf<String>()
        findHeaderFiles(File(rootPath), headers)
        return headers
    }

    private fun findHeaderFiles(dir: File, headers: MutableList<String>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                findHeaderFiles(file, headers)
            } else if (file.name.endsWith(".h") || file.name.endsWith(".hpp") || file.name.endsWith(".hxx")) {
                headers.add(file.absolutePath)
            }
        }
    }

    fun detectCMakeProject(rootPath: String): Boolean {
        return File(rootPath, "CMakeLists.txt").exists()
    }

    fun detectMakefileProject(rootPath: String): Boolean {
        return File(rootPath, "Makefile").exists() || File(rootPath, "makefile").exists()
    }

    fun resolveIncludePaths(rootPath: String): List<String> {
        val paths = mutableListOf<String>()
        paths.add(rootPath)
        paths.add(File(rootPath, "include").absolutePath)
        paths.add(File(rootPath, "src").absolutePath)

        val cmakeFile = File(rootPath, "CMakeLists.txt")
        if (cmakeFile.exists()) {
            try {
                val content = cmakeFile.readText()
                val includeRegex = Regex("include_directories\\s*\\(\\s*([^)]+)\\)")
                includeRegex.findAll(content).forEach { match ->
                    val dirs = match.groupValues[1].split(Regex("\\s+"))
                    dirs.forEach { dir ->
                        val trimmed = dir.trim().removeSurrounding("\"")
                        val resolved = File(rootPath, trimmed).absolutePath
                        if (File(resolved).exists()) paths.add(resolved)
                    }
                }
            } catch (_: Exception) {}
        }

        return paths
    }

    suspend fun compileFile(filePath: String, outputPath: String? = null, flags: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val output = outputPath ?: filePath.removeSuffix(".cpp").removeSuffix(".c").removeSuffix(".cc")
            val compiler = if (filePath.endsWith(".c")) "gcc" else "g++"
            val command = mutableListOf(compiler, filePath, "-o", output)
            if (flags.isNotEmpty()) {
                command.addAll(flags.split(" "))
            }
            val process = Runtime.getRuntime().exec(command.toTypedArray())
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (stderr.isNotEmpty()) stderr else if (stdout.isNotEmpty()) stdout else "Compilation successful"
        } catch (e: Exception) {
            "Error compiling: ${e.message}"
        }
    }

    suspend fun runExecutable(executablePath: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(executablePath)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error running: ${e.message}"
        }
    }

    suspend fun cmakeBuild(rootPath: String, buildDir: String = "build"): String = withContext(Dispatchers.IO) {
        try {
            val buildPath = File(rootPath, buildDir)
            if (!buildPath.exists()) buildPath.mkdirs()

            val configure = Runtime.getRuntime().exec(arrayOf("cmake", ".."), null, buildPath)
            configure.waitFor()
            val configureError = configure.errorStream.bufferedReader().readText()
            if (configureError.isNotEmpty() && configure.exitValue() != 0) {
                return@withContext "CMake configure error: $configureError"
            }

            val build = Runtime.getRuntime().exec(arrayOf("cmake", "--build", "."), null, buildPath)
            val output = build.inputStream.bufferedReader().readText()
            val error = build.errorStream.bufferedReader().readText()
            build.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "CMake build error: ${e.message}"
        }
    }

    suspend fun makeBuild(rootPath: String, target: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val command = if (target.isEmpty()) {
                arrayOf("make", "-C", rootPath)
            } else {
                arrayOf("make", "-C", rootPath, target)
            }
            val process = Runtime.getRuntime().exec(command)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Make build error: ${e.message}"
        }
    }

    fun getRunConfiguration(filePath: String): Map<String, Any> {
        val outputName = File(filePath).nameWithoutExtension
        return mapOf(
            "type" to "cpp",
            "name" to "C/C++: $outputName",
            "program" to filePath,
            "output" to outputName,
            "args" to emptyList<String>(),
            "compiler" to if (filePath.endsWith(".c")) "gcc" else "g++",
            "flags" to "-std=c++17 -O2"
        )
    }

    fun getDebugConfiguration(filePath: String): Map<String, Any> {
        val outputName = File(filePath).nameWithoutExtension
        return mapOf(
            "type" to "cppdbg",
            "name" to "C/C++ Debug: $outputName",
            "program" to outputName,
            "args" to emptyList<String>(),
            "compiler" to if (filePath.endsWith(".c")) "gcc" else "g++",
            "flags" to "-std=c++17 -g -O0"
        )
    }

    private fun getWordAtPosition(text: String, position: Position): String? {
        val lines = text.lines()
        if (position.line >= lines.size) return null
        val line = lines[position.line]
        if (position.character >= line.length) return null

        var start = position.character
        while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')) {
            start--
        }
        var end = position.character
        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) {
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
            "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
            "bool", "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t",
            "class", "compl", "concept", "const", "consteval", "constexpr", "constinit",
            "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype",
            "default", "delete", "do", "double", "dynamic_cast", "else", "enum",
            "explicit", "export", "extern", "false", "float", "for", "friend",
            "goto", "if", "inline", "int", "long", "mutable", "namespace", "new",
            "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq",
            "private", "protected", "public", "register", "reinterpret_cast", "requires",
            "return", "short", "signed", "sizeof", "static", "static_assert",
            "static_cast", "struct", "switch", "template", "this", "thread_local",
            "throw", "true", "try", "typedef", "typeid", "typename", "union",
            "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while",
            "xor", "xor_eq", "override", "final", "include", "define", "ifdef",
            "ifndef", "endif", "pragma", "error", "line", "undef"
        )

        val BUILTIN_TYPES = setOf(
            "int", "long", "short", "char", "float", "double", "bool", "void",
            "unsigned", "signed", "wchar_t", "size_t", "ssize_t", "ptrdiff_t",
            "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t",
            "uint32_t", "uint64_t", "nullptr_t", "max_align_t", "byte",
            "FILE", "va_list", "time_t", "clock_t", "div_t", "ldiv_t"
        )

        val STL_CLASSES = setOf(
            "string", "wstring", "u16string", "u32string", "string_view",
            "vector", "deque", "list", "forward_list", "array",
            "set", "multiset", "map", "multimap", "unordered_set",
            "unordered_multiset", "unordered_map", "unordered_multimap",
            "stack", "queue", "priority_queue", "bitset", "valarray",
            "pair", "tuple", "optional", "variant", "any",
            "shared_ptr", "unique_ptr", "weak_ptr", "auto_ptr",
            "function", "bind", "reference_wrapper", "mem_fn",
            "thread", "mutex", "lock_guard", "unique_lock", "shared_lock",
            "condition_variable", "future", "promise", "packaged_task",
            "atomic", "atomic_flag", "once_flag", "call_once",
            "ifstream", "ofstream", "fstream", "stringstream", "istringstream",
            "ostringstream", "iostream", "istream", "ostream", "cin", "cout",
            "cerr", "clog", "streambuf", "filebuf", "stringbuf",
            "regex", "smatch", "cmatch", "regex_iterator", "regex_token_iterator",
            "exception", "runtime_error", "logic_error", "bad_alloc", "bad_cast",
            "bad_typeid", "out_of_range", "invalid_argument", "length_error",
            "iterator", "reverse_iterator", "move_iterator", "back_insert_iterator",
            "front_insert_iterator", "insert_iterator", "istream_iterator", "ostream_iterator"
        )

        val STL_FUNCTIONS = setOf(
            "make_shared", "make_unique", "make_pair", "make_tuple",
            "move", "forward", "swap", "exchange",
            "get", "tie", "apply", "visit",
            "min", "max", "clamp", "abs", "gcd", "lcm",
            "sort", "stable_sort", "partial_sort", "nth_element",
            "find", "find_if", "find_if_not", "count", "count_if",
            "search", "binary_search", "lower_bound", "upper_bound",
            "copy", "copy_if", "transform", "replace", "remove", "remove_if",
            "unique", "reverse", "rotate", "shuffle", "random_shuffle",
            "for_each", "accumulate", "inner_product", "partial_sum", "adjacent_difference",
            "all_of", "any_of", "none_of", "equal", "mismatch", "lexicographical_compare",
            "is_sorted", "is_heap", "is_partitioned", "is_permutation",
            "merge", "includes", "set_union", "set_intersection", "set_difference",
            "push_heap", "pop_heap", "make_heap", "sort_heap",
            "next_permutation", "prev_permutation",
            "to_string", "stoi", "stol", "stoll", "stod", "stof", "stold",
            "stoul", "stoull", "to_chars", "from_chars",
            "bind", "mem_fn", "ref", "cref", "not_fn",
            "async", "sleep_for", "sleep_until", "yield", "get_id",
            "malloc", "free", "calloc", "realloc", "memcpy", "memmove",
            "memset", "memcmp", "memchr", "strlen", "strcpy", "strncpy",
            "strcat", "strncat", "strcmp", "strncmp", "strchr", "strstr",
            "printf", "scanf", "sprintf", "snprintf", "fprintf", "fscanf",
            "fopen", "fclose", "fread", "fwrite", "fseek", "ftell", "fflush"
        )

        val COMMON_HEADERS = setOf(
            "<iostream>", "<fstream>", "<sstream>", "<iomanip>",
            "<string>", "<string_view>", "<cstring>", "<cctype>",
            "<vector>", "<deque>", "<list>", "<array>", "<forward_list>",
            "<set>", "<map>", "<unordered_set>", "<unordered_map>",
            "<stack>", "<queue>", "<bitset>", "<valarray>",
            "<algorithm>", "<numeric>", "<functional>", "<iterator>",
            "<memory>", "<utility>", "<tuple>", "<optional>", "<variant>",
            "<thread>", "<mutex>", "<condition_variable>", "<future>", "<atomic>",
            "<regex>", "<chrono>", "<random>", "<ratio>", "<ctime>",
            "<cmath>", "<complex>", "<numbers>", "<cstdlib>",
            "<exception>", "<stdexcept>", "<system_error>", "<new>", "<typeinfo>",
            "<cstdio>", "<cstdlib>", "<cstdarg>", "<cstddef>", "<cstdint>",
            "<filesystem>", "<span>", "<compare>", "<concepts>", "<ranges>",
            "<coroutine>", "<source_location>", "<format>", "<expected>",
            "<stdio.h>", "<stdlib.h>", "<string.h>", "<math.h>", "<time.h>",
            "<unistd.h>", "<fcntl.h>", "<sys/stat.h>", "<sys/types.h>"
        )

        val CPP_KEYWORD_INFO = mapOf(
            "auto" to "Automatic type deduction.",
            "const" to "Specifies that a variable's value cannot be changed.",
            "constexpr" to "Specifies that a function or variable can be evaluated at compile time.",
            "static" to "Specifies static storage duration or internal linkage.",
            "extern" to "Declares a variable or function defined in another translation unit.",
            "inline" to "Suggests inlining a function, or allows multiple definitions.",
            "virtual" to "Declares a virtual function for dynamic dispatch.",
            "override" to "Indicates a virtual function overrides a base class function.",
            "final" to "Prevents further overriding of a virtual function, or class inheritance.",
            "template" to "Defines a template for generic programming.",
            "typename" to "Declares a type parameter in a template.",
            "class" to "Defines a class in C++.",
            "struct" to "Defines a structure (class with public default access).",
            "namespace" to "Defines a namespace for scope organization.",
            "using" to "Type alias or namespace using declaration.",
            "typedef" to "Creates a type alias.",
            "enum" to "Defines an enumeration type.",
            "new" to "Allocates memory dynamically.",
            "delete" to "Deallocates memory allocated with new.",
            "try" to "Begins an exception handling block.",
            "catch" to "Catches an exception.",
            "throw" to "Throws an exception.",
            "noexcept" to "Specifies that a function does not throw exceptions.",
            "this" to "Pointer to the current object.",
            "friend" to "Grants access to private/protected members.",
            "operator" to "Defines an operator overload.",
            "explicit" to "Prevents implicit conversions.",
            "mutable" to "Allows modification of a member in a const object.",
            "volatile" to "Indicates a variable may be modified externally.",
            "static_cast" to "Compile-time type conversion.",
            "dynamic_cast" to "Runtime type conversion with checking.",
            "reinterpret_cast" to "Low-level type reinterpretation.",
            "const_cast" to "Adds or removes const/volatile qualifiers.",
            "sizeof" to "Returns the size of a type or expression.",
            "typeid" to "Returns type information at runtime.",
            "decltype" to "Deduces the type of an expression.",
            "nullptr" to "Null pointer literal.",
            "alignas" to "Specifies alignment requirement.",
            "thread_local" to "Thread-local storage duration.",
            "concept" to "Defines a concept for constraining templates (C++20).",
            "requires" to "Specifies requirements on template parameters (C++20).",
            "co_await" to "Suspends a coroutine until the awaited expression is ready (C++20).",
            "co_return" to "Returns a value from a coroutine (C++20).",
            "co_yield" to "Yields a value from a coroutine (C++20)."
        )

        val CPP_STL_INFO = mapOf(
            "vector" to "Dynamic array that can grow and shrink.",
            "string" to "Sequence of characters with many utility methods.",
            "map" to "Sorted associative container of key-value pairs.",
            "set" to "Sorted associative container of unique keys.",
            "unordered_map" to "Hash table based key-value container.",
            "unordered_set" to "Hash table based unique key container.",
            "list" to "Doubly-linked list.",
            "deque" to "Double-ended queue.",
            "queue" to "FIFO queue adapter.",
            "stack" to "LIFO stack adapter.",
            "priority_queue" to "Priority queue (heap) adapter.",
            "array" to "Fixed-size array wrapper.",
            "pair" to "Holds two values of possibly different types.",
            "tuple" to "Holds a fixed number of values of possibly different types.",
            "optional" to "Container that may or may not contain a value.",
            "variant" to "Type-safe union.",
            "any" to "Type-safe container for single values of any type.",
            "shared_ptr" to "Shared ownership smart pointer.",
            "unique_ptr" to "Exclusive ownership smart pointer.",
            "weak_ptr" to "Non-owning reference to shared_ptr.",
            "function" to "General-purpose polymorphic function wrapper.",
            "thread" to "Represents a single thread of execution.",
            "mutex" to "Mutual exclusion primitive.",
            "future" to "Access to the result of an asynchronous operation.",
            "atomic" to "Atomic operations on shared data.",
            "regex" to "Regular expression pattern matching.",
            "string_view" to "Non-owning view of a string.",
            "span" to "Non-owning view of a contiguous sequence (C++20).",
            "filesystem" to "File system path manipulation and queries (C++17).",
            "ranges" to "Range-based algorithms and views (C++20)."
        )

        val COMMON_SNIPPETS = listOf(
            SnippetInfo("include", "#include \${1:<header>}", "Include header"),
            SnippetInfo("main", "int main(int argc, char* argv[]) {\n    \${1:// code}\n    return 0;\n}", "Main function"),
            SnippetInfo("class", "class \${1:ClassName} {\npublic:\n    \${2:// members}\nprivate:\n    \${3:// members}\n};", "Class definition"),
            SnippetInfo("struct", "struct \${1:StructName} {\n    \${2:// members}\n};", "Struct definition"),
            SnippetInfo("for", "for (int \${1:i} = \${2:0}; \${1:i} < \${3:n}; ++\${1:i}) {\n    \${4:// code}\n}", "For loop"),
            SnippetInfo("foreach", "for (const auto& \${1:item} : \${2:container}) {\n    \${3:// code}\n}", "Range-based for loop"),
            SnippetInfo("if", "if (\${1:condition}) {\n    \${2:// code}\n}", "If statement"),
            SnippetInfo("ifelse", "if (\${1:condition}) {\n    \${2:// code}\n} else {\n    \${3:// code}\n}", "If-else statement"),
            SnippetInfo("while", "while (\${1:condition}) {\n    \${2:// code}\n}", "While loop"),
            SnippetInfo("switch", "switch (\${1:expr}) {\ncase \${2:val}:\n    \${3:// code}\n    break;\ndefault:\n    \${4:// code}\n    break;\n}", "Switch statement"),
            SnippetInfo("try", "try {\n    \${1:// code}\n} catch (const \${2:std::exception}& \${3:e}) {\n    \${4:// handle}\n}", "Try-catch block"),
            SnippetInfo("lambda", "[\${1:capture}](\${2:params}) -> \${3:return_type} {\n    \${4:// body}\n}", "Lambda expression"),
            SnippetInfo("cout", "std::cout << \${1:value} << std::endl;", "Print to console"),
            SnippetInfo("vector", "std::vector<\${1:Type}> \${2:vec};", "Vector declaration"),
            SnippetInfo("map", "std::map<\${1:KeyType}, \${2:ValueType}> \${3:map};", "Map declaration"),
            SnippetInfo("shared_ptr", "auto \${1:ptr} = std::make_shared<\${2:Type}>(\${3:args});", "Shared pointer"),
            SnippetInfo("unique_ptr", "auto \${1:ptr} = std::make_unique<\${2:Type}>(\${3:args});", "Unique pointer"),
            SnippetInfo("template", "template<typename \${1:T}>\n\${2:declaration}", "Template declaration"),
            SnippetInfo("namespace", "namespace \${1:name} {\n    \${2:// code}\n}", "Namespace")
        )
    }
}