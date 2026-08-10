package com.monsteraltech.habitly.feature.routines.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers how many comments the user had already seen on each routine, so the list can mark the
 * ones with something new.
 *
 * Kept on the device on purpose: "have I read this?" is personal, changes constantly, and writing
 * it to Firestore would cost a write per routine opened for something nobody else needs to know.
 */
@Singleton
class CommentSeenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun seenCount(userId: String, routineId: String): Int =
        prefs.getInt(keyOf(userId, routineId), 0)

    fun markSeen(userId: String, routineId: String, count: Int) {
        if (userId.isBlank() || routineId.isBlank()) return
        prefs.edit { putInt(keyOf(userId, routineId), count) }
    }

    /**
     * The key carries the uid, not just the routine.
     *
     * Two accounts sharing a phone would otherwise inherit each other's read state and see wrong
     * badges — the same mistake that once leaked the assistant's chat history between accounts.
     */
    private fun keyOf(userId: String, routineId: String): String = "${userId}_$routineId"

    private companion object {
        const val PREFS_NAME = "routine_comments_seen"
    }
}
