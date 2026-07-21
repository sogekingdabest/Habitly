package com.monsteraltech.habitly.feature.aiassistant.domain.util

import com.monsteraltech.habitly.feature.aiassistant.domain.util.MarkdownTableSegments.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableSegmentsTest {

    @Test
    fun `plain text produces a single text segment`() {
        val text = "Puedes cocinar una **tortilla** de patatas.\n\n- Huevos\n- Patatas"

        val segments = MarkdownTableSegments.split(text)

        assertEquals(listOf<Segment>(Segment.Text(text)), segments)
    }

    @Test
    fun `table between paragraphs is isolated with its column count`() {
        val segments = MarkdownTableSegments.split(
            """
                Aquí tienes el plan:

                | Día | Área | Tareas | Frecuencia |
                |---|---|---|---|
                | Lunes | Cocina | Fregar | Diario |
                | Martes | Baños | Inodoro | Semanal |

                ¡A por ello!
            """.trimIndent()
        )

        assertEquals(3, segments.size)
        assertEquals(Segment.Text("Aquí tienes el plan:"), segments[0])
        val table = segments[1] as Segment.Table
        assertEquals(4, table.columnCount)
        assertTrue(table.content.startsWith("| Día"))
        assertTrue(table.content.endsWith("| Martes | Baños | Inodoro | Semanal |"))
        assertEquals(Segment.Text("¡A por ello!"), segments[2])
    }

    @Test
    fun `table at the start and end of the message works`() {
        val segments = MarkdownTableSegments.split(
            """
                | A | B |
                |---|---|
                | 1 | 2 |
            """.trimIndent()
        )

        assertEquals(1, segments.size)
        assertEquals(2, (segments[0] as Segment.Table).columnCount)
    }

    @Test
    fun `pipes without separator row stay as text`() {
        // Streaming: la cabecera ha llegado pero el separador aún no.
        val text = "Voy con la tabla:\n| Día | Área |"

        val segments = MarkdownTableSegments.split(text)

        assertEquals(listOf<Segment>(Segment.Text(text)), segments)
    }

    @Test
    fun `separator with alignment colons is recognized`() {
        val segments = MarkdownTableSegments.split(
            """
                | Día | Horas |
                |:---|---:|
                | Lunes | 2 |
            """.trimIndent()
        )

        assertTrue(segments.single() is Segment.Table)
    }

    @Test
    fun `two tables produce two table segments`() {
        val segments = MarkdownTableSegments.split(
            """
                | A |
                |---|
                | 1 |

                Y además:

                | B | C |
                |---|---|
                | 2 | 3 |
            """.trimIndent()
        )

        assertEquals(3, segments.size)
        assertEquals(1, (segments[0] as Segment.Table).columnCount)
        assertEquals(Segment.Text("Y además:"), segments[1])
        assertEquals(2, (segments[2] as Segment.Table).columnCount)
    }
}
