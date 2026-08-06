package com.monsteraltech.habitly.feature.notes.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import com.monsteraltech.habitly.feature.notes.domain.repository.NotesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Notes on Firestore, newest first.
 *
 * Personal notes live under the user, shared ones under the household — which is also what decides
 * who can see them, since each path carries its own security rules.
 */
class NotesRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : NotesRepository {

    override fun observePersonalNotes(userId: String): Flow<List<Note>> =
        observeNotes(userId.takeIf { it.isNotBlank() }?.let { personalNotes(it) })

    override fun observeHouseholdNotes(householdId: String): Flow<List<Note>> =
        observeNotes(householdId.takeIf { it.isNotBlank() }?.let { householdNotes(it) })

    private fun observeNotes(collection: CollectionReference?): Flow<List<Note>> = callbackFlow {
        if (collection == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        val listener = collection
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(MAX_NOTES_SHOWN)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notes = snapshot.documents.mapNotNull {
                        it.toObject(Note::class.java)?.copy(id = it.id)
                    }
                    trySend(notes)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun addNote(userId: String, householdId: String, note: Note): Result<Unit> =
        runCatching {
            collectionFor(note.type, userId, householdId).document(note.id).set(note).await()
        }

    override suspend fun updateNote(userId: String, householdId: String, note: Note): Result<Unit> =
        runCatching {
            // An explicit field map, like the routines repository: it keeps a future field from
            // being wiped by an edit that does not know about it.
            collectionFor(note.type, userId, householdId).document(note.id)
                .update(
                    mapOf(
                        "text" to note.text.trim(),
                        "updatedAt" to note.updatedAt
                    )
                ).await()
        }

    override suspend fun deleteNote(userId: String, householdId: String, note: Note): Result<Unit> =
        runCatching {
            collectionFor(note.type, userId, householdId).document(note.id).delete().await()
        }

    private fun personalNotes(userId: String): CollectionReference =
        firestore.collection("users").document(userId).collection("notes")

    private fun householdNotes(householdId: String): CollectionReference =
        firestore.collection("households").document(householdId).collection("notes")

    private fun collectionFor(type: NoteType, userId: String, householdId: String): CollectionReference =
        if (type == NoteType.PERSONAL) personalNotes(userId) else householdNotes(householdId)

    private companion object {
        // A board, not an archive: enough for any household and it keeps the listener bounded.
        const val MAX_NOTES_SHOWN = 200L
    }
}
