package com.monsteraltech.habitly.feature.notes.presentation

import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesUiStateTest {

    private val old = Note(id = "old", text = "Comprar leche", updatedAt = 10)
    private val recent = Note(id = "recent", text = "Vacaciones\nReservar hotel", updatedAt = 20)
    private val pinned = Note(id = "pinned", text = "Teléfonos", isPinned = true, updatedAt = 5)

    @Test
    fun `pinned notes stay first when sorting by recency`() {
        val result = NotesUiState(notes = listOf(old, recent, pinned)).notesOf(NoteType.PERSONAL)

        assertEquals(listOf("pinned", "recent", "old"), result.map { it.id })
    }

    @Test
    fun `search matches heading and body ignoring case`() {
        val result = NotesUiState(
            notes = listOf(old, recent, pinned),
            searchQuery = "HOTEL"
        ).notesOf(NoteType.PERSONAL)

        assertEquals(listOf("recent"), result.map { it.id })
    }

    @Test
    fun `title order keeps pinned notes first`() {
        val result = NotesUiState(
            notes = listOf(old, recent, pinned),
            sortOrder = NoteSortOrder.TITLE
        ).notesOf(NoteType.PERSONAL)

        assertEquals(listOf("pinned", "old", "recent"), result.map { it.id })
    }

    @Test
    fun `existing note is unchanged while title and body match`() {
        val editor = NoteEditorState(
            note = recent,
            title = recent.heading,
            body = recent.body
        )

        assertFalse(editor.hasChanges)
    }

    @Test
    fun `existing note detects edited content`() {
        val editor = NoteEditorState(
            note = recent,
            title = "Vacaciones 2027",
            body = recent.body
        )

        assertTrue(editor.hasChanges)
    }

    @Test
    fun `new blank note is unchanged until content is entered`() {
        assertFalse(NoteEditorState().hasChanges)
        assertTrue(NoteEditorState(body = "Una idea").hasChanges)
    }
}
