package com.monsteraltech.habitly.feature.notes.domain.repository

import com.monsteraltech.habitly.feature.notes.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    /** The user's own notes, updating live. */
    fun observePersonalNotes(userId: String): Flow<List<Note>>

    /** The household's shared board, updating live. */
    fun observeHouseholdNotes(householdId: String): Flow<List<Note>>

    suspend fun addNote(userId: String, householdId: String, note: Note): Result<Unit>

    /** Saves the note's text. Everything else about it stays as it was. */
    suspend fun updateNote(userId: String, householdId: String, note: Note): Result<Unit>

    suspend fun deleteNote(userId: String, householdId: String, note: Note): Result<Unit>
}
