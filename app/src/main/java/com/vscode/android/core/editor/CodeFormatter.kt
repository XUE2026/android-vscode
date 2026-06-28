package com.vscode.android.core.editor

class CodeFormatter {

    fun format(text: String, languageId: String, indentSize: Int = 4, useTabs: Boolean = false): String {
        return when (languageId) {
            "kotlin", "java", "cpp", "c", "javascript", "typescript", "groovy",
            "dart", "swift", "go", "rust" -> formatCurlBraceLanguage(text, indentSize, useTabs)
            "python" -> formatPython(text, indentSize, useTabs)
            "html", "xml" -> formatXmlLike(text, indentSize, useTabs)
            "json" -> formatJson(text, indentSize, useTabs)
            "css" -> formatCss(text, indentSize, useTabs)
            "sql" -> formatSql(text, indentSize, useTabs)
            "yaml" -> formatYaml(text, indentSize, useTabs)
            "ruby" -> formatRuby(text, indentSize, useTabs)
            "php" -> formatPhp(text, indentSize, useTabs)
            else -> text
        }
    }

    fun getIndentString(indentSize: Int, useTabs: Boolean): String {
        return if (useTabs) "\t" else " ".repeat(indentSize)
    }

    fun autoIndentLine(
        currentLine: String,
        previousLine: String,
        languageId: String,
        indentSize: Int,
        useTabs: Boolean
    ): String {
        val indent = getIndentString(indentSize, useTabs)
        val prevIndent = getLineIndent(previousLine)

        val prevTrimmed = previousLine.trimStart()
        val currentTrimmed = currentLine.trimStart()

        val increaseIndent = when (languageId) {
            "python" -> prevTrimmed.endsWith(":") &&
                    !prevTrimmed.startsWith("else") &&
                    !prevTrimmed.startsWith("elif") &&
                    !prevTrimmed.startsWith("except") &&
                    !prevTrimmed.startsWith("finally")
            else -> prevTrimmed.endsWith("{") ||
                    prevTrimmed.endsWith("(") ||
                    prevTrimmed.endsWith("[")
        }

        val decreaseIndent = when (languageId) {
            "python" -> currentTrimmed.startsWith("else") ||
                    currentTrimmed.startsWith("elif") ||
                    currentTrimmed.startsWith("except") ||
                    currentTrimmed.startsWith("finally") ||
                    currentTrimmed == "pass"
            else -> currentTrimmed.startsWith("}") ||
                    currentTrimmed.startsWith("]") ||
                    currentTrimmed.startsWith(")") ||
                    currentTrimmed == "};"
        }

        return when {
            decreaseIndent -> {
                val reduced = removeIndentLevel(prevIndent, indent)
                if (reduced.length < prevIndent.length) reduced + currentTrimmed
                else prevIndent + currentTrimmed
            }
            increaseIndent -> prevIndent + indent + currentTrimmed
            else -> prevIndent + currentTrimmed
        }
    }

    fun getIndentLevel(line: String, indentSize: Int, useTabs: Boolean): Int {
        val indent = getIndentString(indentSize, useTabs)
        val leading = getLineIndent(line)
        val indentStr = if (useTabs) "\t" else " ".repeat(indentSize)
        return leading.length / indentStr.length
    }

    private fun formatCurlBraceLanguage(text: String, indentSize: Int, useTabs: Boolean): String {
        val indent = getIndentString(indentSize, useTabs)
        val lines = text.lines()
        val result = StringBuilder()
        var currentIndent = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            if (trimmed.startsWith("}") || trimmed.startsWith("]") || trimmed.startsWith(")")) {
                currentIndent = (currentIndent - 1).coerceAtLeast(0)
            }

            result.append(indent.repeat(currentIndent))
            result.appendLine(trimmed)

            val openCount = trimmed.count { it == '{' || it == '(' || it == '[' }
            val closeCount = trimmed.count { it == '}' || it == ')' || it == ']' }

            currentIndent += (openCount - closeCount)
            currentIndent = currentIndent.coerceAtLeast(0)
        }

