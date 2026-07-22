package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import java.text.Normalizer
import javax.inject.Inject

/**
 * Decide si el mensaje del usuario pide **crear** rutinas nuevas (frente a solo consultar las que
 * ya tiene). Es la "puerta de intención" del segundo turno: si devuelve `false`, no se lanza la
 * extracción y no aparece tarjeta de crear rutinas. Así evitamos sugerencias sin sentido (p. ej.
 * ofrecer crear rutinas cuando el usuario pregunta por las que ya existen).
 *
 * Heurística deliberadamente conservadora y ajustable: exige un verbo de creación específico junto
 * a un sustantivo de rutina, o una frase que ya implica crear ("plan de limpieza"). Evita verbos
 * genéricos (dame, quiero, organiza) que también aparecen en consultas del tipo "organiza mi día
 * con las rutinas que tengo".
 *
 * Los verbos se buscan como **inicio de palabra** (`\b`), no como substring: así "ponme una
 * rutina" abre la puerta pero "¿me respondes con mis rutinas?" no, y "créame" cuenta sin que
 * cuente "ideas creativas". Los verbos cortos (pon, haz, crea…) llevan sus formas cerradas
 * porque como prefijo abierto colisionan con otras palabras (pongo, hazaña, creativas).
 */
class RoutineCreationIntentUseCase @Inject constructor() {

    operator fun invoke(userMessage: String): Boolean {
        val text = userMessage.normalizeForMatch()
        if (text.isBlank()) return false
        if (CREATION_PHRASES.any { text.contains(it) }) return true
        val hasVerb = CREATION_VERB_REGEX.containsMatchIn(text)
        val hasNoun = ROUTINE_NOUNS.any { text.contains(it) }
        return hasVerb && hasNoun
    }

    /**
     * Confirmación corta de una propuesta previa: "sí", "vale", "créalas", "ponlas". No dice
     * QUÉ crear (no lleva sustantivo), así que solo abre la puerta si quien llama confirma
     * que el último mensaje del asistente [looksLikeRoutineProposal].
     */
    fun isFollowUpConfirmation(userMessage: String): Boolean {
        val text = userMessage.normalizeForMatch()
        if (text.isBlank() || text.length > MAX_FOLLOW_UP_LENGTH) return false
        if (AFFIRMATIVE_REGEX.containsMatchIn(text)) return true
        // Verbo de creación con pronombre y sin sustantivo: "créalas", "apúntalas", "ponlas".
        return CREATION_VERB_REGEX.containsMatchIn(text)
    }

    /**
     * Heurística de "el asistente acaba de proponer rutinas": nombra rutinas/hábitos Y usa
     * lenguaje de propuesta EN LA MISMA FRASE (ventana acotada, sin cruzar puntuación
     * fuerte). El marcador evita confundir un listado de las rutinas que YA existen con una
     * propuesta de crear nuevas; la cercanía evita el falso positivo de una respuesta de
     * OTRO tema (una lista de la compra con "te recomiendo…" al principio) que menciona
     * "rutina" de pasada en otra frase.
     */
    fun looksLikeRoutineProposal(assistantText: String): Boolean {
        val text = assistantText.normalizeForMatch()
        if (text.isBlank()) return false
        return PROPOSAL_NEAR_NOUN_REGEX.containsMatchIn(text)
    }

    /** Minúsculas y sin tildes, para comparar sin depender de acentos ni mayúsculas. */
    private fun String.normalizeForMatch(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")

    private companion object {
        val DIACRITICS_REGEX = Regex("\\p{Mn}+")

        /**
         * Verbos que expresan crear/añadir rutinas (sin tildes, el texto llega normalizado).
         * Prefijos abiertos donde todas las continuaciones son formas del verbo (anad → añade,
         * añadir, añádeme…) y formas cerradas para los verbos cortos ambiguos.
         */
        val CREATION_VERB_REGEX = listOf(
            "propon", "sugier", "planifica", "establece", "anad", "agreg", "apunt",
            "crea(?:r(?:me|nos)?|me|nos|la|las|lo)?\\b",
            "monta(?:r(?:me|nos)?|me|nos)?\\b",
            "genera(?:r(?:me)?|me)?\\b",
            "pon(?:er(?:me|nos)?|me|nos|la|las|ga)?\\b",
            "haz(?:me|nos|la|las|lo)?\\b"
        ).joinToString("|").let { Regex("\\b(?:$it)") }

        /** Sustantivos de rutina (sin tildes). */
        val ROUTINE_NOUNS = listOf("rutina", "habito")

        /** Frases que por sí solas implican querer crear rutinas. */
        val CREATION_PHRASES = listOf(
            "plan de limpieza", "plan de rutinas", "nuevas rutinas", "rutinas nuevas",
            "nueva rutina", "rutina nueva", "ideas de rutinas"
        )

        /** Tope de longitud de una confirmación: más largo ya es una petición con contenido. */
        const val MAX_FOLLOW_UP_LENGTH = 60

        /** Afirmaciones cortas, como palabra completa ("si" suelto, no dentro de "casi"). */
        val AFFIRMATIVE_REGEX =
            Regex("\\b(?:si|vale|adelante|claro|perfecto|ok|venga|genial|de acuerdo)\\b")

        /** Lenguaje de propuesta del asistente (sin tildes; prefijos de conjugación). */
        val PROPOSAL_MARKERS = listOf(
            "propong", "sugier", "recomiend", "recomendar",
            "podrias crear", "puedes crear", "podrias anadir", "puedes anadir"
        )

        /** Ventana marcador↔sustantivo dentro de la misma frase (en caracteres). */
        const val PROPOSAL_WINDOW_CHARS = 80

        /** Marcador de propuesta y sustantivo de rutina cerca y sin cruzar `.!?` ni salto. */
        val PROPOSAL_NEAR_NOUN_REGEX = run {
            val marker = "(?:${PROPOSAL_MARKERS.joinToString("|")})"
            val noun = "(?:${ROUTINE_NOUNS.joinToString("|")})"
            val gap = "[^.!?\\n]{0,$PROPOSAL_WINDOW_CHARS}"
            Regex("$marker$gap$noun|$noun$gap$marker")
        }
    }
}
