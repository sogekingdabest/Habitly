package com.monsteraltech.habitly.feature.notes.domain.usecase

import com.monsteraltech.habitly.feature.notes.data.repository.FakeNotesRepository
import com.monsteraltech.habitly.feature.notes.domain.model.MAX_NOTE_LENGTH
import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotesUseCasesTest {

    private val repository = FakeNotesRepository()

    private lateinit var observeUseCase: ObserveNotesUseCase
    private lateinit var addUseCase: AddNoteUseCase
    private lateinit var updateUseCase: UpdateNoteUseCase
    private lateinit var deleteUseCase: DeleteNoteUseCase

    private val userId = "user1"
    private val householdId = "house1"

    @Before
    fun setUp() {
        observeUseCase = ObserveNotesUseCase(repository)
        addUseCase = AddNoteUseCase(repository)
        updateUseCase = UpdateNoteUseCase(repository)
        deleteUseCase = DeleteNoteUseCase(repository)
    }

    // ---------- Validación ----------

    @Test
    fun `blank note is rejected`() = runBlocking {
        val result = addUseCase(userId, householdId, "   \n  ", NoteType.PERSONAL)

        assertTrue(result.isFailure)
        assertEquals(0, repository.addCalls)
    }

    @Test
    fun `note is trimmed`() = runBlocking {
        addUseCase(userId, householdId, "  comprar pilas  ", NoteType.PERSONAL)

        val stored = repository.observePersonalNotes(userId).first()
        assertEquals("comprar pilas", stored[0].text)
    }

    @Test
    fun `note longer than the limit is cut`() = runBlocking {
        addUseCase(userId, householdId, "x".repeat(MAX_NOTE_LENGTH + 100), NoteType.PERSONAL)

        val stored = repository.observePersonalNotes(userId).first()
        assertEquals(MAX_NOTE_LENGTH, stored[0].text.length)
    }

    @Test
    fun `household note without a household is rejected`() = runBlocking {
        val result = addUseCase(userId, "", "hola", NoteType.HOUSEHOLD)

        assertTrue(result.isFailure)
        assertEquals(0, repository.addCalls)
    }

    @Test
    fun `personal note does not need a household`() = runBlocking {
        val result = addUseCase(userId, "", "solo mía", NoteType.PERSONAL)

        assertTrue(result.isSuccess)
    }

    // ---------- Cabecera y cuerpo ----------

    @Test
    fun `first line becomes the heading and the rest the body`() {
        val note = Note(text = "Compra semanal\nleche\npan")

        assertEquals("Compra semanal", note.heading)
        assertEquals("leche\npan", note.body)
    }

    @Test
    fun `a one-line note has an empty body`() {
        val note = Note(text = "Sacar la basura")

        assertEquals("Sacar la basura", note.heading)
        assertEquals("", note.body)
    }

    // ---------- Mezcla de orígenes ----------

    @Test
    fun `observe merges both boards newest first`() = runBlocking {
        repository.seedPersonal(Note(id = "p1", text = "mía", type = NoteType.PERSONAL, updatedAt = 100))
        repository.seedHousehold(Note(id = "h1", text = "de casa", type = NoteType.HOUSEHOLD, updatedAt = 200))

        val all = observeUseCase(userId, householdId).first()

        assertEquals(2, all.size)
        assertEquals("h1", all[0].id)
        assertEquals("p1", all[1].id)
    }

    // ---------- Edición y borrado ----------

    @Test
    fun `update moves updatedAt forward`() = runBlocking {
        val note = Note(id = "n1", text = "viejo", type = NoteType.PERSONAL, updatedAt = 1)
        repository.seedPersonal(note)

        updateUseCase(userId, householdId, note, "nuevo")

        val stored = repository.observePersonalNotes(userId).first()
        assertEquals("nuevo", stored[0].text)
        assertTrue("updatedAt debe avanzar", stored[0].updatedAt > 1)
    }

    @Test
    fun `update rejects blank text`() = runBlocking {
        val note = Note(id = "n1", text = "algo", type = NoteType.PERSONAL)
        repository.seedPersonal(note)

        val result = updateUseCase(userId, householdId, note, "   ")

        assertTrue(result.isFailure)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun `delete removes the note`() = runBlocking {
        val note = Note(id = "n1", text = "adiós", type = NoteType.HOUSEHOLD)
        repository.seedHousehold(note)

        deleteUseCase(userId, householdId, note)

        assertTrue(repository.observeHouseholdNotes(householdId).first().isEmpty())
    }

    @Test
    fun `a failing repository does not report success`() = runBlocking {
        repository.shouldFail = true

        assertTrue(addUseCase(userId, householdId, "hola", NoteType.PERSONAL).isFailure)
    }
}
