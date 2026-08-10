package com.monsteraltech.habitly.feature.notes.data.repository

import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFirestoreMapperTest {

    @Test
    fun `write uses canonical fields and excludes computed properties`() {
        val fields = Note(
            id = "note-1",
            text = "Título\nContenido",
            type = NoteType.HOUSEHOLD,
            isPinned = true,
            authorId = "user-1",
            createdAt = 10,
            updatedAt = 20
        ).toFirestoreFields()

        assertEquals(true, fields["isPinned"])
        assertEquals("HOUSEHOLD", fields["type"])
        assertFalse(fields.containsKey("pinned"))
        assertFalse(fields.containsKey("heading"))
        assertFalse(fields.containsKey("body"))
        assertFalse(fields.containsKey("id"))
    }

    @Test
    fun `read restores canonical pinned state`() {
        val note = mapOf<String, Any?>(
            "text" to "Importante",
            "type" to "PERSONAL",
            "isPinned" to true,
            "authorId" to "user-1",
            "createdAt" to 10L,
            "updatedAt" to 20L
        ).toNote(id = "note-1", defaultTimestamp = 99)

        assertEquals("note-1", note.id)
        assertTrue(note.isPinned)
        assertEquals(NoteType.PERSONAL, note.type)
        assertEquals(10L, note.createdAt)
        assertEquals(20L, note.updatedAt)
    }

    @Test
    fun `read supports legacy pinned field`() {
        val note = mapOf<String, Any?>(
            "text" to "Legada",
            "type" to "HOUSEHOLD",
            "pinned" to true
        ).toNote(id = "legacy", defaultTimestamp = 99)

        assertTrue(note.isPinned)
        assertEquals(NoteType.HOUSEHOLD, note.type)
        assertEquals(99L, note.createdAt)
        assertEquals(99L, note.updatedAt)
    }

    @Test
    fun `canonical pinned field wins over legacy value`() {
        val note = mapOf<String, Any?>(
            "isPinned" to false,
            "pinned" to true
        ).toNote(id = "migrating", defaultTimestamp = 99)

        assertFalse(note.isPinned)
    }
}
