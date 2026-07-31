package com.monsteraltech.habitly.feature.share.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.share.domain.util.SharedTextClassifier
import com.monsteraltech.habitly.feature.share.domain.util.SharedTextGuard
import com.monsteraltech.habitly.feature.share.domain.util.SharedTextKind
import com.monsteraltech.habitly.feature.shopping.domain.util.PlainListParser
import javax.inject.Inject

/** What was recognised in a shared text, for the user to review. */
data class SharedTextExtraction(
    val products: List<AiShoppingSuggestion> = emptyList(),
    val routines: List<AiRoutineSuggestion> = emptyList(),
    /** Whether the local model extracted it or the plain-text reader did. */
    val usedAi: Boolean = false
) {
    val isEmpty: Boolean get() = products.isEmpty() && routines.isEmpty()
}

/**
 * Analyses text arriving from another app via "Share with Habitly".
 *
 * It reuses the assistant's machinery as-is: the repository's isolated extraction turn
 * ([AiAssistantRepository.extractShopping] / [AiAssistantRepository.extractRoutines], low
 * temperature, not persisted) and the tolerant parsers of its output. All this path adds is its own
 * concerns: the text goes in **delimited as data** ([SharedTextGuard]), and there is a model-free
 * plan B ([withoutAi]) so the user is not left at a dead end.
 */
class ExtractSharedTextUseCase @Inject constructor(
    private val repository: AiAssistantRepository,
    private val parseAiShoppingListUseCase: ParseAiShoppingListUseCase,
    private val parseAiRoutinesUseCase: ParseAiRoutinesUseCase
) {

    /**
     * Extraction with the local model. It only runs the turns the text kind calls for: each is a
     * full inference, and the first of the day is slow.
     *
     * If the model finds nothing, it falls back to the plain-text reader: better to propose the
     * text's lines than return an empty screen when the user shared a list.
     */
    suspend fun withAi(sanitized: String): SharedTextExtraction {
        if (sanitized.isBlank()) return SharedTextExtraction()

        val kind = SharedTextClassifier(sanitized)
        val payload = SharedTextGuard.asData(sanitized)

        val products = if (kind.includesShopping) {
            parseAiShoppingListUseCase(repository.extractShopping(payload))
        } else {
            emptyList()
        }
        val routines = if (kind.includesRoutines) {
            parseAiRoutinesUseCase(repository.extractRoutines(payload))
        } else {
            emptyList()
        }

        val extraction = SharedTextExtraction(products, routines, usedAi = true)
        return if (extraction.isEmpty) withoutAi(sanitized, kind) else extraction
    }

    /**
     * Model-free extraction: one line (or a comma-separated item) is a product, with its quantity
     * and unit if written. Covers the most common case — a list pasted from WhatsApp or a note —
     * without downloading 2.5 GB.
     */
    fun withoutAi(
        sanitized: String,
        kind: SharedTextKind = SharedTextClassifier(sanitized)
    ): SharedTextExtraction {
        if (sanitized.isBlank()) return SharedTextExtraction()

        val entries = PlainListParser.fromLines(sanitized)
        if (entries.isEmpty()) return SharedTextExtraction()

        // A chores text is proposed as routines; in a mixed text, shopping wins: guessing routines
        // out of a weekly menu without a model would give garbage.
        return if (kind == SharedTextKind.ROUTINES) {
            SharedTextExtraction(
                routines = entries.map { entry ->
                    AiRoutineSuggestion(title = entry.name, frequency = RoutineFrequency.DAILY)
                }
            )
        } else {
            SharedTextExtraction(
                products = entries.map { entry ->
                    AiShoppingSuggestion(
                        name = entry.name,
                        quantity = entry.quantity,
                        unit = entry.unit
                    )
                }
            )
        }
    }
}