        return result.toString().trimEnd()
    }

    private fun formatPython(text: String, indentSize: Int, useTabs: Boolean): String {
        val indent = getIndentString(indentSize, useTabs)
        val lines = text.lines()
        val result = StringBuilder()
        var currentIndent = 0
        val indentStack = mutableListOf<Int>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            val isDecreaseIndent = trimmed.startsWith("else") ||
                    trimmed.startsWith("elif") ||
                    trimmed.startsWith("except") ||
                    trimmed.startsWith("finally") ||
                    trimmed.startsWith("elif ") ||
                    trimmed.startsWith("except ") ||
                    trimmed.startsWith("finally ")

            if (isDecreaseIndent && indentStack.isNotEmpty()) {
                currentIndent = indentStack.removeAt(indentStack.lastIndex)
            }

            result.append(indent.repeat(currentIndent))
            result.appendLine(trimmed)

            if (trimmed.endsWith(":") && !isDecreaseIndent) {
                indentStack.add(currentIndent)
                currentIndent++
            } else if (trimmed.endsWith(":")) {
                indentStack.add(currentIndent)
                currentIndent++
            }
        }

        return result.toString().trimEnd()
    }

    private fun formatXmlLike(text: String, indentSize: Int, useTabs: Boolean): String {
        val indent = getIndentString(indentSize, useTabs)
        val lines = text.lines()
        val result = StringBuilder()
        var currentIndent = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            val isClosing = trimmed.startsWith("</") || trimmed.startsWith("-->") || trimmed == "/>"
            val isSelfClosing = trimmed.endsWith("/>") && !trimmed.startsWith("</")
            val isCommentEnd = trimmed == "-->"
            val isCommentStart = trimmed.startsWith("<!--") && !trimmed.endsWith("-->")

            if (isClosing && !isCommentEnd) {
                currentIndent = (currentIndent - 1).coerceAtLeast(0)
            }

            result.append(indent.repeat(currentIndent))
            result.appendLine(trimmed)

            if (trimmed.startsWith("<") && !isClosing && !isSelfClosing && !trimmed.startsWith("<!--") && !trimmed.startsWith("<!")) {
                currentIndent++
            }
            if (isCommentStart) {
                currentIndent++
            }
            if (isCommentEnd) {
                currentIndent = (currentIndent - 1).coerceAtLeast(0)
            }
        }

        return result.toString().trimEnd()
    }

    private fun formatJson(text: String, indentSize: Int, useTabs: Boolean): String {
        val indent = getIndentString(indentSize, useTabs)
        val result = StringBuilder()
        var currentIndent = 0
        var inString = false
        var stringChar = '"'

        for (i in text.indices) {
            val ch = text[i]

            when {
                ch == '"' && (i == 0 || text[i - 1] != '\\') -> {
                    if (!inString) {
                        stringChar = ch
                        inString = true
                    } else {
                        inString = false
                    }
                    result.append(ch)
                }
                inString -> result.append(ch)
                ch == '{' || ch == '[' -> {
                    result.append(ch)
                    result.append('\n')
                    currentIndent++
                    result.append(indent.repeat(currentIndent))
                }
                ch == '}' || ch == ']' -> {
                    result.append('\n')
                    currentIndent = (currentIndent - 1).coerceAtLeast(0)
                    result.append(indent.repeat(currentIndent))
                    result.append(ch)
                }
                ch == ',' -> {
                    result.append(ch)
                    result.append('\n')
                    result.append(indent.repeat(currentIndent))
                }
                ch == ':' -> {
                    result.append(ch)
                    result.append(' ')
                }
                ch.isWhitespace() -> {
                    if (result.isNotEmpty() && result.last() != '\n' && result.last() != ' ') {
                        result.append(' ')
                    }
                }
                else -> result.append(ch)
            }
        }

        return result.toString().trim()
    }

    private fun formatCss(text: String, indentSize: Int, useTabs: Boolean): String {
        val indent = getIndentString(indentSize, useTabs)
        val lines = text.lines()
        val result = StringBuilder()
        var currentIndent = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            if (trimmed.startsWith("}")) {
                currentIndent = (currentIndent - 1).coerceAtLeast(0)
            }

            result.append(indent.repeat(currentIndent))

            if (trimmed.contains("{") && trimmed.contains("}")) {
                result.appendLine(trimmed)
            } else if (trimmed.contains("{")) {
                result.appendLine(trimmed)
                currentIndent++
            } else if (trimmed.contains("}")) {
                result.appendLine(trimmed)
            } else if (trimmed.endsWith(";")) {
                result.appendLine(trimmed)
            } else {
                result.appendLine(trimmed)
            }
        }

        return result.toString().trimEnd()
    }

    private fun formatSql(text: String, indentSize: Int, useTabs: Boolean): String {
        val indent = getIndentString(indentSize, useTabs)
        val lines = text.lines()
        val result = StringBuilder()
        var currentIndent = 0

        val mainKeywords = listOf(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE",
            "ALTER", "DROP", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL",
            "ON", "AND", "OR", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET",
            "UNION", "VALUES", "SET", "INTO", "CASE", "WHEN", "THEN", "ELSE", "END"
        )

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            val upperLine = trimmed.uppercase()
            val isMainKeyword = mainKeywords.any { upperLine.startsWith(it) && (trimmed.length == it.length || !trimmed[it.length].isLetterOrDigit()) }

            if (isMainKeyword) {
                currentIndent = 1
                result.appendLine(trimmed)
            } else if (trimmed.startsWith(")")) {
                currentIndent = (currentIndent - 1).coerceAtLeast(0)
                result.append(indent.repeat(currentIndent))
                result.appendLine(trimmed)
            } else {
                result.append(indent.repeat(currentIndent))
                result.appendLine(trimmed)
            }
        }

        return result.toString().trimEnd()
    }

    private fun formatYaml(text: String, indentSize: Int, useTabs: Boolean): String {
        return text
    }

    private fun formatRuby(text: String, indentSize: Int, useTabs: Boolean): String {
        val indent = getIndentString(indentSize, useTabs)
        val lines = text.lines()
        val result = StringBuilder()
        var currentIndent = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            if (trimmed == "end" || trimmed.startsWith("end ") || trimmed == "}" || trimmed == "])") {
                currentIndent = (currentIndent - 1).coerceAtLeast(0)
            }

            result.append(indent.repeat(currentIndent))
            result.appendLine(trimmed)

            if (trimmed.endsWith("do") || trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")) {
                currentIndent++
            }
            if (trimmed in listOf("if", "unless", "while", "until", "for", "begin", "case", "class", "module", "def")) {
                currentIndent++
            }
        }

        return result.toString().trimEnd()
    }

    private fun formatPhp(text: String, indentSize: Int, useTabs: Boolean): String {
        return formatCurlBraceLanguage(text, indentSize, useTabs)
    }

    private fun getLineIndent(line: String): String {
        val sb = StringBuilder()
        for (ch in line) {
            if (ch == ' ' || ch == '\t') sb.append(ch)
            else break
        }
        return sb.toString()
    }

    private fun removeIndentLevel(lineIndent: String, indent: String): String {
        if (lineIndent.endsWith(indent)) {
            return lineIndent.substring(0, lineIndent.length - indent.length)
        }
        return lineIndent
    }
}