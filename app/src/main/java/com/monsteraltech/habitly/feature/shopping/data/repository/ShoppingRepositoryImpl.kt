package com.monsteraltech.habitly.feature.shopping.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingHistory
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class ShoppingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ShoppingRepository {

    override fun observeShoppingList(householdId: String): Flow<List<ShoppingItem>> = callbackFlow {
        val collection = firestore.collection("households")
            .document(householdId)
            .collection("shopping_items")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id)
                }
                // Separamos los items marcados de los no marcados para mandarlos ordenados
                val unChecked = items.filter { !it.isChecked }
                val checked = items.filter { it.isChecked }
                trySend(unChecked + checked)
            }
        }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override suspend fun addShoppingItem(householdId: String, name: String, store: String, authorId: String): Result<Unit> {
        return try {
            val item = ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = name,
                isChecked = false,
                store = store,
                authorId = authorId,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("households")
                .document(householdId)
                .collection("shopping_items")
                .document(item.id)
                .set(item)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleShoppingItem(householdId: String, itemId: String, isChecked: Boolean): Result<Unit> {
        return try {
            firestore.collection("households")
                .document(householdId)
                .collection("shopping_items")
                .document(itemId)
                .update("isChecked", isChecked)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteShoppingItem(householdId: String, itemId: String): Result<Unit> {
        return try {
            firestore.collection("households")
                .document(householdId)
                .collection("shopping_items")
                .document(itemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun archiveShoppingList(householdId: String): Result<Unit> {
        return try {
            val collectionRef = firestore.collection("households")
                .document(householdId)
                .collection("shopping_items")
            
            // Obtenemos todos los items actuales
            val snapshot = collectionRef.get().await()
            val items = snapshot.documents.mapNotNull { doc -> doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id) }
            
            if (items.isEmpty()) return Result.success(Unit)

            val batch = firestore.batch()

            // 1. Guardamos el historial en otra colección usando un data class para evitar fallos de mapeo
            val historyId = UUID.randomUUID().toString()
            val historyData = ShoppingHistory(
                id = historyId,
                createdAt = System.currentTimeMillis(),
                items = items
            )
            
            val historyRef = firestore.collection("households")
                .document(householdId)
                .collection("shopping_history")
                .document(historyId)
                
            batch.set(historyRef, historyData)

            // 2. Borramos los items de la lista activa
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            
            // 3. Ejecutamos ambas acciones de forma 100% atómica
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeShoppingHistory(householdId: String): Flow<List<ShoppingHistory>> = callbackFlow {
        val collection = firestore.collection("households")
            .document(householdId)
            .collection("shopping_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val historyList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ShoppingHistory::class.java)?.copy(id = doc.id)
                }
                trySend(historyList)
            }
        }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun observeCustomStores(householdId: String): Flow<List<String>> = callbackFlow {
        val docRef = firestore.collection("households").document(householdId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val stores = snapshot.get("customStores") as? List<String> ?: emptyList()
                trySend(stores)
            } else {
                trySend(emptyList())
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun addCustomStore(householdId: String, storeName: String): Result<Unit> {
        return try {
            val docRef = firestore.collection("households").document(householdId)
            docRef.set(mapOf("customStores" to FieldValue.arrayUnion(storeName)), SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
