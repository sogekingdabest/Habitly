package com.monsteraltech.habitly.feature.notes.domain.model

/**
 * Whose note it is. Mirrors `RoutineType`, and decides which collection the note lives in.
 *
 * Careful: Firestore serialises this by name, so adding a value breaks older app versions that
 * read a note using it.
 */
enum class NoteType {
    PERSONAL,
    HOUSEHOLD
}

/**
 * A free-text note. Household ones work as a shared board; personal ones only their owner sees.
 *
 * There is no title field on purpose: the first line is shown as the heading in the list. People
 * write that way anyway, and it is one less field to fill in and to keep.
 */
data class Note(
    val id: String = "",
    val text: String = "",
    val type: NoteType = NoteType.PERSONAL,
    /** Pinned notes stay above the rest of their board. Missing Firestore fields default to false. */
    val isPinned: Boolean = false,
    val authorId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** First line, for the list heading. */
    val heading: String
        get() = text.lineSequence().firstOrNull()?.trim().orEmpty()

    /** Everything after the first line, or empty when the note is a one-liner. */
    val body: String
        get() = text.substringAfter('\n', "").trim()
}

/** Longest note accepted. Room for a shopping-list-sized note without turning into a document. */
const val MAX_NOTE_LENGTH = 2000
