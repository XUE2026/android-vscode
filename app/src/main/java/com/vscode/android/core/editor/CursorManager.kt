package com.vscode.android.core.editor

class CursorManager {

    private val cursors = mutableListOf<CursorState>()
    private var primaryCursorIndex = 0
    private var selectionStart = -1
    private var selectionEnd = -1

    fun addCursor(position: Int, line: Int = 0, column: Int = 0) {
        val adjustedLine = if (line > 0) line else position
        val adjustedColumn = if (column > 0) column else position
        val cursor = CursorState(
            position = position,
            line = adjustedLine,
            column = adjustedColumn,
            anchorPosition = position,
            anchorLine = adjustedLine,
            anchorColumn = adjustedColumn
        )
        cursors.add(cursor)
        if (cursors.size == 1) {
            primaryCursorIndex = 0
        }
    }

    fun removeCursor(index: Int) {
        if (index in cursors.indices) {
            cursors.removeAt(index)
            if (primaryCursorIndex >= cursors.size) {
                primaryCursorIndex = (cursors.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun removeAllCursors() {
        cursors.clear()
        primaryCursorIndex = 0
        addCursor(0, 0, 0)
    }

    fun getPrimaryCursor(): CursorState? {
        return if (primaryCursorIndex in cursors.indices) cursors[primaryCursorIndex] else null
    }

    fun getCursor(index: Int): CursorState? {
        return if (index in cursors.indices) cursors[index] else null
    }

    fun getAllCursors(): List<CursorState> = cursors.toList()

    fun getCursorCount(): Int = cursors.size

    fun setPrimaryCursor(index: Int) {
        if (index in cursors.indices) {
            primaryCursorIndex = index
        }
    }

    fun setCursorPosition(index: Int, position: Int, line: Int = -1, column: Int = -1) {
        val cursor = getCursor(index) ?: return
        cursor.position = position
        if (line >= 0) cursor.line = line
        if (column >= 0) cursor.column = column
    }

    fun moveCursorToLine(index: Int, line: Int, text: String) {
        val cursor = getCursor(index) ?: return
        val lines = text.lines()
        if (line in 0 until lines.size) {
            cursor.line = line
            val column = cursor.column.coerceAtMost(lines[line].length)
            cursor.column = column
            cursor.position = calculatePosition(lines, line, column)
        }
    }

    fun moveCursorLeft(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        if (cursor.position > 0) {
            cursor.position--
            updateLineColumnFromPosition(cursor, text)
        }
    }

    fun moveCursorRight(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        if (cursor.position < text.length) {
            cursor.position++
            updateLineColumnFromPosition(cursor, text)
        }
    }

    fun moveCursorUp(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        val lines = text.lines()
        if (cursor.line > 0) {
            cursor.line--
            cursor.column = cursor.column.coerceAtMost(lines[cursor.line].length)
            cursor.position = calculatePosition(lines, cursor.line, cursor.column)
        }
    }

    fun moveCursorDown(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        val lines = text.lines()
        if (cursor.line < lines.size - 1) {
            cursor.line++
            cursor.column = cursor.column.coerceAtMost(lines[cursor.line].length)
            cursor.position = calculatePosition(lines, cursor.line, cursor.column)
        }
    }

    fun moveCursorToLineStart(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        val lines = text.lines()
        if (cursor.line in lines.indices) {
            cursor.column = 0
            cursor.position = calculatePosition(lines, cursor.line, 0)
        }
    }

    fun moveCursorToLineEnd(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        val lines = text.lines()
        if (cursor.line in lines.indices) {
            cursor.column = lines[cursor.line].length
            cursor.position = calculatePosition(lines, cursor.line, cursor.column)
        }
    }

    fun moveCursorToDocumentStart(index: Int) {
        val cursor = getCursor(index) ?: return
        cursor.position = 0
        cursor.line = 0
        cursor.column = 0
    }

    fun moveCursorToDocumentEnd(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        cursor.position = text.length
        val lines = text.lines()
        cursor.line = (lines.size - 1).coerceAtLeast(0)
        cursor.column = lines.lastOrNull()?.length ?: 0
    }

    fun moveCursorToNextWord(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        var pos = cursor.position
        while (pos < text.length && text[pos].isLetterOrDigit()) pos++
        while (pos < text.length && !text[pos].isLetterOrDigit()) pos++
        cursor.position = pos.coerceAtMost(text.length)
        updateLineColumnFromPosition(cursor, text)
    }

    fun moveCursorToPrevWord(index: Int, text: String) {
        val cursor = getCursor(index) ?: return
        var pos = cursor.position
        if (pos > 0) pos--
        while (pos > 0 && !text[pos].isLetterOrDigit()) pos--
        while (pos > 0 && text[pos - 1].isLetterOrDigit()) pos--
        cursor.position = pos.coerceAtLeast(0)
        updateLineColumnFromPosition(cursor, text)
    }

    fun setSelection(start: Int, end: Int) {
        selectionStart = start
        selectionEnd = end
        val primary = getPrimaryCursor()
        if (primary != null) {
            primary.anchorPosition = start
            primary.position = end
        }
    }

    fun clearSelection() {
        selectionStart = -1
        selectionEnd = -1
    }

    fun hasSelection(): Boolean {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd
    }

    fun getSelection(): Pair<Int, Int>? {
        return if (hasSelection()) {
            val start = selectionStart.coerceAtMost(selectionEnd)
            val end = selectionStart.coerceAtLeast(selectionEnd)
            Pair(start, end)
        } else null
    }

    fun getSelectionText(text: String): String? {
        val (start, end) = getSelection() ?: return null
        return if (start in 0..end && end <= text.length) text.substring(start, end) else null
    }

    fun selectAll(text: String) {
        setSelection(0, text.length)
    }

    fun selectWord(text: String, position: Int) {
        var start = position
        var end = position
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
        setSelection(start, end)
    }

    fun selectLine(text: String, line: Int) {
        val lines = text.lines()
        if (line in lines.indices) {
            val start = calculatePosition(lines, line, 0)
            val end = start + lines[line].length
            setSelection(start, end)
        }
    }

    fun findMatchingBracket(text: String, position: Int): Int? {
        if (position < 0 || position >= text.length) return null

        val ch = text[position]
        val openBrackets = "({["
        val closeBrackets = ")}]"

        val isOpen = openBrackets.indexOf(ch)
        val isClose = closeBrackets.indexOf(ch)

        return when {
            isOpen >= 0 -> findClosingBracket(text, position, ch, closeBrackets[isOpen])
            isClose >= 0 -> findOpeningBracket(text, position, ch, openBrackets[isClose])
            else -> {
                if (position > 0) {
                    val prevCh = text[position - 1]
                    val prevClose = closeBrackets.indexOf(prevCh)
                    if (prevClose >= 0) {
                        findOpeningBracket(text, position - 1, prevCh, openBrackets[prevClose])
                    } else null
                } else null
            }
        }
    }

    private fun findClosingBracket(text: String, start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var inString = false
        var stringChar = '"'
        var inSingleLineComment = false
        var inMultiLineComment = false

        for (i in start until text.length) {
            val ch = text[i]

            if (inSingleLineComment) {
                if (ch == '\n') inSingleLineComment = false
                continue
            }
            if (inMultiLineComment) {
                if (ch == '*' && i + 1 < text.length && text[i + 1] == '/') {
                    inMultiLineComment = false
                }
                continue
            }
            if (inString) {
                if (ch == '\\' && i + 1 < text.length) continue
                if (ch == stringChar) inString = false
                continue
            }

            if (ch == '/' && i + 1 < text.length) {
                if (text[i + 1] == '/') inSingleLineComment = true
                if (text[i + 1] == '*') inMultiLineComment = true
            }
            if (ch == '"' || ch == '\'' || ch == '`') {
                inString = true
                stringChar = ch
                continue
            }

            if (ch == open) depth++
            if (ch == close) {
                if (depth == 0) return i
                depth--
            }
        }
        return null
    }

    private fun findOpeningBracket(text: String, start: Int, close: Char, open: Char): Int? {
        var depth = 0
        var inString = false
        var stringChar = '"'
        var inSingleLineComment = false
        var inMultiLineComment = false

        for (i in start downTo 0) {
            val ch = text[i]

            if (inSingleLineComment) {
                if (ch == '\n') inSingleLineComment = false
                continue
            }
            if (inMultiLineComment) {
                if (ch == '*' && i > 0 && text[i - 1] == '/') {
                    inMultiLineComment = false
                }
                continue
            }
            if (inString) {
                if (ch == '\\' && i > 0) continue
                if (ch == stringChar) inString = false
                continue
            }

            if (ch == '/' && i > 0) {
                if (text[i - 1] == '*') inMultiLineComment = true
            }
            if (ch == '"' || ch == '\'' || ch == '`') {
                inString = true
                stringChar = ch
                continue
            }

            if (ch == close) depth++
            if (ch == open) {
                if (depth == 0) return i
                depth--
            }
        }
        return null
    }

    fun addCursorAtMatchingBracket(text: String) {
        val primary = getPrimaryCursor() ?: return
        val matchPos = findMatchingBracket(text, primary.position)
        if (matchPos != null) {
            addCursor(matchPos)
        }
    }

    fun addCursorAtNextMatch(text: String, searchText: String) {
        val primary = getPrimaryCursor() ?: return
        val nextPos = text.indexOf(searchText, primary.position + 1)
        if (nextPos >= 0) {
            addCursor(nextPos + searchText.length)
        }
    }

    fun addCursorAtAllMatches(text: String, searchText: String) {
        if (searchText.isEmpty()) return
        var pos = text.indexOf(searchText)
        while (pos >= 0) {
            addCursor(pos + searchText.length)
            pos = text.indexOf(searchText, pos + 1)
        }
    }

    private fun updateLineColumnFromPosition(cursor: CursorState, text: String) {
        val lines = text.substring(0, cursor.position.coerceAtMost(text.length)).lines()
        cursor.line = (lines.size - 1).coerceAtLeast(0)
        cursor.column = lines.lastOrNull()?.length ?: 0
    }

    private fun calculatePosition(lines: List<String>, line: Int, column: Int): Int {
        var pos = 0
        for (i in 0 until line.coerceAtMost(lines.size)) {
            pos += lines[i].length + 1
        }
        return (pos + column.coerceAtMost(lines.getOrElse(line) { "" }.length)).coerceAtMost(
            lines.sumOf { it.length + 1 }
        )
    }

    fun getCursorLine(index: Int): Int = getCursor(index)?.line ?: 0
    fun getCursorColumn(index: Int): Int = getCursor(index)?.column ?: 0
    fun getCursorPosition(index: Int): Int = getCursor(index)?.position ?: 0
}

data class CursorState(
    var position: Int = 0,
    var line: Int = 0,
    var column: Int = 0,
    var anchorPosition: Int = 0,
    var anchorLine: Int = 0,
    var anchorColumn: Int = 0
)