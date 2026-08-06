package com.monsteraltech.habitly.feature.notes.domain.usecase

import com.monsteraltech.habitly.feature.notes.domain.model.MAX_NOTE_LENGTH
import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import com.monsteraltech.habitly.feature.notes.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject

/**
 * Both boards as one list, newest first — the same shape `ObserveRoutinesUseCase` gives the routines
 * screen. Merging here is what lets the dashboard add notes without exceeding `combine`'s typed
 * overloads.
 */
class ObserveNotesUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    operator fun invoke(userId: String, householdId: String): Flow<List<Note>> =
        combine(
            repository.observePersonalNotes(userId),
            repository.observeHouseholdNotes(householdId)
        ) { personal, household ->
            (personal + household).sortedByDescending { it.updatedAt }
        }
}

class AddNoteUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    /** Trims and caps here rather than in the screen, so the rules hold whoever calls it. */
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        text: String,
        type: NoteType
    ): Result<Note> {
        val clean = text.trim().take(MAX_NOTE_LENGTH)
        if (clean.isEmpty()) return Result.failure(Exception("La nota no puede estar vacía"))
        if (userId.isBlank()) return Result.failure(Exception("Falta el usuario"))
        if (type == NoteType.HOUSEHOLD && householdId.isBlank()) {
            return Result.failure(Exception("Falta la casa"))
        }

        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            text = clean,
            type = type,
            authorId = userId,
            createdAt = now,
            updatedAt = now
        )
        return repository.addNote(userId, householdId, note).map { note }
    }
}

class UpdateNoteUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        note: Note,
        text: String
    ): Result<Unit> {
        val clean = text.trim().take(MAX_NOTE_LENGTH)
        if (clean.isEmpty()) return Result.failure(Exception("La nota no puede estar vacía"))

        // updatedAt moves so the note climbs back to the top of a board sorted by recency.
        return repository.updateNote(
            userId = userId,
            householdId = householdId,
            note = note.copy(text = clean, updatedAt = System.currentTimeMillis())
        )
    }
}

class DeleteNoteUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(userId: String, householdId: String, note: Note): Result<Unit> =
        repository.deleteNote(userId, householdId, note)
}
