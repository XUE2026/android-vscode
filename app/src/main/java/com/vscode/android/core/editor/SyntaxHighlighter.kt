package com.vscode.android.core.editor

import com.vscode.android.VSCodeApp

class SyntaxHighlighter {

    fun tokenize(text: String, languageId: String): List<SyntaxToken> {
        return when (languageId) {
            "kotlin" -> tokenizeKotlin(text)
            "java" -> tokenizeJava(text)
            "python" -> tokenizePython(text)
            "cpp", "c" -> tokenizeCpp(text)
            "javascript" -> tokenizeJavaScript(text)
            "typescript" -> tokenizeTypeScript(text)
            "html" -> tokenizeHtml(text)
            "css" -> tokenizeCss(text)
            "json" -> tokenizeJson(text)
            "xml" -> tokenizeXml(text)
            "markdown" -> tokenizeMarkdown(text)
            "groovy" -> tokenizeGroovy(text)
            "shell" -> tokenizeShell(text)
            "sql" -> tokenizeSql(text)
            "yaml" -> tokenizeYaml(text)
            "go" -> tokenizeGo(text)
            "rust" -> tokenizeRust(text)
            "swift" -> tokenizeSwift(text)
            "dart" -> tokenizeDart(text)
            "ruby" -> tokenizeRuby(text)
            "php" -> tokenizePhp(text)
            else -> tokenizePlainText(text)
        }
    }

    fun getLanguageName(languageId: String): String {
        return when (languageId) {
            "kotlin" -> "Kotlin"
            "java" -> "Java"
            "python" -> "Python"
            "cpp" -> "C++"
            "c" -> "C"
            "javascript" -> "JavaScript"
            "typescript" -> "TypeScript"
            "html" -> "HTML"
            "css" -> "CSS"
            "json" -> "JSON"
            "xml" -> "XML"
            "markdown" -> "Markdown"
            "groovy" -> "Groovy"
            "shell" -> "Shell Script"
            "sql" -> "SQL"
            "yaml" -> "YAML"
            "go" -> "Go"
            "rust" -> "Rust"
            "swift" -> "Swift"
            "dart" -> "Dart"
            "ruby" -> "Ruby"
            "php" -> "PHP"
            else -> "Plain Text"
        }
    }

    private fun tokenizeKotlin(text: String): List<SyntaxToken> {
        return genericTokenizer(text, kotlinKeywords, kotlinTypes, kotlinAnnotations)
    }

    private fun tokenizeJava(text: String): List<SyntaxToken> {
        return genericTokenizer(text, javaKeywords, javaTypes, javaAnnotations)
    }

    private fun tokenizePython(text: String): List<SyntaxToken> {
        return genericTokenizer(text, pythonKeywords, pythonTypes, pythonDecorators)
    }

    private fun tokenizeCpp(text: String): List<SyntaxToken> {
        return genericTokenizer(text, cppKeywords, cppTypes, emptyList())
    }

    private fun tokenizeJavaScript(text: String): List<SyntaxToken> {
        return genericTokenizer(text, jsKeywords, jsTypes, emptyList())
    }

    private fun tokenizeTypeScript(text: String): List<SyntaxToken> {
        return genericTokenizer(text, tsKeywords + jsKeywords, tsTypes + jsTypes, tsDecorators)
    }

