package com.monsteraltech.habitly.feature.routines.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.routinesDataStore: DataStore<Preferences> by preferencesDataStore(name = "routines_cache")

@Singleton
class RoutinesCacheManager @Inject constructor(
    private val context: Context
) {
    private val gson = Gson()

    fun observePersonalRoutines(userId: String): Flow<List<Routine>> {
        val key = stringPreferencesKey("personal_routines_$userId")
        return context.routinesDataStore.data.map { prefs ->
            val json = prefs[key] ?: return@map emptyList()
            try {
                val type = object : TypeToken<List<Routine>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>> {
        val key = stringPreferencesKey("household_routines_$householdId")
        return context.routinesDataStore.data.map { prefs ->
            val json = prefs[key] ?: return@map emptyList()
            try {
                val type = object : TypeToken<List<Routine>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun cachePersonalRoutines(userId: String, routines: List<Routine>) {
        val key = stringPreferencesKey("personal_routines_$userId")
        context.routinesDataStore.edit { prefs ->
            prefs[key] = gson.toJson(routines)
        }
    }

    suspend fun cacheHouseholdRoutines(householdId: String, routines: List<Routine>) {
        val key = stringPreferencesKey("household_routines_$householdId")
        context.routinesDataStore.edit { prefs ->
            prefs[key] = gson.toJson(routines)
        }
    }

    suspend fun removeRoutine(userId: String, householdId: String, routineId: String, type: RoutineType) {
        if (type == RoutineType.PERSONAL) {
            val key = stringPreferencesKey("personal_routines_$userId")
            context.routinesDataStore.edit { prefs ->
                val json = prefs[key]
                if (json != null) {
                    val listType = object : TypeToken<List<Routine>>() {}.type
                    val routines: List<Routine> = gson.fromJson(json, listType)
                    prefs[key] = gson.toJson(routines.filter { it.id != routineId })
                }
            }
        } else {
            val key = stringPreferencesKey("household_routines_$householdId")
            context.routinesDataStore.edit { prefs ->
                val json = prefs[key]
                if (json != null) {
                    val listType = object : TypeToken<List<Routine>>() {}.type
                    val routines: List<Routine> = gson.fromJson(json, listType)
                    prefs[key] = gson.toJson(routines.filter { it.id != routineId })
                }
            }
        }
    }

    suspend fun clearCache(userId: String, householdId: String) {
        context.routinesDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
