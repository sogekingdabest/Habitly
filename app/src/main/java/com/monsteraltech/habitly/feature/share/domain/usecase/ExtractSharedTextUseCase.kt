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

/** Lo que se ha reconocido en un texto compartido, para que el usuario lo revise. */
data class SharedTextExtraction(
    val products: List<AiShoppingSuggestion> = emptyList(),
    val routines: List<AiRoutineSuggestion> = emptyList(),
    /** Si lo ha extraído el modelo local o el lector de texto plano. */
    val usedAi: Boolean = false
) {
    val isEmpty: Boolean get() = products.isEmpty() && routines.isEmpty()
}

/**
 * Analiza el texto que llega de otra app por "Compartir con Habitly".
 *
 * Reutiliza tal cual la maquinaria del asistente: el turno de extracción aislado del repositorio
 * ([AiAssistantRepository.extractShopping] / [AiAssistantRepository.extractRoutines], temperatura
 * baja y no persistido) y los parsers tolerantes de su salida. Aquí solo se añade lo propio de
 * este camino: el texto va **delimitado como datos** ([SharedTextGuard]) y hay un plan B sin
 * modelo ([withoutAi]) para no dejar al usuario en un callejón sin salida.
 */
class ExtractSharedTextUseCase @Inject constructor(
    private val repository: AiAssistantRepository,
    private val parseAiShoppingListUseCase: ParseAiShoppingListUseCase,
    private val parseAiRoutinesUseCase: ParseAiRoutinesUseCase
) {

    /**
     * Extracción con el modelo local. Solo lanza los turnos que pide el tipo de texto: cada uno
     * es una inferencia completa, y la primera del día tarda.
     *
     * Si el modelo no encuentra nada, cae al lector de texto plano: es mejor proponer las líneas
     * del texto que devolver una pantalla vacía cuando el usuario ha compartido una lista.
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
     * Extracción sin modelo: una línea (o un elemento separado por comas) es un producto, con
     * su cantidad y unidad si vienen escritas. Cubre el caso más común —una lista pegada de
     * WhatsApp o de una nota— sin descargar 2,5 GB.
     */
    fun withoutAi(
        sanitized: String,
        kind: SharedTextKind = SharedTextClassifier(sanitized)
    ): SharedTextExtraction {
        if (sanitized.isBlank()) return SharedTextExtraction()

        val entries = PlainListParser.fromLines(sanitized)
        if (entries.isEmpty()) return SharedTextExtraction()

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