    private fun tokenizeHtml(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < text.length) {
            when {
                text.startsWith("<!--", i) -> {
                    val end = text.indexOf("-->", i + 4)
                    val commentEnd = if (end >= 0) end + 3 else text.length
                    tokens.add(SyntaxToken(i, commentEnd, VSCodeApp.SyntaxTokenType.COMMENT))
                    i = commentEnd
                }
                text.startsWith("<!", i) -> {
                    val end = text.indexOf('>', i)
                    val declEnd = if (end >= 0) end + 1 else text.length
                    tokens.add(SyntaxToken(i, declEnd, VSCodeApp.SyntaxTokenType.KEYWORD))
                    i = declEnd
                }
                text.startsWith("<script", i, ignoreCase = true) || text.startsWith("</script", i, ignoreCase = true) -> {
                    val end = text.indexOf('>', i) + 1
                    tokens.add(SyntaxToken(i, end, VSCodeApp.SyntaxTokenType.TAG))
                    i = end
                    val scriptEnd = text.indexOf("</script>", i, ignoreCase = true)
                    val contentEnd = if (scriptEnd >= 0) scriptEnd else text.length
                    tokens.addAll(tokenizeJavaScript(text.substring(i, contentEnd)).map {
                        SyntaxToken(it.start + i, it.end + i, it.type)
                    })
                    i = contentEnd
                }
                text.startsWith("<style", i, ignoreCase = true) || text.startsWith("</style", i, ignoreCase = true) -> {
                    val end = text.indexOf('>', i) + 1
                    tokens.add(SyntaxToken(i, end, VSCodeApp.SyntaxTokenType.TAG))
                    i = end
                    val styleEnd = text.indexOf("</style>", i, ignoreCase = true)
                    val contentEnd = if (styleEnd >= 0) styleEnd else text.length
                    tokens.addAll(tokenizeCss(text.substring(i, contentEnd)).map {
                        SyntaxToken(it.start + i, it.end + i, it.type)
                    })
                    i = contentEnd
                }
                text[i] == '<' -> {
                    val end = text.indexOf('>', i)
                    val tagEnd = if (end >= 0) end + 1 else text.length
                    val tagContent = text.substring(i, tagEnd)
                    tokenizeHtmlTag(tagContent, i, tokens)
                    i = tagEnd
                }
                text[i] == '&' -> {
                    val end = text.indexOf(';', i)
                    val entityEnd = if (end >= 0) end + 1 else i + 1
                    tokens.add(SyntaxToken(i, entityEnd, VSCodeApp.SyntaxTokenType.CONSTANT))
                    i = entityEnd
                }
                else -> {
                    i++
                }
            }
        }
        return tokens
    }

    private fun tokenizeHtmlTag(tagContent: String, offset: Int, tokens: MutableList<SyntaxToken>) {
        var i = 0
        val len = tagContent.length

        while (i < len) {
            when {
                tagContent[i] == '<' || tagContent[i] == '>' || tagContent[i] == '/' -> {
                    tokens.add(SyntaxToken(offset + i, offset + i + 1, VSCodeApp.SyntaxTokenType.TAG))
                    i++
                }
                tagContent[i].isLetter() -> {
                    val start = i
                    while (i < len && (tagContent[i].isLetterOrDigit() || tagContent[i] == '-' || tagContent[i] == '_')) i++
                    tokens.add(SyntaxToken(offset + start, offset + i, VSCodeApp.SyntaxTokenType.TAG))
                }
                tagContent[i] == '"' || tagContent[i] == '\'' -> {
                    val quote = tagContent[i]
                    val start = i
                    i++
                    while (i < len && tagContent[i] != quote) {
                        if (tagContent[i] == '\\') i++
                        i++
                    }
                    if (i < len) i++
                    tokens.add(SyntaxToken(offset + start, offset + i, VSCodeApp.SyntaxTokenType.ATTRIBUTE_VALUE))
                }
                tagContent[i] == '=' -> {
                    tokens.add(SyntaxToken(offset + i, offset + i + 1, VSCodeApp.SyntaxTokenType.OPERATOR))
                    i++
                }
                else -> {
                    if (tagContent[i].isLetter()) {
                        val start = i
                        while (i < len && (tagContent[i].isLetterOrDigit() || tagContent[i] == '-')) i++
                        tokens.add(SyntaxToken(offset + start, offset + i, VSCodeApp.SyntaxTokenType.ATTRIBUTE_NAME))
                    } else {
                        i++
                    }
                }
            }
        }
    }

    private fun tokenizeCss(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < text.length) {
            when {
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2)
                    val commentEnd = if (end >= 0) end + 2 else text.length
                    tokens.add(SyntaxToken(i, commentEnd, VSCodeApp.SyntaxTokenType.COMMENT))
                    i = commentEnd
                }
                text[i] == '"' || text[i] == '\'' -> {
                    val start = i
                    val quote = text[i]
                    i++
                    while (i < text.length && text[i] != quote) {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < text.length) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.STRING))
                }
                text[i] == '#' -> {
                    val start = i
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '-' || text[i] == '_')) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.CONSTANT))
                }
                text[i] == '.' -> {
                    val start = i
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '-' || text[i] == '_')) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.CLASS_NAME))
                }
                text[i] == '@' -> {
                    val start = i
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '-')) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.KEYWORD))
                }
                text[i].isDigit() -> {
                    val start = i
                    while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == '%' || text[i] == 'e' || text[i] == 'E' || text[i] == '-' || text[i] == '+')) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.NUMBER))
                }
                text[i] == '{' || text[i] == '}' || text[i] == ':' || text[i] == ';' -> {
                    tokens.add(SyntaxToken(i, i + 1, VSCodeApp.SyntaxTokenType.OPERATOR))
                    i++
                }
                else -> {
                    i++
                }
            }
        }
        return tokens
    }

    private fun tokenizeJson(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < text.length) {
            when {
                text[i] == '"' -> {
                    val start = i
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < text.length) i++

                    val prevNonSpace = findPrevNonSpace(text, start)
                    if (prevNonSpace == ':') {
                        tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.STRING))
                    } else {
                        tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.PROPERTY))
                    }
                }
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val start = i
                    if (text[i] == '-') i++
                    while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == 'e' || text[i] == 'E' || text[i] == '+' || text[i] == '-')) {
                        if ((text[i] == 'e' || text[i] == 'E') && i + 1 < text.length && (text[i + 1] == '+' || text[i + 1] == '-')) i++
                        i++
                    }
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.NUMBER))
                }
                text.startsWith("true", i, ignoreCase = true) || text.startsWith("false", i, ignoreCase = true) || text.startsWith("null", i, ignoreCase = true) -> {
                    val len = when {
                        text.startsWith("true", i, ignoreCase = true) -> 4
                        text.startsWith("null", i, ignoreCase = true) -> 4
                        else -> 5
                    }
                    tokens.add(SyntaxToken(i, i + len, VSCodeApp.SyntaxTokenType.KEYWORD))
                    i += len
                }
                text[i] == '{' || text[i] == '}' || text[i] == '[' || text[i] == ']' || text[i] == ':' || text[i] == ',' -> {
                    tokens.add(SyntaxToken(i, i + 1, VSCodeApp.SyntaxTokenType.OPERATOR))
                    i++
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun tokenizeXml(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < text.length) {
            when {
                text.startsWith("<!--", i) -> {
                    val end = text.indexOf("-->", i + 4)
                    val commentEnd = if (end >= 0) end + 3 else text.length
                    tokens.add(SyntaxToken(i, commentEnd, VSCodeApp.SyntaxTokenType.COMMENT))
                    i = commentEnd
                }
                text.startsWith("<?", i) || text.startsWith("<!", i) -> {
                    val end = text.indexOf('>', i)
                    val declEnd = if (end >= 0) end + 1 else text.length
                    tokens.add(SyntaxToken(i, declEnd, VSCodeApp.SyntaxTokenType.KEYWORD))
                    i = declEnd
                }
                text[i] == '<' -> {
                    val end = text.indexOf('>', i)
                    val tagEnd = if (end >= 0) end + 1 else text.length
                    val tagContent = text.substring(i, tagEnd)
                    tokenizeHtmlTag(tagContent, i, tokens)
                    i = tagEnd
                }
                text[i] == '"' || text[i] == '\'' -> {
                    val start = i
                    val quote = text[i]
                    i++
                    while (i < text.length && text[i] != quote) {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < text.length) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.STRING))
                }
                text[i] == '&' -> {
                    val end = text.indexOf(';', i)
                    val entityEnd = if (end >= 0) end + 1 else i + 1
                    tokens.add(SyntaxToken(i, entityEnd, VSCodeApp.SyntaxTokenType.CONSTANT))
                    i = entityEnd
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun tokenizeMarkdown(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val lines = text.lines()

        for ((lineIdx, line) in lines.withIndex()) {
            val lineOffset = text.indexOf(line, i).let { if (it >= 0) it else i }

            if (line.startsWith("#")) {
                val end = line.indexOf(' ')
                val headerEnd = if (end >= 0) end else line.length
                tokens.add(SyntaxToken(lineOffset, lineOffset + headerEnd, VSCodeApp.SyntaxTokenType.KEYWORD))
            }

            if (line.startsWith("```") || line.startsWith("~~~")) {
                val fenceEnd = lineOffset + 3
                tokens.add(SyntaxToken(lineOffset, lineOffset + line.length, VSCodeApp.SyntaxTokenType.KEYWORD))
            }

            var j = 0
            while (j < line.length) {
                val actualOffset = lineOffset + j
                when {
                    line.startsWith("**", j) || line.startsWith("__", j) -> {
                        val end = line.indexOf(if (line[j] == '*') "**" else "__", j + 2)
                        val boldEnd = if (end >= 0) end + 2 else j + 2
                        tokens.add(SyntaxToken(actualOffset, lineOffset + boldEnd, VSCodeApp.SyntaxTokenType.CONSTANT))
                        j = boldEnd
                    }
                    line.startsWith("*", j) || line.startsWith("_", j) -> {
                        val delimiter = line[j].toString()
                        val end = line.indexOf(delimiter, j + 1)
                        val italicEnd = if (end >= 0) end + 1 else j + 1
                        tokens.add(SyntaxToken(actualOffset, lineOffset + italicEnd, VSCodeApp.SyntaxTokenType.CONSTANT))
                        j = italicEnd
                    }
                    line.startsWith("`", j) -> {
                        val end = line.indexOf('`', j + 1)
                        val codeEnd = if (end >= 0) end + 1 else j + 1
                        tokens.add(SyntaxToken(actualOffset, lineOffset + codeEnd, VSCodeApp.SyntaxTokenType.STRING))
                        j = codeEnd
                    }
                    line.startsWith("[", j) -> {
                        val end = line.indexOf(']', j)
                        if (end >= 0) {
                            tokens.add(SyntaxToken(actualOffset, lineOffset + end + 1, VSCodeApp.SyntaxTokenType.FUNCTION))
                            j = end + 1
                        } else {
                            j++
                        }
                    }
                    line.startsWith("http", j, ignoreCase = true) -> {
                        val start = j
                        while (j < line.length && !line[j].isWhitespace() && line[j] != ')') j++
                        tokens.add(SyntaxToken(lineOffset + start, lineOffset + j, VSCodeApp.SyntaxTokenType.FUNCTION))
                    }
                    else -> j++
                }
            }
            i = lineOffset + line.length + 1
        }
        return tokens
    }

    private fun tokenizeGroovy(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "def", "class", "interface", "enum", "trait", "extends", "implements",
            "import", "package", "as", "new", "return", "if", "else", "switch",
            "case", "default", "for", "in", "while", "do", "break", "continue",
            "throw", "try", "catch", "finally", "assert", "public", "private",
            "protected", "static", "final", "abstract", "synchronized", "transient",
            "volatile", "strictfp", "native", "void", "boolean", "byte", "char",
            "short", "int", "long", "float", "double", "true", "false", "null",
            "this", "super", "instanceof", "each", "any", "every", "find", "findAll",
            "collect", "grep", "with", "closure", "delegate", "owner"
        )
        return genericTokenizer(text, keywords, emptyList(), emptyList())
    }

    private fun tokenizeShell(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < text.length) {
            when {
                text[i] == '#' -> {
                    val end = text.indexOf('\n', i)
                    val commentEnd = if (end >= 0) end else text.length
                    tokens.add(SyntaxToken(i, commentEnd, VSCodeApp.SyntaxTokenType.COMMENT))
                    i = commentEnd
                }
                text[i] == '"' || text[i] == '\'' -> {
                    val start = i
                    val quote = text[i]
                    i++
                    while (i < text.length && text[i] != quote) {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < text.length) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.STRING))
                }
                text[i] == '$' && i + 1 < text.length && (text[i + 1].isLetter() || text[i + 1] == '_' || text[i + 1] == '{') -> {
                    val start = i
                    i++
                    if (text[i] == '{') {
                        i++
                        var depth = 1
                        while (i < text.length && depth > 0) {
                            when (text[i]) {
                                '{' -> depth++
                                '}' -> depth--
                            }
                            i++
                        }
                    } else {
                        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    }
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.VARIABLE))
                }
                text[i].isLetter() -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
                    val word = text.substring(start, i)
                    val type = if (word in shellKeywords) VSCodeApp.SyntaxTokenType.KEYWORD
                        else VSCodeApp.SyntaxTokenType.PLAIN
                    tokens.add(SyntaxToken(start, i, type))
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun tokenizeSql(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW", "JOIN",
            "INNER", "LEFT", "RIGHT", "OUTER", "FULL", "ON", "AND", "OR", "NOT",
            "IN", "EXISTS", "BETWEEN", "LIKE", "IS", "NULL", "AS", "ORDER", "BY",
            "GROUP", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END", "BEGIN", "COMMIT", "ROLLBACK",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "UNIQUE",
            "CHECK", "DEFAULT", "AUTO_INCREMENT", "CASCADE", "TRIGGER", "PROCEDURE",
            "FUNCTION", "DECLARE", "CURSOR", "FETCH", "OPEN", "CLOSE", "EXEC",
            "GRANT", "REVOKE", "TRUNCATE", "IF", "ASC", "DESC", "COUNT", "SUM",
            "AVG", "MAX", "MIN", "COALESCE", "CAST", "CONVERT", "TOP",
            "select", "from", "where", "insert", "into", "values", "update", "set",
            "delete", "create", "table", "alter", "drop", "index", "view", "join",
            "inner", "left", "right", "outer", "full", "on", "and", "or", "not",
            "in", "exists", "between", "like", "is", "null", "as", "order", "by",
            "group", "having", "limit", "offset", "union", "all", "distinct",
            "case", "when", "then", "else", "end", "primary", "key", "foreign",
            "references", "constraint", "unique", "check", "default", "cascade",
            "trigger", "procedure", "function", "declare", "if", "asc", "desc",
            "count", "sum", "avg", "max", "min", "coalesce", "cast", "convert", "top"
        )
        return genericTokenizer(text, keywords, emptyList(), emptyList())
    }

    private fun tokenizeYaml(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < text.length) {
            when {
                text[i] == '#' -> {
                    val end = text.indexOf('\n', i)
                    val commentEnd = if (end >= 0) end else text.length
                    tokens.add(SyntaxToken(i, commentEnd, VSCodeApp.SyntaxTokenType.COMMENT))
                    i = commentEnd
                }
                text[i] == '"' || text[i] == '\'' -> {
                    val start = i
                    val quote = text[i]
                    i++
                    while (i < text.length && text[i] != quote) {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < text.length) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.STRING))
                }
                text[i] == ':' -> {
                    tokens.add(SyntaxToken(i, i + 1, VSCodeApp.SyntaxTokenType.OPERATOR))
                    i++
                }
                text[i] == '-' && (i == 0 || text[i - 1] == '\n') -> {
                    tokens.add(SyntaxToken(i, i + 1, VSCodeApp.SyntaxTokenType.OPERATOR))
                    i++
                }
                text.startsWith("true", i, ignoreCase = true) || text.startsWith("false", i, ignoreCase = true) || text.startsWith("null", i, ignoreCase = true) || text.startsWith("yes", i, ignoreCase = true) || text.startsWith("no", i, ignoreCase = true) -> {
                    val len = when {
                        text.startsWith("true", i, ignoreCase = true) -> 4
                        text.startsWith("null", i, ignoreCase = true) -> 4
                        text.startsWith("false", i, ignoreCase = true) -> 5
                        text.startsWith("yes", i, ignoreCase = true) -> 3
                        else -> 2
                    }
                    tokens.add(SyntaxToken(i, i + len, VSCodeApp.SyntaxTokenType.KEYWORD))
                    i += len
                }
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val start = i
                    if (text[i] == '-') i++
                    while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.NUMBER))
                }
                text[i] == '&' || text[i] == '*' -> {
                    val start = i
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.VARIABLE))
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun tokenizeGo(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "break", "case", "chan", "const", "continue", "default", "defer",
            "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
            "interface", "map", "package", "range", "return", "select", "struct",
            "switch", "type", "var"
        )
        val types = listOf(
            "bool", "byte", "complex64", "complex128", "error", "float32", "float64",
            "int", "int8", "int16", "int32", "int64", "rune", "string",
            "uint", "uint8", "uint16", "uint32", "uint64", "uintptr", "nil", "true", "false"
        )
        return genericTokenizer(text, keywords, types, emptyList())
    }

    private fun tokenizeRust(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "as", "break", "const", "continue", "crate", "else", "enum", "extern",
            "false", "fn", "for", "if", "impl", "in", "let", "loop", "match",
            "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
            "struct", "super", "trait", "true", "type", "unsafe", "use", "where",
            "while", "async", "await", "dyn", "abstract", "become", "box", "do",
            "final", "macro", "override", "priv", "typeof", "unsized", "virtual",
            "yield", "try"
        )
        val types = listOf(
            "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64",
            "u128", "usize", "f32", "f64", "bool", "char", "str", "String",
            "Vec", "Option", "Result", "Box", "Rc", "Arc", "Cell", "RefCell",
            "HashMap", "HashSet", "BTreeMap", "BTreeSet", "Cow"
        )
        return genericTokenizer(text, keywords, types, emptyList())
    }

    private fun tokenizeSwift(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "class", "deinit", "enum", "extension", "func", "import", "init",
            "inout", "internal", "let", "operator", "private", "protocol",
            "public", "static", "struct", "subscript", "typealias", "var",
            "break", "case", "continue", "default", "defer", "do", "else",
            "fallthrough", "for", "guard", "if", "in", "repeat", "return",
            "switch", "where", "while", "as", "catch", "dynamicType", "false",
            "is", "nil", "rethrows", "super", "self", "Self", "throw", "throws",
            "true", "try", "associatedtype", "convenience", "dynamic", "didSet",
            "final", "get", "infix", "indirect", "lazy", "left", "mutating",
            "none", "nonmutating", "optional", "override", "postfix", "precedence",
            "prefix", "Protocol", "required", "right", "set", "Type", "unowned",
            "weak", "willSet", "open", "fileprivate", "async", "await", "actor"
        )
        return genericTokenizer(text, keywords, swiftTypes, emptyList())
    }

    private fun tokenizeDart(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "abstract", "as", "assert", "async", "await", "break", "case", "catch",
            "class", "const", "continue", "covariant", "default", "deferred", "do",
            "dynamic", "else", "enum", "export", "extends", "extension", "external",
            "factory", "false", "final", "finally", "for", "Function", "get", "hide",
            "if", "implements", "import", "in", "interface", "is", "late", "library",
            "mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
            "return", "set", "show", "static", "super", "switch", "sync", "this",
            "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield"
        )
        return genericTokenizer(text, keywords, dartTypes, emptyList())
    }

    private fun tokenizeRuby(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "BEGIN", "END", "alias", "and", "begin", "break", "case", "class",
            "def", "defined?", "do", "else", "elsif", "end", "ensure", "false",
            "for", "if", "in", "module", "next", "nil", "not", "or", "redo",
            "rescue", "retry", "return", "self", "super", "then", "true", "undef",
            "unless", "until", "when", "while", "yield", "__FILE__", "__LINE__",
            "__ENCODING__"
        )
        return genericTokenizer(text, keywords, emptyList(), emptyList())
    }

    private fun tokenizePhp(text: String): List<SyntaxToken> {
        val keywords = listOf(
            "abstract", "and", "array", "as", "break", "callable", "case", "catch",
            "class", "clone", "const", "continue", "declare", "default", "die",
            "do", "echo", "else", "elseif", "empty", "enddeclare", "endfor",
            "endforeach", "endif", "endswitch", "endwhile", "eval", "exit",
            "extends", "final", "finally", "fn", "for", "foreach", "function",
            "global", "goto", "if", "implements", "include", "include_once",
            "instanceof", "insteadof", "interface", "isset", "list", "match",
            "namespace", "new", "or", "print", "private", "protected", "public",
            "readonly", "require", "require_once", "return", "static", "switch",
            "throw", "trait", "try", "unset", "use", "var", "while", "xor", "yield",
            "true", "false", "null", "int", "float", "string", "bool", "array",
            "object", "void", "never", "mixed", "self", "parent"
        )
        return genericTokenizer(text, keywords, emptyList(), emptyList())
    }

    private fun tokenizePlainText(text: String): List<SyntaxToken> {
        return emptyList()
    }

    private fun genericTokenizer(
        text: String,
        keywords: List<String>,
        types: List<String>,
        annotations: List<String>
    ): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < text.length) {
            when {
                text.startsWith("//", i) -> {
                    val end = text.indexOf('\n', i)
                    val commentEnd = if (end >= 0) end else text.length
                    tokens.add(SyntaxToken(i, commentEnd, VSCodeApp.SyntaxTokenType.COMMENT))
                    i = commentEnd
                }
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2)
                    val commentEnd = if (end >= 0) end + 2 else text.length
                    tokens.add(SyntaxToken(i, commentEnd, VSCodeApp.SyntaxTokenType.COMMENT))
                    i = commentEnd
                }
                text.startsWith("'''", i) || text.startsWith("\"\"\"", i) -> {
                    val delimiter = text.substring(i, i + 3)
                    val start = i
                    i += 3
                    while (i < text.length && !text.startsWith(delimiter, i)) {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < text.length) i += 3
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.STRING))
                }
                text[i] == '"' || text[i] == '\'' || text[i] == '`' -> {
                    val start = i
                    val quote = text[i]
                    i++
                    while (i < text.length && text[i] != quote) {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < text.length) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.STRING))
                }
                text[i] == '@' && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] == '\n') -> {
                    val start = i
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '.')) i++
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.KEYWORD))
                }
                text[i].isDigit() || (text[i] == '.' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val start = i
                    if (text.startsWith("0x", i, ignoreCase = true) || text.startsWith("0b", i, ignoreCase = true)) {
                        i += 2
                        while (i < text.length && (text[i].isDigit() || text[i] in 'a'..'f' || text[i] in 'A'..'F' || text[i] == '_')) i++
                    } else {
                        if (text[i] == '.') i++
                        while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == 'e' || text[i] == 'E' || text[i] == 'f' || text[i] == 'F' || text[i] == 'L' || text[i] == 'l' || text[i] == '_' || text[i] == 'x' || text[i] == 'X')) {
                            if ((text[i] == 'e' || text[i] == 'E') && i + 1 < text.length && (text[i + 1] == '+' || text[i + 1] == '-')) i++
                            i++
                        }
                    }
                    tokens.add(SyntaxToken(start, i, VSCodeApp.SyntaxTokenType.NUMBER))
                }
                text[i].isLetter() || text[i] == '_' || text[i] == '$' -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) i++
                    val word = text.substring(start, i)
                    val type = when {
                        word in keywords -> VSCodeApp.SyntaxTokenType.KEYWORD
                        word in types -> VSCodeApp.SyntaxTokenType.TYPE
                        word == "true" || word == "false" || word == "null" || word == "nil" || word == "None" || word == "True" || word == "False" -> VSCodeApp.SyntaxTokenType.CONSTANT
                        word.startsWith("@") -> VSCodeApp.SyntaxTokenType.KEYWORD
                        else -> VSCodeApp.SyntaxTokenType.PLAIN
                    }
                    tokens.add(SyntaxToken(start, i, type))
                }
                text[i] == '{' || text[i] == '}' || text[i] == '(' || text[i] == ')' || text[i] == '[' || text[i] == ']' -> {
                    tokens.add(SyntaxToken(i, i + 1, VSCodeApp.SyntaxTokenType.OPERATOR))
                    i++
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun findPrevNonSpace(text: String, pos: Int): Char? {
        for (i in pos - 1 downTo 0) {
            if (!text[i].isWhitespace()) return text[i]
        }
        return null
    }

    val kotlinKeywords = listOf(
        "package", "import", "class", "interface", "object", "enum", "fun",
        "val", "var", "const", "typealias", "as", "is", "in", "!in", "!is",
        "if", "else", "when", "for", "while", "do", "break", "continue",
        "return", "try", "catch", "finally", "throw", "this", "super",
        "constructor", "init", "companion", "private", "protected", "internal",
        "public", "abstract", "final", "open", "override", "sealed", "data",
        "inner", "inline", "noinline", "crossinline", "reified", "suspend",
        "tailrec", "operator", "infix", "external", "annotation", "field",
        "property", "param", "setparam", "get", "set", "delegate", "receiver",
        "dynamic", "expect", "actual", "lateinit", "by", "where", "typeof",
        "true", "false", "null"
    )

    val kotlinTypes = listOf(
        "String", "Int", "Long", "Float", "Double", "Boolean", "Byte", "Short",
        "Char", "Unit", "Nothing", "Any", "Array", "List", "MutableList", "Set",
        "MutableSet", "Map", "MutableMap", "Pair", "Triple", "Sequence", "Range",
        "IntRange", "LongRange", "CharRange", "UByte", "UShort", "UInt", "ULong",
        "Comparable", "Iterable", "Iterator", "Collection", "MutableCollection"
    )

    val kotlinAnnotations = listOf(
        "@JvmStatic", "@JvmField", "@JvmOverloads", "@JvmName", "@JvmMultifileClass",
        "@JvmSynthetic", "@Synchronized", "@Strictfp", "@Volatile", "@Transient",
        "@Throws", "@Deprecated", "@ReplaceWith", "@Suppress", "@OptIn", "@PublishedApi",
        "@RestrictSuspension", "@DslMarker", "@RequiresOptIn", "@ExperimentalUnsignedTypes",
        "@ExperimentalStdlibApi", "@ExperimentalContracts", "@UseExperimental"
    )

    val javaKeywords = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "true", "false", "null",
        "var", "record", "sealed", "permits", "yield"
    )

    val javaTypes = listOf(
        "String", "Object", "Integer", "Long", "Float", "Double", "Boolean",
        "Byte", "Short", "Character", "Void", "Class", "Enum", "Number",
        "Math", "System", "Runtime", "Thread", "Exception", "Error", "Throwable",
        "List", "ArrayList", "LinkedList", "Set", "HashSet", "TreeSet", "Map",
        "HashMap", "TreeMap", "Collection", "Collections", "Arrays", "Optional",
        "Stream", "Supplier", "Consumer", "Function", "Predicate", "Runnable",
        "Callable", "Comparable", "Comparator", "Iterator", "Iterable"
    )

    val javaAnnotations = listOf(
        "@Override", "@Deprecated", "@SuppressWarnings", "@SafeVarargs",
        "@FunctionalInterface", "@Retention", "@Target", "@Documented", "@Inherited",
        "@Native", "@Repeatable"
    )

    val pythonKeywords = listOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
        "with", "yield", "match", "case"
    )

    val pythonTypes = listOf(
        "int", "float", "complex", "str", "list", "tuple", "range", "dict",
        "set", "frozenset", "bool", "bytes", "bytearray", "memoryview",
        "NoneType", "type", "object", "Exception", "ValueError", "TypeError",
        "KeyError", "IndexError", "AttributeError", "ImportError", "RuntimeError",
        "NotImplementedError", "StopIteration", "GeneratorExit", "KeyboardInterrupt"
    )

    val pythonDecorators = listOf(
        "@staticmethod", "@classmethod", "@property", "@abstractmethod",
        "@cached_property", "@dataclass", "@overload", "@contextmanager",
        "@asynccontextmanager"
    )

    val cppKeywords = listOf(
        "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
        "bool", "break", "case", "catch", "char", "char16_t", "char32_t", "class",
        "compl", "concept", "const", "constexpr", "const_cast", "continue",
        "decltype", "default", "delete", "do", "double", "dynamic_cast", "else",
        "enum", "explicit", "export", "extern", "false", "float", "for", "friend",
        "goto", "if", "inline", "int", "long", "mutable", "namespace", "new",
        "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq",
        "private", "protected", "public", "register", "reinterpret_cast", "return",
        "short", "signed", "sizeof", "static", "static_cast", "struct", "switch",
        "template", "this", "thread_local", "throw", "true", "try", "typedef",
        "typeid", "typename", "union", "unsigned", "using", "virtual", "void",
        "volatile", "wchar_t", "while", "xor", "xor_eq", "override", "final",
        "constinit", "consteval", "requires"
    )

    val cppTypes = listOf(
        "string", "wstring", "vector", "map", "unordered_map", "set", "unordered_set",
        "list", "deque", "queue", "stack", "priority_queue", "array", "pair", "tuple",
        "shared_ptr", "unique_ptr", "weak_ptr", "make_shared", "make_unique",
        "function", "optional", "variant", "any", "string_view", "span",
        "size_t", "ptrdiff_t", "int8_t", "int16_t", "int32_t", "int64_t",
        "uint8_t", "uint16_t", "uint32_t", "uint64_t", "FILE", "fstream",
        "ifstream", "ofstream", "stringstream", "iostream", "ostream", "istream",
        "cout", "cin", "cerr", "endl", "std", "nullptr", "NULL"
    )

    val jsKeywords = listOf(
        "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "finally",
        "for", "function", "if", "import", "in", "instanceof", "let", "new",
        "return", "super", "switch", "this", "throw", "try", "typeof", "var",
        "void", "while", "with", "yield", "async", "await", "of", "from",
        "true", "false", "null", "undefined", "NaN", "Infinity", "static",
        "get", "set", "enum", "implements", "interface", "package", "private",
        "protected", "public"
    )

    val jsTypes = listOf(
        "String", "Number", "Boolean", "Array", "Object", "Function", "Symbol",
        "Map", "Set", "WeakMap", "WeakSet", "Promise", "RegExp", "Date", "Error",
        "Math", "JSON", "console", "ArrayBuffer", "DataView", "Int8Array",
        "Uint8Array", "Int16Array", "Uint16Array", "Int32Array", "Uint32Array",
        "Float32Array", "Float64Array", "BigInt", "Proxy", "Reflect", "Generator",
        "AsyncFunction", "Iterator", "AsyncIterator"
    )

    val tsKeywords = listOf(
        "type", "interface", "enum", "declare", "namespace", "module", "abstract",
        "implements", "readonly", "keyof", "infer", "extends", "as", "is",
        "any", "unknown", "never", "void", "string", "number", "boolean",
        "bigint", "symbol", "object", "undefined", "null", "true", "false"
    )

    val tsTypes = listOf(
        "Partial", "Required", "Readonly", "Record", "Pick", "Omit", "Exclude",
        "Extract", "NonNullable", "Parameters", "ReturnType", "InstanceType",
        "ConstructorParameters", "ThisParameterType", "OmitThisParameter",
        "ThisType", "Awaited", "Promise", "PromiseLike", "Iterable", "Iterator",
        "Generator", "AsyncIterable", "AsyncIterator", "AsyncGenerator"
    )

    val tsDecorators = emptyList<String>()

    val shellKeywords = listOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
        "until", "do", "done", "in", "function", "select", "time", "coproc",
        "declare", "typeset", "local", "readonly", "export", "alias", "unalias",
        "source", "exit", "return", "break", "continue", "eval", "exec", "shift",
        "set", "unset", "trap", "test", "echo", "printf", "cd", "pwd", "ls",
        "true", "false"
    )

    val swiftTypes = listOf(
        "Int", "UInt", "Float", "Double", "Bool", "String", "Character",
        "Array", "Dictionary", "Set", "Optional", "Result", "Data", "Date",
        "URL", "Error", "NSObject", "Any", "AnyObject", "Self", "Void",
        "Never", "Decimal", "CGFloat", "NSNumber", "NSPredicate", "NSRange",
        "NSAttributedString", "NSMutableAttributedString", "UIView", "UIViewController",
        "SwiftUI", "View", "ObservableObject", "StateObject", "Published"
    )

    val dartTypes = listOf(
        "String", "int", "double", "bool", "num", "List", "Set", "Map",
        "Iterable", "Stream", "Future", "Object", "dynamic", "void", "Never",
        "Widget", "BuildContext", "StatelessWidget", "StatefulWidget", "State",
        "Text", "Container", "Column", "Row", "Stack", "Scaffold", "AppBar",
        "MaterialApp", "FloatingActionButton", "Icon", "Image", "InkWell",
        "GestureDetector", "TextEditingController", "FocusNode", "Navigator",
        "Theme", "MediaQuery", "BuildOwner", "Key", "GlobalKey", "ValueKey"
    )
}