package com.vscode.android.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt

class LineNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lineCount: Int = 1
    private var firstVisibleLine: Int = 0
    private var lastVisibleLine: Int = 0
    private var currentLine: Int = 1
    private var lineHeight: Float = 0f
    private var scrollY: Int = 0
    private var editorScrollY: Int = 0

    @ColorInt
    private var lineNumberColor: Int = 0xFF858585.toInt()

    @ColorInt
    private var lineNumberActiveColor: Int = 0xFFC6C6C6.toInt()

    @ColorInt
    private var gutterBackgroundColor: Int = 0xFF1E1E1E.toInt()

    @ColorInt
    private var currentLineHighlightColor: Int = 0xFF2A2D2E.toInt()

    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val textBounds = Rect()
    private var gutterWidth: Float = 0f

    private var foldIndicators: MutableMap<Int, Boolean> = mutableMapOf()
    private var isFoldIndicatorVisible = false

    fun setGutterWidthDp(widthDp: Int) {
        gutterWidth = dpToPx(widthDp).toFloat()
        requestLayout()
    }

    fun setLineNumberColor(@ColorInt color: Int) {
        lineNumberColor = color
        invalidate()
    }

    fun setLineNumberActiveColor(@ColorInt color: Int) {
        lineNumberActiveColor = color
        invalidate()
    }

    fun setGutterBackgroundColor(@ColorInt color: Int) {
        gutterBackgroundColor = color
        backgroundPaint.color = color
        invalidate()
    }

    fun setCurrentLineHighlightColor(@ColorInt color: Int) {
        currentLineHighlightColor = color
        highlightPaint.color = color
        invalidate()
    }

    fun setTextSizePx(textSize: Float) {
        lineNumberPaint.textSize = textSize
        lineHeight = textSize * 1.4f
        requestLayout()
        invalidate()
    }

    fun setLineCount(count: Int) {
        lineCount = count.coerceAtLeast(1)
        updateFirstAndLastVisibleLines()
        invalidate()
    }

    fun setCurrentLine(line: Int) {
        currentLine = line.coerceAtLeast(1)
        invalidate()
    }

    fun setFoldIndicators(indicators: Map<Int, Boolean>) {
        foldIndicators.clear()
        foldIndicators.putAll(indicators)
        invalidate()
    }

    fun setFoldIndicatorVisible(visible: Boolean) {
        isFoldIndicatorVisible = visible
        invalidate()
    }

    fun syncWithEditorScroll(editorScrollY: Int) {
        this.editorScrollY = editorScrollY
        this.scrollY = editorScrollY
        updateFirstAndVisibleLines()
        invalidate()
    }

    private fun updateFirstAndVisibleLines() {
        if (lineHeight <= 0f) {
            firstVisibleLine = 0
            lastVisibleLine = lineCount
            return
        }
        firstVisibleLine = (scrollY / lineHeight).toInt().coerceIn(0, lineCount - 1)
        lastVisibleLine = ((scrollY + height) / lineHeight).toInt().coerceAtMost(lineCount - 1)
    }

    private fun updateFirstAndLastVisibleLines() {
        updateFirstAndVisibleLines()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (gutterWidth > 0f) {
            gutterWidth.toInt()
        } else {
            MeasureSpec.getSize(widthMeasureSpec)
        }
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        backgroundPaint.color = gutterBackgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (lineHeight <= 0f || lineCount <= 0) return

        val startLine = (scrollY / lineHeight).toInt().coerceIn(0, lineCount - 1)
        val endLine = ((scrollY + height) / lineHeight).toInt().coerceAtMost(lineCount - 1)

        for (line in startLine..endLine) {
            val y = (line + 1) * lineHeight - scrollY

            if (line + 1 == currentLine) {
                highlightPaint.color = currentLineHighlightColor
                canvas.drawRect(0f, y - lineHeight + 2f, width.toFloat(), y + 2f, highlightPaint)
            }

            if (isFoldIndicatorVisible && foldIndicators.containsKey(line + 1)) {
                val foldY = y - lineHeight / 2
                lineNumberPaint.color = lineNumberColor
                val foldSymbol = if (foldIndicators[line + 1] == true) "\u25BC" else "\u25B6"
                lineNumberPaint.getTextBounds(foldSymbol, 0, foldSymbol.length, textBounds)
                canvas.drawText(
                    foldSymbol,
                    dpToPx(16).toFloat(),
                    foldY + textBounds.height() / 2f,
                    lineNumberPaint
                )
            }

            val lineNumber = (line + 1).toString()
            lineNumberPaint.color = if (line + 1 == currentLine) lineNumberActiveColor else lineNumberColor
            lineNumberPaint.getTextBounds(lineNumber, 0, lineNumber.length, textBounds)

            val textX = width - dpToPx(8).toFloat()
            val textY = y - textBounds.height() / 2f - lineHeight / 4f

            canvas.drawText(lineNumber, textX, textY, lineNumberPaint)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}