package com.monsteraltech.habitly.feature.aiassistant.domain.util

/**
 * Trocea el markdown de una respuesta en segmentos de texto y de tabla, para que la UI
 * pueda renderizar cada tabla aparte (a su ancho natural, con scroll horizontal) en vez
 * de estrujarla al ancho de la burbuja partiendo palabras.
 *
 * Se considera tabla un grupo de líneas consecutivas que empiezan por `|` cuya segunda
 * línea es el separador de cabecera GFM (`|---|---|`). Un grupo de líneas con `|` sin
 * ese separador se deja como texto normal: así ni el streaming (tabla a medio llegar)
 * ni un uso suelto del carácter se tratan como tabla.
 */
object MarkdownTableSegments {

    sealed interface Segment {
        val content: String

        data class Text(override val content: String) : Segment
        data class Table(override val content: String, val columnCount: Int) : Segment
    }

    /** Fila hecha solo de `|`, `-`, `:` y espacios (el subrayado de la cabecera). */
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
