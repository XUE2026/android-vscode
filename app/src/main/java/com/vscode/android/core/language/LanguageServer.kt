package com.vscode.android.core.language

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class LanguageDefinition(
    val id: String,
    val name: String,
    val extensions: List<String>,
    val aliases: List<String> = emptyList(),
    val mimeTypes: List<String> = emptyList(),
    val commentStart: String = "",
    val commentEnd: String = "",
    val lineComment: String = "",
    val blockCommentStart: String = "",
    val blockCommentEnd: String = "",
    val brackets: List<Pair<String, String>> = emptyList(),
    val autoClosingPairs: List<Pair<String, String>> = emptyList(),
    val surroundingPairs: List<Pair<String, String>> = emptyList(),
    val filenames: List<String> = emptyList(),
    val filenamePatterns: List<String> = emptyList(),
    val firstLine: String = "",
    val configuration: Map<String, Any> = emptyMap()
)

data class SyntaxToken(
    val start: Int,
    val end: Int,
    val type: SyntaxTokenType,
    val modifiers: List<String> = emptyList()
)

enum class SyntaxTokenType {
    KEYWORD, STRING, NUMBER, COMMENT, TYPE, FUNCTION,
    VARIABLE, CONSTANT, OPERATOR, CLASS_NAME, PARAMETER,
    PROPERTY, REGEX, TAG, ATTRIBUTE_NAME, ATTRIBUTE_VALUE,
    PLAIN, WHITESPACE, PUNCTUATION, DELIMITER, BRACKET,
    LABEL, DIRECTIVE, BUILTIN, BOOLEAN, NULL, ENUM_MEMBER,
    MACRO, PREPROCESSOR, ESCAPE, DECORATOR, ANNOTATION
}

data class CompletionItem(
    val label: String,
    val insertText: String = "",
    val detail: String = "",
    val documentation: String = "",
    val kind: CompletionItemKind = CompletionItemKind.TEXT,
    val sortText: String = "",
    val filterText: String = "",
    val commitCharacters: List<String> = emptyList(),
    val additionalTextEdits: List<TextEdit> = emptyList(),
    val deprecated: Boolean = false
)

enum class CompletionItemKind {
    TEXT, METHOD, FUNCTION, CONSTRUCTOR, FIELD, VARIABLE,
    CLASS, INTERFACE, MODULE, PROPERTY, UNIT, VALUE,
    ENUM, KEYWORD, SNIPPET, COLOR, FILE, REFERENCE,
    FOLDER, ENUM_MEMBER, CONSTANT, STRUCT, EVENT, OPERATOR,
    TYPE_PARAMETER
}

data class TextEdit(
    val range: TextRange,
    val newText: String
)

data class TextRange(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int
)

data class HoverResult(
    val contents: String,
    val range: TextRange? = null
)

data class Location(
    val uri: String,
    val range: TextRange
)

data class Diagnostic(
    val range: TextRange,
    val message: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val source: String = "",
    val code: String = ""
)

enum class DiagnosticSeverity {
    ERROR, WARNING, INFORMATION, HINT
}

data class TextEditResult(
    val edits: List<TextEdit>
)

data class Position(
    val line: Int,
    val character: Int
)

