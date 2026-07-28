package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import java.text.Normalizer
import javax.inject.Inject

/**
 * Decides whether the user's message asks to **create** new routines, as opposed to merely asking
 * about the ones they already have. This is the intent gate of the second turn: when it returns
 * `false` no extraction runs and no routine card appears, which keeps nonsensical suggestions away.
 *
 * The heuristic is deliberately conservative: it requires a specific creation verb next to a
 * routine noun, or a phrase that already implies creating one. Generic verbs are avoided because
 * they also show up in queries about existing routines.
 *
 * Verbs are matched at a **word boundary** (`\b`), not as substrings, so "ponme una rutina" opens
 * the gate while "¿me respondes con mis rutinas?" does not. Short verbs carry their closed forms,
 * because as open prefixes they collide with unrelated words.
 *
 * The patterns are Spanish and unaccented: the model is prompted in Spanish and the text arrives
 * normalised.
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
     * Short confirmation of an earlier proposal: "sí", "vale", "créalas". It does not say **what**
     * to create, since it carries no noun, so it only opens the gate when the caller confirms the
     * assistant's last message [looksLikeRoutineProposal].
     */
    fun isFollowUpConfirmation(userMessage: String): Boolean {
        val text = userMessage.normalizeForMatch()
        if (text.isBlank() || text.length > MAX_FOLLOW_UP_LENGTH) return false
        if (AFFIRMATIVE_REGEX.containsMatchIn(text)) return true
        // Creation verb with a pronoun and no noun: "créalas", "apúntalas", "ponlas".
        return CREATION_VERB_REGEX.containsMatchIn(text)
    }

    /**
     * Heuristic for "the assistant just proposed routines": it names routines or habits **and**
     * uses proposal language **in the same sentence**, within a bounded window that does not cross
     * strong punctuation.
     *
     * The proposal marker stops a listing of routines that already exist from being read as a
     * proposal to create new ones; the proximity requirement stops the false positive of an answer
     * about something else — a shopping list opening with a recommendation — that mentions
     * "routine" in passing in another sentence.
     */
    fun looksLikeRoutineProposal(assistantText: String): Boolean {
        val text = assistantText.normalizeForMatch()
        if (text.isBlank()) return false
        return PROPOSAL_NEAR_NOUN_REGEX.containsMatchIn(text)
    }

    /** Lowercased and unaccented, so matching depends on neither accents nor case. */
    private fun String.normalizeForMatch(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")

    private companion object {
        val DIACRITICS_REGEX = Regex("\\p{Mn}+")

        /**
         * Verbs expressing create/add for routines. Open prefixes where every continuation is a
         * form of the verb (anad → añade, añadir, añádeme), and closed forms for the short,
         * ambiguous ones.
         */
        val CREATION_VERB_REGEX = listOf(
            "propon", "sugier", "planifica", "establece", "anad", "agreg", "apunt",
            "crea(?:r(?:me|nos)?|me|nos|la|las|lo)?\\b",
            "monta(?:r(?:me|nos)?|me|nos)?\\b",
            "genera(?:r(?:me)?|me)?\\b",
            "pon(?:er(?:me|nos)?|me|nos|la|las|ga)?\\b",
            "haz(?:me|nos|la|las|lo)?\\b"
        ).joinToString("|").let { Regex("\\b(?:$it)") }

        /** Routine nouns. */
        val ROUTINE_NOUNS = listOf("rutina", "habito")

        /** Phrases that on their own imply wanting to create routines. */
        val CREATION_PHRASES = listOf(
            "plan de limpieza", "plan de rutinas", "nuevas rutinas", "rutinas nuevas",
            "nueva rutina", "rutina nueva", "ideas de rutinas"
        )

        /** Length cap for a confirmation: anything longer is a request with content of its own. */
        const val MAX_FOLLOW_UP_LENGTH = 60

        /** Short affirmations, as whole words ("si" on its own, not inside "casi"). */
        val AFFIRMATIVE_REGEX =
            Regex("\\b(?:si|vale|adelante|claro|perfecto|ok|venga|genial|de acuerdo)\\b")

        /** The assistant's proposal language (conjugation prefixes). */
        val PROPOSAL_MARKERS = listOf(
            "propong", "sugier", "recomiend", "recomendar",
            "podrias crear", "puedes crear", "podrias anadir", "puedes anadir"
        )

        /** Marker-to-noun window within the same sentence, in characters. */
        const val PROPOSAL_WINDOW_CHARS = 80

        /** Proposal marker and routine noun close together, without crossing `.!?` or a newline. */
        val PROPOSAL_NEAR_NOUN_REGEX = run {
            val marker = "(?:${PROPOSAL_MARKERS.joinToString("|")})"
            val noun = "(?:${ROUTINE_NOUNS.joinToString("|")})"
            val gap = "[^.!?\\n]{0,$PROPOSAL_WINDOW_CHARS}"
            Regex("$marker$gap$noun|$noun$gap$marker")
        }
    }
}
