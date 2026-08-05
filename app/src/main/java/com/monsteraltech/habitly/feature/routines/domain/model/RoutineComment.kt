package com.monsteraltech.habitly.feature.routines.domain.model

/**
 * A message left on a shared routine, so the household can coordinate around it ("I left the
 * laundry half done"). Corresponds to a document in the `comments/{commentId}` subcollection.
 *
 * Deliberately not a chat: comments hang off the routine they talk about, which is what makes them
 * useful without a backend — Firestore listeners already deliver them live.
 */
data class RoutineComment(
    val id: String = "",
    val text: String = "",
    val authorId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Longest comment accepted. Long enough for a note between housemates, short enough to render. */
const val MAX_COMMENT_LENGTH = 500
