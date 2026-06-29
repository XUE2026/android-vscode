package com.vscode.android.core.editor

import java.io.File
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy

class SuggestionProvider {

    fun getSuggestions(tab: EditorTab, cursorPosition: Int): List<Suggestion> {
        val text = tab.content
        val languageId = tab.languageId
        val filePath = tab.filePath

        val suggestions = mutableListOf<Suggestion>()

        val wordStart = findWordStart(text, cursorPosition)
        val prefix = if (wordStart < cursorPosition) text.substring(wordStart, cursorPosition) else ""

        suggestions.addAll(getKeywordSuggestions(languageId, prefix))

        suggestions.addAll(getContextVariableSuggestions(text, languageId, prefix))

        if (filePath != null) {
            suggestions.addAll(getFilePathSuggestions(filePath, prefix))
        }

        suggestions.addAll(getSnippetSuggestions(languageId, prefix))

        suggestions.addAll(getLanguageSpecificSuggestions(languageId, prefix, text))

        return suggestions.distinctBy { it.label }.take(50)
    }

    private fun findWordStart(text: String, cursorPosition: Int): Int {
        var start = cursorPosition
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_' || text[start - 1] == '#')) {
            start--
        }
        return start
    }

    private fun getKeywordSuggestions(languageId: String, prefix: String): List<Suggestion> {
        val keywords = when (languageId) {
            "kotlin" -> kotlinKeywords
            "java" -> javaKeywords
            "python" -> pythonKeywords
            "cpp", "c" -> cppKeywords
            "javascript" -> jsKeywords
            "typescript" -> tsKeywords + jsKeywords
            "html" -> htmlKeywords
            "css" -> cssKeywords
            "json" -> emptyList()
            "xml" -> emptyList()
            "markdown" -> emptyList()
            "groovy" -> groovyKeywords
            "shell" -> shellKeywords
            "sql" -> sqlKeywords
            "yaml" -> emptyList()
            "go" -> goKeywords
            "rust" -> rustKeywords
            "swift" -> swiftKeywords
            "dart" -> dartKeywords
            "ruby" -> rubyKeywords
            "php" -> phpKeywords
            else -> emptyList()
        }

        return keywords
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .map { Suggestion(label = it, insertText = it, kind = SuggestionKind.KEYWORD, detail = "Keyword") }
    }

    private fun getContextVariableSuggestions(text: String, languageId: String, prefix: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val wordPattern = Regex("[a-zA-Z_][a-zA-Z0-9_]*")

        val words = wordPattern.findAll(text).map { it.value }.toSet()

        for (word in words) {
            if (word.startsWith(prefix) && word.length > prefix.length && word.length >= 2) {
                if (!isKeyword(word, languageId)) {
                    suggestions.add(Suggestion(
                        label = word,
                        insertText = word,
                        kind = SuggestionKind.VARIABLE,
                        detail = "Variable"
                    ))
                }
            }
        }

        val functionPattern = when (languageId) {
            "kotlin" -> Regex("fun\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "java" -> Regex("(?:public|private|protected|static)?\\s+[a-zA-Z_][a-zA-Z0-9_<>]*\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "python" -> Regex("def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "javascript", "typescript" -> Regex("(?:function\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "cpp", "c" -> Regex("(?:[a-zA-Z_][a-zA-Z0-9_<>*&\\s]+)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "go" -> Regex("func\\s+(?:\\([^)]*\\)\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "rust" -> Regex("fn\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "swift" -> Regex("func\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "dart" -> Regex("(?:void|int|String|bool|dynamic|Future|Widget)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            "ruby" -> Regex("def\\s+([a-zA-Z_][a-zA-Z0-9_?!]*)\\s*")
            "php" -> Regex("function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
            else -> null
        }

        functionPattern?.let { pattern ->
            pattern.findAll(text).forEach { match ->
                val funcName = match.groupValues[1]
                if (funcName.startsWith(prefix)) {
                    suggestions.add(Suggestion(
                        label = funcName,
                        insertText = "$funcName()",
                        kind = SuggestionKind.FUNCTION,
                        detail = "Function"
                    ))
                }
            }
        }

        val classPattern = Regex("(?:class|interface|enum|struct|object|trait|protocol)\\s+([a-zA-Z_][a-zA-Z0-9_]*)")
        classPattern.findAll(text).forEach { match ->
            val className = match.groupValues[1]
            if (className.startsWith(prefix)) {
                suggestions.add(Suggestion(
                    label = className,
                    insertText = className,
                    kind = SuggestionKind.CLASS,
                    detail = "Class"
                ))
            }
        }

        return suggestions
    }

    private fun getFilePathSuggestions(filePath: String, prefix: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        if (prefix.isEmpty() || !prefix.contains("/") && !prefix.contains(".")) return suggestions

        try {
            val currentFile = File(filePath)
            val parentDir = currentFile.parentFile ?: return suggestions

            val lastSlash = prefix.lastIndexOf('/')
            val searchDir = if (lastSlash >= 0) {
                File(parentDir, prefix.substring(0, lastSlash))
            } else {
                parentDir
            }
            val searchPrefix = if (lastSlash >= 0) prefix.substring(lastSlash + 1) else prefix

            if (searchDir.exists() && searchDir.isDirectory) {
                val files = searchDir.listFiles()?.filter {
                    it.name.startsWith(searchPrefix) && !it.isHidden
                } ?: return suggestions

                for (file in files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })) {
                    val relativePath = file.relativeTo(parentDir).path
                    val displayPath = if (file.isDirectory) "$relativePath/" else relativePath
                    suggestions.add(Suggestion(
                        label = displayPath,
                        insertText = displayPath,
                        kind = if (file.isDirectory) SuggestionKind.FOLDER else SuggestionKind.FILE,
                        detail = if (file.isDirectory) "Folder" else "File"
                    ))
                }
            }
        } catch (_: Exception) {
            // Ignore path resolution errors
        }

        return suggestions
    }

    private fun getSnippetSuggestions(languageId: String, prefix: String): List<Suggestion> {
        val snippets = when (languageId) {
            "kotlin" -> listOf(
                "fun" to "fun ${prefix}name() {\n    \n}",
                "class" to "class ${prefix}name {\n    \n}",
                "if" to "if (condition) {\n    \n}",
                "when" to "when (value) {\n    \n}",
                "for" to "for (item in collection) {\n    \n}",
                "main" to "fun main() {\n    \n}",
                "data" to "data class ${prefix}name(val )",
                "companion" to "companion object {\n    \n}",
                "try" to "try {\n    \n} catch (e: Exception) {\n    \n}",
                "launch" to "launch {\n    \n}"
            )
            "java" -> listOf(
                "class" to "public class ${prefix}name {\n    \n}",
                "main" to "public static void main(String[] args) {\n    \n}",
                "sysout" to "System.out.println();",
                "for" to "for (int i = 0; i < ; i++) {\n    \n}",
                "if" to "if (condition) {\n    \n}",
                "try" to "try {\n    \n} catch (Exception e) {\n    \n}",
                "public" to "public void ${prefix}name() {\n    \n}"
            )
            "python" -> listOf(
                "def" to "def ${prefix}name():\n    ",
                "class" to "class ${prefix}name:\n    def __init__(self):\n        ",
                "if" to "if condition:\n    ",
                "for" to "for item in iterable:\n    ",
                "try" to "try:\n    \nexcept Exception as e:\n    ",
                "with" to "with open('file', 'r') as f:\n    ",
                "import" to "import "
            )
            "javascript", "typescript" -> listOf(
                "function" to "function ${prefix}name() {\n    \n}",
                "const" to "const ${prefix}name = () => {\n    \n}",
                "if" to "if (condition) {\n    \n}",
                "for" to "for (let i = 0; i < ; i++) {\n    \n}",
                "try" to "try {\n    \n} catch (error) {\n    \n}",
                "import" to "import { } from '';",
                "export" to "export default ${prefix}name;",
                "log" to "console.log();"
            )
            "html" -> listOf(
                "html" to "<!DOCTYPE html>\n<html>\n<head>\n    <title></title>\n</head>\n<body>\n    \n</body>\n</html>",
                "div" to "<div>\n    \n</div>",
                "span" to "<span></span>",
                "input" to "<input type=\"\" />",
                "link" to "<link rel=\"stylesheet\" href=\"\" />",
                "script" to "<script src=\"\"></script>"
            )
            "css" -> listOf(
                "flex" to "display: flex;\njustify-content: center;\nalign-items: center;",
                "grid" to "display: grid;\ngrid-template-columns: repeat(, 1fr);",
                "media" to "@media (max-width: ) {\n    \n}",
                "margin" to "margin: 0 auto;",
                "padding" to "padding: ;"
            )
            else -> emptyList()
        }

        return snippets
            .filter { it.first.startsWith(prefix, ignoreCase = true) }
            .map { (label, body) ->
                Suggestion(
                    label = label,
                    insertText = body,
                    kind = SuggestionKind.SNIPPET,
                    detail = "Snippet"
                )
            }
    }

    private fun getLanguageSpecificSuggestions(
        languageId: String,
        prefix: String,
        text: String
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        when (languageId) {
            "python" -> {
                if (prefix.isNotEmpty()) {
                    listOf("self", "cls", "args", "kwargs", "__init__", "__str__", "__repr__", "__name__", "__main__").forEach {
                        if (it.startsWith(prefix)) {
                            suggestions.add(Suggestion(it, it, kind = SuggestionKind.VARIABLE, detail = "Built-in"))
                        }
                    }
                }
                if (text.contains("import ")) {
                    listOf("os", "sys", "json", "re", "datetime", "math", "random", "pathlib", "typing", "collections").forEach {
                        if (it.startsWith(prefix)) {
                            suggestions.add(Suggestion(it, it, kind = SuggestionKind.MODULE, detail = "Module"))
                        }
                    }
                }
            }
            "kotlin" -> {
                listOf("let", "also", "apply", "run", "with", "forEach", "map", "filter", "takeIf", "takeUnless").forEach {
                    if (it.startsWith(prefix)) {
                        suggestions.add(Suggestion(".$it", ".$it { }", kind = SuggestionKind.METHOD, detail = "Scope function"))
                    }
                }
            }
            "javascript", "typescript" -> {
                if (prefix.isNotEmpty()) {
                    listOf("console", "document", "window", "Math", "JSON", "Array", "Object", "Promise", "setTimeout", "setInterval", "fetch", "require", "module", "exports").forEach {
                        if (it.startsWith(prefix)) {
                            suggestions.add(Suggestion(it, it, kind = SuggestionKind.VARIABLE, detail = "Global"))
                        }
                    }
                }
            }
            "html" -> {
                listOf("a", "abbr", "article", "aside", "b", "blockquote", "br", "button",
                    "canvas", "code", "div", "em", "fieldset", "footer", "form", "h1", "h2",
                    "h3", "h4", "h5", "h6", "head", "header", "hr", "html", "i", "iframe",
                    "img", "input", "label", "li", "link", "main", "meta", "nav", "ol",
                    "option", "p", "pre", "section", "select", "small", "span", "strong",
                    "style", "table", "tbody", "td", "textarea", "tfoot", "th", "thead",
                    "title", "tr", "ul", "video").forEach {
                    if (it.startsWith(prefix)) {
                        suggestions.add(Suggestion(it, "<$it>$0</$it>", kind = SuggestionKind.TAG, detail = "HTML Tag"))
                    }
                }
            }
            "css" -> {
                listOf("color", "background", "margin", "padding", "border", "width", "height",
                    "display", "position", "top", "left", "right", "bottom", "font-size",
                    "font-weight", "font-family", "text-align", "text-decoration", "opacity",
                    "z-index", "overflow", "box-shadow", "transform", "transition", "animation",
                    "flex", "flex-direction", "justify-content", "align-items", "grid",
                    "grid-template-columns", "gap", "border-radius", "cursor", "visibility").forEach {
                    if (it.startsWith(prefix)) {
                        suggestions.add(Suggestion(it, "$it: ;", kind = SuggestionKind.PROPERTY, detail = "CSS Property"))
                    }
                }
            }
            "sql" -> {
                listOf("SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE",
                    "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "ORDER BY", "GROUP BY",
                    "HAVING", "LIMIT", "UNION", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN").forEach {
                    if (it.startsWith(prefix, ignoreCase = true)) {
                        suggestions.add(Suggestion(it, it, kind = SuggestionKind.KEYWORD, detail = "SQL"))
                    }
                }
            }
        }

        return suggestions
    }

    private fun isKeyword(word: String, languageId: String): Boolean {
        val keywords = when (languageId) {
            "kotlin" -> kotlinKeywords
            "java" -> javaKeywords
            "python" -> pythonKeywords
            "cpp", "c" -> cppKeywords
            "javascript" -> jsKeywords
            "typescript" -> tsKeywords + jsKeywords
            "go" -> goKeywords
            "rust" -> rustKeywords
            "swift" -> swiftKeywords
            "dart" -> dartKeywords
            "ruby" -> rubyKeywords
            "php" -> phpKeywords
            "groovy" -> groovyKeywords
            "shell" -> shellKeywords
            "sql" -> sqlKeywords
            else -> emptyList()
        }
        return word in keywords
    }

    private val kotlinKeywords = listOf(
        "package", "import", "class", "interface", "object", "enum", "fun", "val", "var",
        "const", "typealias", "as", "is", "in", "if", "else", "when", "for", "while",
        "do", "break", "continue", "return", "try", "catch", "finally", "throw",
        "this", "super", "constructor", "init", "companion", "private", "protected",
        "internal", "public", "abstract", "final", "open", "override", "sealed", "data",
        "inner", "inline", "noinline", "crossinline", "reified", "suspend", "tailrec",
        "operator", "infix", "external", "lateinit", "by", "true", "false", "null"
    )

    private val javaKeywords = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super",
        "switch", "synchronized", "this", "throw", "throws", "transient", "try",
        "void", "volatile", "while", "true", "false", "null", "var", "record", "sealed"
    )

    private val pythonKeywords = listOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally", "for",
        "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not",
        "or", "pass", "raise", "return", "try", "while", "with", "yield", "match", "case"
    )

    private val cppKeywords = listOf(
        "auto", "bool", "break", "case", "catch", "char", "class", "const", "continue",
        "default", "delete", "do", "double", "else", "enum", "explicit", "extern",
        "false", "float", "for", "friend", "goto", "if", "inline", "int", "long",
        "namespace", "new", "noexcept", "nullptr", "operator", "override", "private",
        "protected", "public", "return", "short", "signed", "sizeof", "static",
        "struct", "switch", "template", "this", "throw", "true", "try", "typedef",
        "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "while"
    )

    private val jsKeywords = listOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default",
        "delete", "do", "else", "export", "extends", "finally", "for", "function",
        "if", "import", "in", "instanceof", "let", "new", "return", "super", "switch",
        "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield",
        "async", "await", "of", "from", "true", "false", "null", "undefined"
    )

    private val tsKeywords = listOf(
        "type", "interface", "enum", "declare", "namespace", "module", "abstract",
        "implements", "readonly", "keyof", "infer", "as", "is", "any", "unknown",
        "never", "void", "string", "number", "boolean", "bigint", "symbol"
    )

    private val htmlKeywords = listOf(
        "DOCTYPE", "html", "head", "body", "title", "meta", "link", "script", "style",
        "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "tr", "td", "th",
        "form", "input", "button", "select", "option", "textarea", "label", "h1", "h2",
        "h3", "h4", "h5", "h6", "header", "footer", "nav", "section", "article",
        "aside", "main", "br", "hr", "iframe", "video", "audio", "canvas", "svg"
    )

    private val cssKeywords = listOf(
        "color", "background", "margin", "padding", "border", "width", "height",
        "display", "position", "top", "left", "right", "bottom", "font-size",
        "font-weight", "font-family", "text-align", "opacity", "z-index", "overflow",
        "box-shadow", "transform", "transition", "flex", "grid", "border-radius"
    )

    private val groovyKeywords = listOf(
        "def", "class", "interface", "enum", "extends", "implements", "import",
        "package", "new", "return", "if", "else", "for", "in", "while", "break",
        "continue", "try", "catch", "finally", "throw", "true", "false", "null",
        "this", "super", "static", "public", "private", "protected", "void", "boolean",
        "byte", "char", "short", "int", "long", "float", "double"
    )

    private val shellKeywords = listOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
        "do", "done", "in", "function", "echo", "exit", "return", "export", "source",
        "local", "declare", "readonly", "set", "unset", "trap", "test", "eval", "exec"
    )

    private val sqlKeywords = listOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
        "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW", "JOIN",
        "INNER", "LEFT", "RIGHT", "ON", "AND", "OR", "NOT", "IN", "EXISTS",
        "BETWEEN", "LIKE", "NULL", "AS", "ORDER", "BY", "GROUP", "HAVING",
        "LIMIT", "OFFSET", "UNION", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN"
    )

    private val goKeywords = listOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "else",
        "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
        "map", "package", "range", "return", "select", "struct", "switch", "type", "var"
    )

    private val rustKeywords = listOf(
        "as", "break", "const", "continue", "crate", "else", "enum", "extern",
        "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
        "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct",
        "super", "trait", "true", "type", "unsafe", "use", "where", "while",
        "async", "await", "dyn"
    )

    private val swiftKeywords = listOf(
        "class", "deinit", "enum", "extension", "func", "import", "init", "inout",
        "internal", "let", "operator", "private", "protocol", "public", "static",
        "struct", "subscript", "typealias", "var", "break", "case", "continue",
        "default", "defer", "do", "else", "fallthrough", "for", "guard", "if",
        "in", "repeat", "return", "switch", "where", "while", "as", "catch",
        "false", "is", "nil", "rethrows", "super", "self", "Self", "throw",
        "throws", "true", "try", "async", "await", "actor"
    )

    private val dartKeywords = listOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch",
        "class", "const", "continue", "default", "deferred", "do", "dynamic",
        "else", "enum", "export", "extends", "extension", "external", "factory",
        "false", "final", "finally", "for", "Function", "get", "hide", "if",
        "implements", "import", "in", "interface", "is", "late", "library", "mixin",
        "new", "null", "on", "operator", "part", "required", "rethrow", "return",
        "set", "show", "static", "super", "switch", "sync", "this", "throw",
        "true", "try", "typedef", "var", "void", "while", "with", "yield"
    )

    private val rubyKeywords = listOf(
        "BEGIN", "END", "alias", "and", "begin", "break", "case", "class", "def",
        "defined?", "do", "else", "elsif", "end", "ensure", "false", "for", "if",
        "in", "module", "next", "nil", "not", "or", "redo", "rescue", "retry",
        "return", "self", "super", "then", "true", "undef", "unless", "until",
        "when", "while", "yield"
    )

    private val phpKeywords = listOf(
        "abstract", "and", "array", "as", "break", "callable", "case", "catch",
        "class", "clone", "const", "continue", "declare", "default", "die", "do",
        "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach",
        "endif", "endswitch", "endwhile", "eval", "exit", "extends", "final",
        "finally", "fn", "for", "foreach", "function", "global", "goto", "if",
        "implements", "include", "include_once", "instanceof", "insteadof",
        "interface", "isset", "list", "match", "namespace", "new", "or", "print",
        "private", "protected", "public", "readonly", "require", "require_once",
        "return", "static", "switch", "throw", "trait", "try", "unset", "use",
        "var", "while", "xor", "yield", "true", "false", "null"
    )
}