class LanguageServer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val languageDefinitions = ConcurrentHashMap<String, LanguageDefinition>()
    private val completionProviders = ConcurrentHashMap<String, CompletionProvider>()
    private val hoverProviders = ConcurrentHashMap<String, HoverProvider>()
    private val definitionProviders = ConcurrentHashMap<String, DefinitionProvider>()
    private val referenceProviders = ConcurrentHashMap<String, ReferenceProvider>()
    private val diagnosticProviders = ConcurrentHashMap<String, DiagnosticProvider>()
    private val formattingProviders = ConcurrentHashMap<String, FormattingProvider>()

    private val _diagnostics = MutableStateFlow<Map<String, List<Diagnostic>>>(emptyMap())
    val diagnostics: StateFlow<Map<String, List<Diagnostic>>> = _diagnostics

    fun initialize() {
        registerBuiltinLanguages()
        registerBuiltinProviders()
    }

    private fun registerBuiltinLanguages() {
        registerLanguage(LanguageDefinition(
            id = "python",
            name = "Python",
            extensions = listOf(".py", ".pyw", ".pyi"),
            aliases = listOf("py", "python3"),
            mimeTypes = listOf("text/x-python"),
            lineComment = "#",
            blockCommentStart = "\"\"\"",
            blockCommentEnd = "\"\"\"",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}"),
            autoClosingPairs = listOf("\"" to "\"", "'" to "'", "(" to ")", "[" to "]", "{" to "}"),
            surroundingPairs = listOf("\"" to "\"", "'" to "'", "(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "java",
            name = "Java",
            extensions = listOf(".java", ".jav"),
            aliases = listOf("java"),
            mimeTypes = listOf("text/x-java-source"),
            lineComment = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}"),
            autoClosingPairs = listOf("\"" to "\"", "'" to "'", "(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "cpp",
            name = "C++",
            extensions = listOf(".cpp", ".cc", ".cxx", ".c++", ".hpp", ".hxx", ".h"),
            aliases = listOf("c++", "cpp", "cxx"),
            mimeTypes = listOf("text/x-c++src"),
            lineComment = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}"),
            autoClosingPairs = listOf("\"" to "\"", "'" to "'", "(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "c",
            name = "C",
            extensions = listOf(".c", ".h"),
            aliases = listOf("c"),
            mimeTypes = listOf("text/x-csrc"),
            lineComment = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "kotlin",
            name = "Kotlin",
            extensions = listOf(".kt", ".kts"),
            aliases = listOf("kt", "kotlin"),
            mimeTypes = listOf("text/x-kotlin"),
            lineComment = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}"),
            autoClosingPairs = listOf("\"" to "\"", "'" to "'", "(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "javascript",
            name = "JavaScript",
            extensions = listOf(".js", ".jsx", ".mjs", ".cjs"),
            aliases = listOf("js", "javascript"),
            mimeTypes = listOf("text/javascript"),
            lineComment = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}"),
            autoClosingPairs = listOf("\"" to "\"", "'" to "'", "`" to "`", "(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "typescript",
            name = "TypeScript",
            extensions = listOf(".ts", ".tsx", ".mts", ".cts"),
            aliases = listOf("ts", "typescript"),
            mimeTypes = listOf("text/typescript"),
            lineComment = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}"),
            autoClosingPairs = listOf("\"" to "\"", "'" to "'", "`" to "`", "(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "html",
            name = "HTML",
            extensions = listOf(".html", ".htm", ".shtml"),
            aliases = listOf("html"),
            mimeTypes = listOf("text/html"),
            blockCommentStart = "<!--",
            blockCommentEnd = "-->",
            brackets = listOf("<" to ">")
        ))

        registerLanguage(LanguageDefinition(
            id = "css",
            name = "CSS",
            extensions = listOf(".css", ".scss", ".less"),
            aliases = listOf("css", "scss", "less"),
            mimeTypes = listOf("text/css"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            brackets = listOf("(" to ")", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "json",
            name = "JSON",
            extensions = listOf(".json", ".jsonc", ".json5"),
            aliases = listOf("json"),
            mimeTypes = listOf("application/json"),
            brackets = listOf("[" to "]", "{" to "}"),
            autoClosingPairs = listOf("\"" to "\"", "[" to "]", "{" to "}")
        ))

        registerLanguage(LanguageDefinition(
            id = "xml",
            name = "XML",
            extensions = listOf(".xml", ".xsl", ".xsd", ".plist", ".svg"),
            aliases = listOf("xml"),
            mimeTypes = listOf("application/xml", "text/xml"),
            blockCommentStart = "<!--",
            blockCommentEnd = "-->",
            brackets = listOf("<" to ">")
        ))

        registerLanguage(LanguageDefinition(
            id = "markdown",
            name = "Markdown",
            extensions = listOf(".md", ".markdown", ".mdown", ".mkd"),
            aliases = listOf("md", "markdown"),
            mimeTypes = listOf("text/markdown"),
            brackets = listOf("[" to "]", "(" to ")")
        ))

        registerLanguage(LanguageDefinition(
            id = "yaml",
            name = "YAML",
            extensions = listOf(".yml", ".yaml"),
            aliases = listOf("yml", "yaml"),
            mimeTypes = listOf("text/yaml"),
            lineComment = "#"
        ))

        registerLanguage(LanguageDefinition(
            id = "sql",
            name = "SQL",
            extensions = listOf(".sql"),
            aliases = listOf("sql"),
            mimeTypes = listOf("text/x-sql"),
            lineComment = "--",
            blockCommentStart = "/*",
            blockCommentEnd = "*/"
        ))

        registerLanguage(LanguageDefinition(
            id = "shell",
            name = "Shell Script",
            extensions = listOf(".sh", ".bash", ".zsh"),
            aliases = listOf("sh", "bash", "zsh", "shell"),
            mimeTypes = listOf("text/x-shellscript"),
            lineComment = "#"
        ))

        registerLanguage(LanguageDefinition(
            id = "plaintext",
            name = "Plain Text",
            extensions = listOf(".txt"),
            aliases = listOf("plaintext", "text"),
            mimeTypes = listOf("text/plain")
        ))
    }

    private fun registerBuiltinProviders() {
        val pythonSupport = PythonSupport(context)
        registerCompletionProvider("python", pythonSupport)
        registerHoverProvider("python", pythonSupport)
        registerDiagnosticProvider("python", pythonSupport)

        val javaSupport = JavaSupport(context)
        registerCompletionProvider("java", javaSupport)
        registerHoverProvider("java", javaSupport)
        registerDiagnosticProvider("java", javaSupport)

        val cppSupport = CppSupport(context)
        registerCompletionProvider("cpp", cppSupport)
        registerCompletionProvider("c", cppSupport)
        registerHoverProvider("cpp", cppSupport)
        registerHoverProvider("c", cppSupport)
        registerDiagnosticProvider("cpp", cppSupport)
        registerDiagnosticProvider("c", cppSupport)

        val kotlinSupport = KotlinSupport(context)
        registerCompletionProvider("kotlin", kotlinSupport)
        registerHoverProvider("kotlin", kotlinSupport)
        registerDiagnosticProvider("kotlin", kotlinSupport)
    }

    fun registerLanguage(definition: LanguageDefinition) {
        languageDefinitions[definition.id] = definition
    }

    fun unregisterLanguage(languageId: String) {
        languageDefinitions.remove(languageId)
    }

    fun getLanguage(id: String): LanguageDefinition? = languageDefinitions[id]

    fun getAllLanguages(): List<LanguageDefinition> = languageDefinitions.values.toList()

    fun detectLanguageByExtension(filePath: String): String {
        val fileName = File(filePath).name.lowercase()
        val ext = filePath.substringAfterLast('.', "").lowercase()

        for ((id, lang) in languageDefinitions) {
            if (ext.isNotEmpty() && lang.extensions.any { it.lowercase() == ".$ext" }) {
                return id
            }
            if (lang.filenames.any { it.lowercase() == fileName }) {
                return id
            }
        }

        return "plaintext"
    }

    suspend fun detectLanguageByContent(content: String): String = withContext(Dispatchers.Default) {
        val firstLine = content.lineSequence().firstOrNull()?.trim() ?: ""

        if (firstLine.startsWith("<!DOCTYPE html") || firstLine.startsWith("<html")) return@withContext "html"
        if (firstLine.startsWith("<?xml")) return@withContext "xml"
        if (firstLine.startsWith("#!")) {
            if (firstLine.contains("python")) return@withContext "python"
            if (firstLine.contains("node")) return@withContext "javascript"
            if (firstLine.contains("bash") || firstLine.contains("sh")) return@withContext "shell"
        }
        if (content.contains("package ") && content.contains("fun ") && content.contains("val ")) return@withContext "kotlin"
        if (content.contains("import java.") || content.contains("public class ")) return@withContext "java"
        if (content.contains("#include") && (content.contains("<iostream>") || content.contains("<stdio.h>"))) return@withContext "cpp"
        if (content.contains("def ") && content.contains("import ") && content.contains(":")) return@withContext "python"
        if (content.contains("function ") || content.contains("const ") || content.contains("let ")) return@withContext "javascript"
        if (content.contains("interface ") && content.contains(": ")) return@withContext "typescript"
        if (content.contains("{") && content.contains("}") && content.contains("\":")) return@withContext "json"

        "plaintext"
    }

    fun registerCompletionProvider(languageId: String, provider: CompletionProvider) {
        completionProviders[languageId] = provider
    }

    fun registerHoverProvider(languageId: String, provider: HoverProvider) {
        hoverProviders[languageId] = provider
    }

    fun registerDefinitionProvider(languageId: String, provider: DefinitionProvider) {
        definitionProviders[languageId] = provider
    }

    fun registerReferenceProvider(languageId: String, provider: ReferenceProvider) {
        referenceProviders[languageId] = provider
    }

    fun registerDiagnosticProvider(languageId: String, provider: DiagnosticProvider) {
        diagnosticProviders[languageId] = provider
    }

    fun registerFormattingProvider(languageId: String, provider: FormattingProvider) {
        formattingProviders[languageId] = provider
    }

    suspend fun getCompletions(languageId: String, content: String, position: Position): List<CompletionItem> {
        val provider = completionProviders[languageId] ?: return emptyList()
        return withContext(Dispatchers.Default) {
            provider.provideCompletions(content, position)
        }
    }

    suspend fun getHover(languageId: String, content: String, position: Position): HoverResult? {
        val provider = hoverProviders[languageId] ?: return null
        return withContext(Dispatchers.Default) {
            provider.provideHover(content, position)
        }
    }

    suspend fun getDefinition(languageId: String, content: String, position: Position, filePath: String): List<Location> {
        val provider = definitionProviders[languageId] ?: return emptyList()
        return withContext(Dispatchers.Default) {
            provider.provideDefinition(content, position, filePath)
        }
    }

    suspend fun getReferences(languageId: String, content: String, position: Position, filePath: String): List<Location> {
        val provider = referenceProviders[languageId] ?: return emptyList()
        return withContext(Dispatchers.Default) {
            provider.provideReferences(content, position, filePath)
        }
    }

    suspend fun getDiagnostics(languageId: String, filePath: String, content: String): List<Diagnostic> {
        val provider = diagnosticProviders[languageId] ?: return emptyList()
        return withContext(Dispatchers.Default) {
            try {
                provider.provideDiagnostics(filePath, content)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun formatDocument(languageId: String, content: String, options: Map<String, Any> = emptyMap()): List<TextEdit> {
        val provider = formattingProviders[languageId] ?: return emptyList()
        return withContext(Dispatchers.Default) {
            provider.provideFormatting(content, options)
        }
    }

    suspend fun runDiagnosticsForFile(filePath: String, content: String) {
        val languageId = detectLanguageByExtension(filePath)
        val diags = getDiagnostics(languageId, filePath, content)
        val current = _diagnostics.value.toMutableMap()
        current[filePath] = diags
        _diagnostics.value = current
    }

    fun clearDiagnostics(filePath: String) {
        val current = _diagnostics.value.toMutableMap()
        current.remove(filePath)
        _diagnostics.value = current
    }

    fun clearAllDiagnostics() {
        _diagnostics.value = emptyMap()
    }

    suspend fun tokenize(text: String, languageId: String): List<SyntaxToken> = withContext(Dispatchers.Default) {
        val tokens = mutableListOf<SyntaxToken>()
        val lang = languageDefinitions[languageId]
        val lineComment = lang?.lineComment ?: ""
        val blockCommentStart = lang?.blockCommentStart ?: ""
        val blockCommentEnd = lang?.blockCommentEnd ?: ""

        var i = 0
        while (i < text.length) {
            when {
                text[i].isWhitespace() -> {
                    val start = i
                    while (i < text.length && text[i].isWhitespace()) i++
                    tokens.add(SyntaxToken(start, i, SyntaxTokenType.WHITESPACE))
                }
                lineComment.isNotEmpty() && text.regionMatches(i, lineComment, 0, lineComment.length) -> {
                    val start = i
                    while (i < text.length && text[i] != '\n') i++
                    tokens.add(SyntaxToken(start, i, SyntaxTokenType.COMMENT))
                }
                blockCommentStart.isNotEmpty() && text.regionMatches(i, blockCommentStart, 0, blockCommentStart.length) -> {
                    val start = i
                    i += blockCommentStart.length
                    while (i < text.length && !text.regionMatches(i, blockCommentEnd, 0, blockCommentEnd.length)) {
                        i++
                    }
                    if (i < text.length) i += blockCommentEnd.length
                    tokens.add(SyntaxToken(start, i, SyntaxTokenType.COMMENT))
                }
                text[i] == '"' || text[i] == '\'' || text[i] == '`' -> {
                    val start = i
                    val quote = text[i]
                    i++
                    while (i < text.length) {
                        if (text[i] == '\\' && i + 1 < text.length) {
                            i += 2
                        } else if (text[i] == quote) {
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                    tokens.add(SyntaxToken(start, i, SyntaxTokenType.STRING))
                }
                text[i].isDigit() -> {
                    val start = i
                    if (text[i] == '0' && i + 1 < text.length && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
                        i += 2
                        while (i < text.length && text[i].isDigitOrHex()) i++
                    } else {
                        while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                    }
                    tokens.add(SyntaxToken(start, i, SyntaxTokenType.NUMBER))
                }
                text[i].isLetter() || text[i] == '_' || text[i] == '#' -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
                    val word = text.substring(start, i)
                    val type = when {
                        isKeyword(word, languageId) -> SyntaxTokenType.KEYWORD
                        isBuiltinType(word, languageId) -> SyntaxTokenType.TYPE
                        isBooleanLiteral(word) -> SyntaxTokenType.BOOLEAN
                        isNullLiteral(word) -> SyntaxTokenType.NULL
                        else -> SyntaxTokenType.PLAIN
                    }
                    tokens.add(SyntaxToken(start, i, type))
                }
                else -> {
                    val start = i
                    i++
                    tokens.add(SyntaxToken(start, i, SyntaxTokenType.PUNCTUATION))
                }
            }
        }
        tokens
    }

    private fun isKeyword(word: String, languageId: String): Boolean {
        return when (languageId) {
            "python" -> PythonSupport.KEYWORDS.contains(word)
            "java" -> JavaSupport.KEYWORDS.contains(word)
            "cpp", "c" -> CppSupport.KEYWORDS.contains(word)
            "kotlin" -> KotlinSupport.KEYWORDS.contains(word)
            "javascript", "typescript" -> jsKeywords.contains(word)
            else -> false
        }
    }

    private fun isBuiltinType(word: String, languageId: String): Boolean {
        return when (languageId) {
            "python" -> PythonSupport.BUILTIN_TYPES.contains(word)
            "java" -> JavaSupport.BUILTIN_TYPES.contains(word)
            "cpp", "c" -> CppSupport.BUILTIN_TYPES.contains(word)
            "kotlin" -> KotlinSupport.BUILTIN_TYPES.contains(word)
            else -> false
        }
    }

    private fun isBooleanLiteral(word: String): Boolean = word in setOf("true", "false", "True", "False", "TRUE", "FALSE")
    private fun isNullLiteral(word: String): Boolean = word in setOf("null", "None", "nil", "NULL", "nullptr", "undefined")

    private fun Char.isDigitOrHex(): Boolean = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private val jsKeywords = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default",
        "delete", "do", "else", "export", "extends", "finally", "for", "function",
        "if", "import", "in", "instanceof", "new", "return", "super", "switch",
        "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield",
        "let", "static", "async", "await", "of", "enum", "implements", "interface",
        "package", "private", "protected", "public", "abstract", "boolean", "byte",
        "char", "double", "final", "float", "goto", "int", "long", "native",
        "short", "synchronized", "throws", "transient", "volatile"
    )

    fun shutdown() {
        languageDefinitions.clear()
        completionProviders.clear()
        hoverProviders.clear()
        definitionProviders.clear()
        referenceProviders.clear()
        diagnosticProviders.clear()
        formattingProviders.clear()
        _diagnostics.value = emptyMap()
    }

    companion object {
        private const val TAG = "LanguageServer"
    }
}

interface CompletionProvider {
    fun provideCompletions(content: String, position: Position): List<CompletionItem>
}

interface HoverProvider {
    fun provideHover(content: String, position: Position): HoverResult?
}

interface DefinitionProvider {
    fun provideDefinition(content: String, position: Position, filePath: String): List<Location>
}

interface ReferenceProvider {
    fun provideReferences(content: String, position: Position, filePath: String): List<Location>
}

interface DiagnosticProvider {
    fun provideDiagnostics(filePath: String, content: String): List<Diagnostic>
}

interface FormattingProvider {
    fun provideFormatting(content: String, options: Map<String, Any>): List<TextEdit>
}