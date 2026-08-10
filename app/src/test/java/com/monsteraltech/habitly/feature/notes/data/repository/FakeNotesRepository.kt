package com.monsteraltech.habitly.feature.notes.data.repository

import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import com.monsteraltech.habitly.feature.notes.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNotesRepository : NotesRepository {

    private val personal = MutableStateFlow<List<Note>>(emptyList())
    private val household = MutableStateFlow<List<Note>>(emptyList())

    var shouldFail = false
    var addCalls = 0
    var updateCalls = 0
    var deleteCalls = 0

    override fun observePersonalNotes(userId: String): Flow<List<Note>> = personal

    override fun observeHouseholdNotes(householdId: String): Flow<List<Note>> = household

    override suspend fun addNote(userId: String, householdId: String, note: Note): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Fake error"))
        addCalls++
        listFor(note.type).value = listFor(note.type).value + note
        return Result.success(Unit)
    }

    override suspend fun updateNote(userId: String, householdId: String, note: Note): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Fake error"))
        updateCalls++
        listFor(note.type).value = listFor(note.type).value.map { if (it.id == note.id) note else it }
        return Result.success(Unit)
    }

    override suspend fun setNotePinned(userId: String, householdId: String, note: Note): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Fake error"))
        updateCalls++
        listFor(note.type).value = listFor(note.type).value.map { if (it.id == note.id) note else it }
        return Result.success(Unit)
    }

    override suspend fun deleteNote(userId: String, householdId: String, note: Note): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Fake error"))
        deleteCalls++
        listFor(note.type).value = listFor(note.type).value.filterNot { it.id == note.id }
        return Result.success(Unit)
    }

    fun seedPersonal(vararg notes: Note) { personal.value = notes.toList() }

    fun seedHousehold(vararg notes: Note) { household.value = notes.toList() }

    private fun listFor(type: NoteType) =
        if (type == NoteType.PERSONAL) personal else household
}
