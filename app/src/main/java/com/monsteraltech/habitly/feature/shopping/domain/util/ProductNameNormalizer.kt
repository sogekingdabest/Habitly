package com.monsteraltech.habitly.feature.shopping.domain.util

import java.text.Normalizer

/**
 * Normalises product names so they can be compared: "Tomate", " tomate " and "TOMÁTE" are the same
 * product.
 *
 * The pantry uses the normalised name as the Firestore document id, so deduplication comes for
 * free: buying tomatoes two weeks running updates the same document instead of creating two.
 */
object ProductNameNormalizer {

    /** Lowercased, unaccented and with whitespace collapsed. */
    fun normalize(name: String): String =
        Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * Document id from the name. Firestore forbids `/` in ids and rejects `.` and `..` as whole
     * ids, so the offending characters are substituted. Returns null when nothing usable is left.
     */
    fun toDocumentId(name: String): String? {
        val normalized = normalize(name)
            .replace(INVALID_ID_CHARS, "-")
            .take(MAX_ID_LENGTH)
            .trim('-', '.', ' ')
        return normalized.takeIf { it.isNotBlank() }
    }

    /** Whether two names refer to the same product. */
    fun isSameProduct(a: String, b: String): Boolean = normalize(a) == normalize(b)

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
    private val INVALID_ID_CHARS = Regex("[/\\\\.#\\[\\]*`\\u0000-\\u001F\\u007F]")
    private const val MAX_ID_LENGTH = 120
}
