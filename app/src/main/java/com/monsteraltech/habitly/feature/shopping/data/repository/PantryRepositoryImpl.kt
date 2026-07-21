package com.monsteraltech.habitly.feature.shopping.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.PantryRepository
import com.monsteraltech.habitly.feature.shopping.domain.util.PantryMerge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PantryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : PantryRepository {

    override fun observePantry(householdId: String): Flow<List<PantryItem>> = callbackFlow {
        if (householdId.isBlank()) {
            trySend(emptyList())
            return@callbackFlow
        }

        val collection = pantryOf(householdId).orderBy("name", Query.Direction.ASCENDING)
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(PantryItem::class.java)?.copy(id = doc.id)
                }
                trySend(items)
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun upsertItems(householdId: String, items: List<PantryItem>): Result<Unit> {
        return try {
            if (items.isEmpty()) return Result.success(Unit)

            val collection = pantryOf(householdId)
            val existing = collection.get().await().documents.mapNotNull { doc ->
                doc.toObject(PantryItem::class.java)?.copy(id = doc.id)
            }

            val batch = firestore.batch()
            PantryMerge.merge(existing, items).forEach { item ->
                batch.set(collection.document(item.id), item)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun adjustQuantity(householdId: String, itemId: String, delta: Int): Result<Unit> {
        return try {
            val docRef = pantryOf(householdId).document(itemId)
            val current = docRef.get().await().toObject(PantryItem::class.java)
                ?: return Result.success(Unit)

            val newQuantity = current.quantity + delta
            if (newQuantity <= 0) {
                docRef.delete().await()
            } else {
                docRef.update(
                    mapOf(
                        "quantity" to newQuantity,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteItem(householdId: String, itemId: String): Result<Unit> {
        return try {
            pantryOf(householdId).document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun pantryOf(householdId: String): CollectionReference =
        firestore.collection("households").document(householdId).collection("pantry_items")
}
