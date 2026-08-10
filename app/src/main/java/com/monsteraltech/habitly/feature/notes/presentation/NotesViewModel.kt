package com.monsteraltech.habitly.feature.notes.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.notes.domain.model.MAX_NOTE_LENGTH
import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import com.monsteraltech.habitly.feature.notes.domain.usecase.AddNoteUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.DeleteNoteUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.ObserveNotesUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.RestoreNoteUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.SetNotePinnedUseCase
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

enum class NoteSortOrder { UPDATED_DESC, UPDATED_ASC, TITLE }

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val currentUserId: String = "",
    val currentHouseholdId: String = "",
    /** Member id to display name, so a shared note can say who wrote it. */
    val memberNicknames: Map<String, String> = emptyMap(),
    /** The note being written or edited; null when the editor is closed. */
    val editing: NoteEditorState? = null,
    val isSaving: Boolean = false,
    val recentlyDeleted: Note? = null,
    val searchQuery: String = "",
    val sortOrder: NoteSortOrder = NoteSortOrder.UPDATED_DESC,
    val showDiscardConfirmation: Boolean = false,
    @StringRes val errorRes: Int? = null
) {
    fun notesOf(type: NoteType): List<Note> {
        val matching = notes.filter { note ->
            note.type == type && (
                searchQuery.isBlank() || note.text.contains(searchQuery.trim(), ignoreCase = true)
            )
        }
        val comparator = when (sortOrder) {
            NoteSortOrder.UPDATED_DESC -> compareByDescending<Note> { it.isPinned }
                .thenByDescending { it.updatedAt }
            NoteSortOrder.UPDATED_ASC -> compareByDescending<Note> { it.isPinned }
                .thenBy { it.updatedAt }
            NoteSortOrder.TITLE -> compareByDescending<Note> { it.isPinned }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.heading }
        }
        return matching.sortedWith(comparator)
    }
}

/** Open editor. A null [note] means a new note, otherwise it is an edit. */
data class NoteEditorState(
    val note: Note? = null,
    val type: NoteType = NoteType.PERSONAL,
    val title: String = "",
    val body: String = ""
) {
    val text: String
        get() = buildString {
            append(title.trim())
            if (body.isNotBlank()) {
                append('\n')
                append(body.trim())
            }
        }

    val canSave: Boolean get() = title.isNotBlank()
    val hasChanges: Boolean
        get() = note?.let { title != it.heading || body != it.body }
            ?: (title.isNotBlank() || body.isNotBlank())
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val addNoteUseCase: AddNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val restoreNoteUseCase: RestoreNoteUseCase,
    private val setNotePinnedUseCase: SetNotePinnedUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val getMemberProfilesUseCase: GetMemberProfilesUseCase,
    firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    private val currentUserId: String = firebaseAuth.currentUser?.uid ?: ""
    private var notesJob: Job? = null
    private var membersJob: Job? = null

    init {
        _uiState.update { it.copy(currentUserId = currentUserId) }
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId).collectLatest { profile ->
                val householdId = profile?.activeHouseholdId.orEmpty()
                _uiState.update { it.copy(currentHouseholdId = householdId) }
                observeNotes(householdId)
                if (householdId.isNotBlank()) loadMemberNicknames(householdId)
            }
        }
    }

    /**
     * Names come from the household document itself, the same way the routines screen resolves
     * them: no Firestore read per member.
     */
    private fun loadMemberNicknames(householdId: String) {
        membersJob?.cancel()
        membersJob = viewModelScope.launch {
            observeHouseholdUseCase(householdId).collectLatest { household ->
                val nicknames = household?.let {
                    getMemberProfilesUseCase(it)
                        .filter { profile -> profile.nickname.isNotBlank() || profile.displayName.isNotBlank() }
                        .associate { profile -> profile.id to profile.nickname.ifBlank { profile.displayName } }
                }.orEmpty()
                _uiState.update { it.copy(memberNicknames = nicknames) }
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
        _uiState.update { it.copy(editing = NoteEditorState(note = null, type = type)) }
    }

    fun onSearchChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortChange(order: NoteSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun onTogglePinned(note: Note) {
        val state = _uiState.value
        viewModelScope.launch {
            setNotePinnedUseCase(
                state.currentUserId,
                state.currentHouseholdId,
                note,
                pinned = !note.isPinned
            ).onFailure {
                _uiState.update { it.copy(errorRes = R.string.notes_error_save) }
            }
        }
    }

    fun onEditNote(note: Note) {
        _uiState.update {
            it.copy(
                editing = NoteEditorState(
                    note = note,
                    type = note.type,
                    title = note.heading,
                    body = note.body
                )
            )
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { state ->
            val editor = state.editing ?: return@update state
            val maxTitle = (MAX_NOTE_LENGTH - editor.body.length - if (editor.body.isBlank()) 0 else 1)
                .coerceAtLeast(0)
            state.copy(editing = editor.copy(title = title.take(minOf(MAX_NOTE_TITLE_LENGTH, maxTitle))))
        }
    }

    fun onBodyChange(body: String) {
        _uiState.update { state ->
            val editor = state.editing ?: return@update state
            val maxBody = (MAX_NOTE_LENGTH - editor.title.length - 1).coerceAtLeast(0)
            state.copy(editing = editor.copy(body = body.take(maxBody)))
        }
    }

    fun onCloseEditor() {
        _uiState.update { it.copy(editing = null, showDiscardConfirmation = false) }
    }

    /** Returns true when the caller may continue its navigation immediately. */
    fun onRequestCloseEditor(): Boolean {
        val editor = _uiState.value.editing ?: return true
        if (!editor.hasChanges) {
            onCloseEditor()
            return true
        }
        _uiState.update { it.copy(showDiscardConfirmation = true) }
        return false
    }

    fun onDiscardEditorChanges() {
        _uiState.update { it.copy(editing = null, showDiscardConfirmation = false) }
    }

    fun onKeepEditing() {
        _uiState.update { it.copy(showDiscardConfirmation = false) }
    }

    fun onSaveNote() {
        val state = _uiState.value
        val editor = state.editing ?: return
        if (!editor.canSave || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val existing = editor.note
            val result = if (existing == null) {
                addNoteUseCase(state.currentUserId, state.currentHouseholdId, editor.text, editor.type)
                    .map { }
            } else {
                updateNoteUseCase(state.currentUserId, state.currentHouseholdId, existing, editor.text)
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
                .onSuccess { _uiState.update { it.copy(recentlyDeleted = note) } }
                .onFailure { _uiState.update { it.copy(errorRes = R.string.notes_error_save) } }
        }
    }

    fun onRestoreDeletedNote() {
        val state = _uiState.value
        val note = state.recentlyDeleted ?: return
        _uiState.update { it.copy(recentlyDeleted = null) }
        viewModelScope.launch {
            restoreNoteUseCase(state.currentUserId, state.currentHouseholdId, note)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.notes_error_save) } }
        }
    }

    fun onDeletedMessageShown() {
        _uiState.update { it.copy(recentlyDeleted = null) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorRes = null) }
    }
}

private const val MAX_NOTE_TITLE_LENGTH = 120
