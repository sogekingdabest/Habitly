package com.monsteraltech.habitly.feature.notes.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.notes.domain.model.MAX_NOTE_LENGTH
import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import com.monsteraltech.habitly.feature.notes.domain.usecase.AddNoteUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.DeleteNoteUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.ObserveNotesUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val currentUserId: String = "",
    val currentHouseholdId: String = "",
    /** The note being written or edited; null when the editor is closed. */
    val editing: NoteEditorState? = null,
    val isSaving: Boolean = false,
    @StringRes val errorRes: Int? = null
) {
    fun notesOf(type: NoteType): List<Note> = notes.filter { it.type == type }
}

/** Open editor. A null [note] means a new note, otherwise it is an edit. */
data class NoteEditorState(
    val note: Note? = null,
    val type: NoteType = NoteType.PERSONAL,
    val draft: String = ""
) {
    val canSave: Boolean get() = draft.isNotBlank()
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val addNoteUseCase: AddNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    private val currentUserId: String = firebaseAuth.currentUser?.uid ?: ""
    private var notesJob: Job? = null

    init {
        _uiState.update { it.copy(currentUserId = currentUserId) }
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId).collectLatest { profile ->
                val householdId = profile?.activeHouseholdId.orEmpty()
                _uiState.update { it.copy(currentHouseholdId = householdId) }
                observeNotes(householdId)
            }
        }
    }

    private fun observeNotes(householdId: String) {
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            observeNotesUseCase(currentUserId, householdId)
                .catch { _uiState.update { it.copy(isLoading = false, errorRes = R.string.notes_error_load) } }
                .collectLatest { notes ->
                    _uiState.update { it.copy(notes = notes, isLoading = false) }
                }
        }
    }

    // ---------- Editor ----------

    fun onNewNote(type: NoteType) {
        _uiState.update { it.copy(editing = NoteEditorState(note = null, type = type, draft = "")) }
    }

    fun onEditNote(note: Note) {
        _uiState.update {
            it.copy(editing = NoteEditorState(note = note, type = note.type, draft = note.text))
        }
    }

    fun onDraftChange(text: String) {
        _uiState.update { state ->
            val editor = state.editing ?: return@update state
            state.copy(editing = editor.copy(draft = text.take(MAX_NOTE_LENGTH)))
        }
    }

    fun onCloseEditor() {
        _uiState.update { it.copy(editing = null) }
    }

    fun onSaveNote() {
        val state = _uiState.value
        val editor = state.editing ?: return
        if (!editor.canSave || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val existing = editor.note
            val result = if (existing == null) {
                addNoteUseCase(state.currentUserId, state.currentHouseholdId, editor.draft, editor.type)
                    .map { }
            } else {
                updateNoteUseCase(state.currentUserId, state.currentHouseholdId, existing, editor.draft)
            }

            result
                // The listener brings the note back, so only the editor is closed here.
                .onSuccess { _uiState.update { it.copy(isSaving = false, editing = null) } }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, errorRes = R.string.notes_error_save) }
                }
        }
    }

    fun onDeleteNote(note: Note) {
        val state = _uiState.value
        viewModelScope.launch {
            deleteNoteUseCase(state.currentUserId, state.currentHouseholdId, note)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.notes_error_save) } }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorRes = null) }
    }
}
