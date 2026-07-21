package com.monsteraltech.habitly.feature.shopping.domain.util

import java.text.Normalizer

/**
 * Normaliza nombres de producto para poder compararlos: "Tomate", " tomate " y "TOMÁTE"
 * son el mismo producto.
 *
 * La despensa usa el nombre normalizado como id de documento en Firestore, así que la
 * deduplicación sale gratis: comprar tomates dos semanas seguidas actualiza el mismo
 * documento en vez de crear dos.
 */
object ProductNameNormalizer {

    /** Minúsculas, sin tildes y con los espacios colapsados. */
    fun normalize(name: String): String =
        Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * Id de documento a partir del nombre. Firestore prohíbe `/` en los ids y no admite
     * `.` ni `..` como id completo, así que se sustituyen los caracteres conflictivos.
     * Devuelve null si no queda nada utilizable.
     */
    fun toDocumentId(name: String): String? {
        val normalized = normalize(name)
            .replace(INVALID_ID_CHARS, "-")
            .take(MAX_ID_LENGTH)
            .trim('-', '.', ' ')
        return normalized.takeIf { it.isNotBlank() }
    }

    /** ¿Dos nombres se refieren al mismo producto? */
    fun isSameProduct(a: String, b: String): Boolean = normalize(a) == normalize(b)

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
    private val INVALID_ID_CHARS = Regex("[/\\\\.#\\[\\]*`\\u0000-\\u001F\\u007F]")
    private const val MAX_ID_LENGTH = 120
}
