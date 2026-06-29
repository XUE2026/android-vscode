package com.vscode.android.core.language

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PythonSupport(private val context: Context) : CompletionProvider, HoverProvider, DiagnosticProvider, FormattingProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun provideCompletions(content: String, position: Position): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()

        completions.addAll(KEYWORDS.map { keyword ->
            CompletionItem(
                label = keyword,
                insertText = keyword,
                detail = "Python keyword",
                kind = CompletionItemKind.KEYWORD,
                sortText = "1_$keyword"
            )
        })

        completions.addAll(BUILTIN_FUNCTIONS.map { func ->
            CompletionItem(
                label = func,
                insertText = "$func()",
                detail = "Built-in function",
                kind = CompletionItemKind.FUNCTION,
                sortText = "2_$func"
            )
        })

        completions.addAll(BUILTIN_TYPES.map { type ->
            CompletionItem(
                label = type,
                insertText = type,
                detail = "Built-in type",
                kind = CompletionItemKind.CLASS,
                sortText = "3_$type"
            )
        })

        completions.addAll(COMMON_MODULES.map { mod ->
            CompletionItem(
                label = mod,
                insertText = mod,
                detail = "Standard library module",
                kind = CompletionItemKind.MODULE,
                sortText = "4_$mod"
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

        val keywordInfo = PYTHON_KEYWORD_INFO[word]
        if (keywordInfo != null) {
            return HoverResult("**$word** (Python keyword)\n\n$keywordInfo")
        }

        val builtinInfo = PYTHON_BUILTIN_INFO[word]
        if (builtinInfo != null) {
            return HoverResult("**$word** (built-in)\n\n$builtinInfo")
        }

        val moduleInfo = PYTHON_MODULE_INFO[word]
        if (moduleInfo != null) {
            return HoverResult("**$word** (standard library module)\n\n$moduleInfo")
        }

        return null
    }

    override fun provideDiagnostics(filePath: String, content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = content.lines()

        for ((lineIndex, line) in lines.withIndex()) {
            if (line.trim().isEmpty()) continue

            if (line.trimStart().startsWith("import ") || line.trimStart().startsWith("from ")) {
                val importedModule = extractImportedModule(line)
                if (importedModule != null && importedModule !in ALL_VALID_MODULES) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(lineIndex, line.indexOf(importedModule), lineIndex, line.indexOf(importedModule) + importedModule.length),
                        message = "Module '$importedModule' may not be available",
                        severity = DiagnosticSeverity.WARNING,
                        source = "python"
                    ))
                }
            }

            if (line.trimStart().startsWith("def ") || line.trimStart().startsWith("class ") ||
                line.trimStart().startsWith("if ") || line.trimStart().startsWith("for ") ||
                line.trimStart().startsWith("while ") || line.trimStart().startsWith("with ") ||
                line.trimStart().startsWith("try:") || line.trimStart().startsWith("except")) {
                if (!line.trimEnd().endsWith(":")) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(lineIndex, line.length - 1, lineIndex, line.length),
                        message = "Expected ':' at end of block statement",
                        severity = DiagnosticSeverity.ERROR,
                        source = "python"
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
                        source = "python"
                    ))
                }
            }

            if (content.contains("[") || content.contains("]")) {
                val openCount = content.count { it == '[' }
                val closeCount = content.count { it == ']' }
                if (openCount != closeCount) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(0, 0, lines.size - 1, lines.last().length),
                        message = "Unbalanced brackets: $openCount open, $closeCount close",
                        severity = DiagnosticSeverity.ERROR,
                        source = "python"
                    ))
                }
            }

            val leadingSpaces = line.length - line.trimStart().length
            if (leadingSpaces > 0 && leadingSpaces % 4 != 0) {
                if (line.trimStart().isNotEmpty()) {
                    diagnostics.add(Diagnostic(
                        range = TextRange(lineIndex, 0, lineIndex, leadingSpaces),
                        message = "Indentation should be a multiple of 4 spaces (PEP 8)",
                        severity = DiagnosticSeverity.WARNING,
                        source = "python"
                    ))
                }
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

            val indent = line.length - line.trimStart().length
            if (indent > 0 && indent % 4 != 0) {
                val correctedIndent = (indent / 4) * 4
                edits.add(TextEdit(
                    range = TextRange(i, 0, i, indent),
                    newText = " ".repeat(correctedIndent)
                ))
            }
        }

        return edits
    }

    fun detectVirtualEnvironment(rootPath: String): String? {
        val venvPaths = listOf(
            File(rootPath, "venv"),
            File(rootPath, ".venv"),
            File(rootPath, "env"),
            File(rootPath, ".env"),
            File(rootPath, "virtualenv")
        )
        for (venvDir in venvPaths) {
            if (venvDir.exists() && venvDir.isDirectory) {
                val pythonBin = File(venvDir, "bin/python")
                if (pythonBin.exists()) return pythonBin.absolutePath
                val pythonScripts = File(venvDir, "Scripts/python.exe")
                if (pythonScripts.exists()) return pythonScripts.absolutePath
            }
        }
        return null
    }

    fun detectRequirementsFile(rootPath: String): String? {
        val reqFile = File(rootPath, "requirements.txt")
        return if (reqFile.exists()) reqFile.absolutePath else null
    }

    fun detectSetupPy(rootPath: String): String? {
        val setupFile = File(rootPath, "setup.py")
        return if (setupFile.exists()) setupFile.absolutePath else null
    }

    fun detectPyprojectToml(rootPath: String): String? {
        val pyprojectFile = File(rootPath, "pyproject.toml")
        return if (pyprojectFile.exists()) pyprojectFile.absolutePath else null
    }

    suspend fun executeScript(scriptPath: String, args: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        try {
            val command = mutableListOf("python", scriptPath)
            command.addAll(args)
            val process = Runtime.getRuntime().exec(command.toTypedArray())
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error executing Python script: ${e.message}"
        }
    }

    suspend fun installPackage(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("pip", "install", packageName))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error installing package: ${e.message}"
        }
    }

    suspend fun installRequirements(requirementsPath: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("pip", "install", "-r", requirementsPath))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error installing requirements: ${e.message}"
        }
    }

    fun getRunConfiguration(scriptPath: String): Map<String, Any> {
        return mapOf(
            "type" to "python",
            "name" to "Python: ${File(scriptPath).name}",
            "program" to scriptPath,
            "args" to emptyList<String>(),
            "pythonPath" to "python",
            "console" to "integratedTerminal"
        )
    }

    fun getDebugConfiguration(scriptPath: String): Map<String, Any> {
        return mapOf(
            "type" to "python",
            "name" to "Python Debug: ${File(scriptPath).name}",
            "program" to scriptPath,
            "args" to emptyList<String>(),
            "pythonPath" to "python",
            "console" to "integratedTerminal",
            "justMyCode" to true
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

    private fun extractImportedModule(line: String): String? {
        val trimmed = line.trimStart()
        return when {
            trimmed.startsWith("import ") -> {
                val parts = trimmed.removePrefix("import ").trim().split(" ")[0].split(".")[0]
                parts
            }
            trimmed.startsWith("from ") -> {
                val parts = trimmed.removePrefix("from ").trim().split(" ")[0].split(".")[0]
                parts
            }
            else -> null
        }
    }

    data class SnippetInfo(
        val label: String,
        val insertText: String,
        val detail: String
    )

    companion object {
        val KEYWORDS = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield", "match", "case"
        )

        val BUILTIN_FUNCTIONS = setOf(
            "abs", "all", "any", "ascii", "bin", "bool", "breakpoint", "bytearray",
            "bytes", "callable", "chr", "classmethod", "compile", "complex",
            "delattr", "dict", "dir", "divmod", "enumerate", "eval", "exec",
            "filter", "float", "format", "frozenset", "getattr", "globals",
            "hasattr", "hash", "help", "hex", "id", "input", "int", "isinstance",
            "issubclass", "iter", "len", "list", "locals", "map", "max",
            "memoryview", "min", "next", "object", "oct", "open", "ord",
            "pow", "print", "property", "range", "repr", "reversed", "round",
            "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum",
            "super", "tuple", "type", "vars", "zip", "__import__"
        )

        val BUILTIN_TYPES = setOf(
            "int", "float", "str", "list", "tuple", "dict", "set", "frozenset",
            "bool", "bytes", "bytearray", "memoryview", "object", "type",
            "complex", "range", "slice", "property", "classmethod", "staticmethod",
            "Exception", "ValueError", "TypeError", "KeyError", "IndexError",
            "AttributeError", "RuntimeError", "IOError", "OSError", "FileNotFoundError",
            "ImportError", "StopIteration", "GeneratorExit", "KeyboardInterrupt",
            "SystemExit", "NotImplementedError", "UnicodeError", "ZeroDivisionError"
        )

        val COMMON_MODULES = setOf(
            "os", "sys", "math", "random", "datetime", "time", "json", "re",
            "collections", "itertools", "functools", "pathlib", "typing", "io",
            "csv", "hashlib", "logging", "threading", "multiprocessing", "subprocess",
            "argparse", "configparser", "shutil", "glob", "tempfile", "pickle",
            "sqlite3", "urllib", "http", "socket", "ssl", "email", "xml",
            "html", "unittest", "doctest", "pdb", "traceback", "warnings",
            "dataclasses", "enum", "abc", "copy", "pprint", "textwrap",
            "string", "re", "struct", "codecs", "base64", "binascii",
            "numpy", "pandas", "matplotlib", "requests", "flask", "django",
            "pytest", "black", "mypy", "pylint", "isort", "tox"
        )

        val ALL_VALID_MODULES = COMMON_MODULES + setOf(
            "asyncio", "concurrent", "contextlib", "ctypes", "curses",
            "decimal", "difflib", "dis", "distutils", "fractions",
            "gc", "gettext", "graphlib", "gzip", "heapq", "hmac",
            "imp", "inspect", "keyword", "linecache", "locale",
            "lzma", "mailbox", "marshal", "mimetypes", "mmap",
            "netrc", "nis", "nntplib", "numbers", "operator",
            "optparse", "platform", "plistlib", "poplib", "posix",
            "profile", "pstats", "pty", "pwd", "py_compile",
            "pyclbr", "pydoc", "queue", "quopri", "resource",
            "runpy", "sched", "secrets", "select", "selectors",
            "shelve", "signal", "site", "smtpd", "smtplib",
            "sndhdr", "socketserver", "spwd", "stat", "statistics",
            "stringprep", "symtable", "sysconfig", "tabnanny",
            "tarfile", "telnetlib", "termios", "test", "timeit",
            "tkinter", "token", "tokenize", "tracemalloc", "tty",
            "turtle", "turtledemo", "typing", "unicodedata",
            "uu", "uuid", "venv", "wave", "weakref", "webbrowser",
            "winreg", "winsound", "wsgiref", "xdrlib", "xmlrpc",
            "zipapp", "zipfile", "zipimport", "zlib"
        )

        val PYTHON_KEYWORD_INFO = mapOf(
            "def" to "Defines a function or method.",
            "class" to "Defines a class.",
            "import" to "Imports a module.",
            "from" to "Imports specific names from a module.",
            "if" to "Conditional execution.",
            "elif" to "Else-if conditional execution.",
            "else" to "Alternative conditional execution.",
            "for" to "Iteration over a sequence.",
            "while" to "Loop while a condition is true.",
            "try" to "Exception handling block.",
            "except" to "Exception catching block.",
            "finally" to "Code that always executes after try/except.",
            "with" to "Context manager protocol.",
            "return" to "Return a value from a function.",
            "yield" to "Yield a value from a generator.",
            "lambda" to "Anonymous function expression.",
            "async" to "Defines an asynchronous function.",
            "await" to "Awaits an asynchronous result.",
            "raise" to "Raises an exception.",
            "assert" to "Debugging assertion.",
            "pass" to "No-operation placeholder.",
            "break" to "Exit a loop.",
            "continue" to "Skip to next iteration of loop.",
            "global" to "Declare global variable.",
            "nonlocal" to "Declare nonlocal variable in nested function.",
            "del" to "Delete a variable or item.",
            "match" to "Pattern matching statement (Python 3.10+).",
            "case" to "Pattern matching case (Python 3.10+).",
            "is" to "Identity comparison operator.",
            "in" to "Membership test operator.",
            "not" to "Logical negation operator.",
            "and" to "Logical AND operator.",
            "or" to "Logical OR operator."
        )

        val PYTHON_BUILTIN_INFO = mapOf(
            "print" to "Prints values to the output stream.",
            "len" to "Returns the length (number of items) of an object.",
            "range" to "Returns an immutable sequence of numbers.",
            "type" to "Returns the type of an object.",
            "int" to "Converts a value to an integer.",
            "str" to "Converts a value to a string.",
            "list" to "Creates a new list.",
            "dict" to "Creates a new dictionary.",
            "set" to "Creates a new set.",
            "tuple" to "Creates a new tuple.",
            "open" to "Opens a file and returns a file object.",
            "input" to "Reads a line from input.",
            "enumerate" to "Returns an enumerate object with index-value pairs.",
            "zip" to "Iterates over multiple iterables in parallel.",
            "map" to "Applies a function to every item of an iterable.",
            "filter" to "Filters items from an iterable based on a function.",
            "sorted" to "Returns a sorted list from an iterable.",
            "sum" to "Sums items of an iterable.",
            "max" to "Returns the largest item.",
            "min" to "Returns the smallest item.",
            "abs" to "Returns the absolute value of a number.",
            "round" to "Rounds a number to a given precision.",
            "isinstance" to "Checks if an object is an instance of a type.",
            "hasattr" to "Checks if an object has a given attribute.",
            "getattr" to "Gets an attribute of an object.",
            "setattr" to "Sets an attribute on an object.",
            "super" to "Returns a proxy object for parent class access.",
            "property" to "Returns a property attribute.",
            "staticmethod" to "Returns a static method function.",
            "classmethod" to "Returns a class method function."
        )

        val PYTHON_MODULE_INFO = mapOf(
            "os" to "Operating system interface module.",
            "sys" to "System-specific parameters and functions.",
            "json" to "JSON encoder and decoder.",
            "re" to "Regular expression operations.",
            "math" to "Mathematical functions.",
            "random" to "Generate pseudo-random numbers.",
            "datetime" to "Basic date and time types.",
            "collections" to "Container datatypes.",
            "itertools" to "Functions creating iterators for efficient looping.",
            "pathlib" to "Object-oriented filesystem paths.",
            "typing" to "Support for type hints.",
            "logging" to "Logging facility for Python.",
            "threading" to "Thread-based parallelism.",
            "subprocess" to "Subprocess management.",
            "argparse" to "Parser for command-line options and arguments.",
            "csv" to "CSV File Reading and Writing.",
            "sqlite3" to "DB-API 2.0 interface for SQLite databases.",
            "unittest" to "Unit testing framework.",
            "dataclasses" to "Decorator and functions for creating data classes."
        )

        val COMMON_SNIPPETS = listOf(
            SnippetInfo("if __name__ == '__main__'", "if __name__ == '__main__':\n    ", "Main guard"),
            SnippetInfo("def", "def \${1:name}(\${2:args}):\n    \${3:pass}", "Define function"),
            SnippetInfo("class", "class \${1:ClassName}(\${2:object}):\n    def __init__(self\${3:}):\n        \${4:pass}", "Define class"),
            SnippetInfo("for", "for \${1:item} in \${2:iterable}:\n    \${3:pass}", "For loop"),
            SnippetInfo("try/except", "try:\n    \${1:pass}\nexcept \${2:Exception} as \${3:e}:\n    \${4:pass}", "Try-except block"),
            SnippetInfo("with", "with \${1:expression} as \${2:target}:\n    \${3:pass}", "With statement"),
            SnippetInfo("import", "import \${1:module}", "Import module"),
            SnippetInfo("from", "from \${1:module} import \${2:name}", "Import from module"),
            SnippetInfo("list comprehension", "[\${1:expr} for \${2:item} in \${3:iterable}]", "List comprehension"),
            SnippetInfo("dict comprehension", "{\${1:key}: \${2:value} for \${3:item} in \${4:iterable}}", "Dictionary comprehension"),
            SnippetInfo("lambda", "lambda \${1:args}: \${2:expr}", "Lambda expression"),
            SnippetInfo("property", "@property\ndef \${1:name}(self):\n    return self.\${2:_name}", "Property decorator"),
            SnippetInfo("print", "print(\${1:value})", "Print statement"),
            SnippetInfo("f-string", "f\"\${1:text}\"", "Formatted string")
        )
    }
}