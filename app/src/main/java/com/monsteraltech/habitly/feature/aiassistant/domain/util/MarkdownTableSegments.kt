package com.monsteraltech.habitly.feature.aiassistant.domain.util

/**
 * Splits an answer's markdown into text and table segments, so the UI can render each table on its
 * own — at its natural width, with horizontal scroll — instead of squeezing it into the bubble's
 * width and breaking words apart.
 *
 * A table is a run of consecutive lines starting with `|` whose second line is the GFM header
 * separator (`|---|---|`). A run of `|` lines without that separator stays plain text, so neither
 * a half-streamed table nor a stray pipe character is treated as one.
 */
object MarkdownTableSegments {

    sealed interface Segment {
        val content: String

        data class Text(override val content: String) : Segment
        data class Table(override val content: String, val columnCount: Int) : Segment
    }

    /** A row made only of `|`, `-`, `:` and spaces — the header underline. */
    private val SEPARATOR_ROW = Regex("""^\|[\s:\-|]+\|?$""")

    fun split(markdown: String): List<Segment> {
        val lines = markdown.lines()
        val segments = mutableListOf<Segment>()
        val textBuffer = mutableListOf<String>()

        fun flushText() {
            val text = textBuffer.joinToString("\n").trim('\n')
            if (text.isNotBlank()) segments += Segment.Text(text)
            textBuffer.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val startsTable = isTableRow(line) && isSeparatorRow(lines.getOrNull(i + 1))
            if (startsTable) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && isTableRow(lines[i])) {
                    tableLines += lines[i]
                    i++
                }
                flushText()
                segments += Segment.Table(
                    content = tableLines.joinToString("\n"),
                    columnCount = countColumns(tableLines.first())
                )
            } else {
                textBuffer += line
                i++
            }
        }
        flushText()

        return segments
    }

    private fun isTableRow(line: String): Boolean = line.trimStart().startsWith("|")

    private fun isSeparatorRow(line: String?): Boolean {
        val trimmed = line?.trim() ?: return false
        return trimmed.contains('-') && SEPARATOR_ROW.matches(trimmed)
    }

    private fun countColumns(headerRow: String): Int = headerRow
        .trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .size
}
