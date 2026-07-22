package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import javax.inject.Inject

/**
 * Prompt del planificador de menús. Pide a la IA un menú semanal de cenas y la lista de la
 * compra consolidada, con cantidades para el número real de personas de la casa. La tarjeta
 * de "añadir a la lista" sale del segundo turno de extracción (la puerta de intención de la
 * compra se abre con "menú"/"compra").
 */
class GenerateWeeklyMenuUseCase @Inject constructor() {

    operator fun invoke(memberCount: Int = 1): String {
        val people = if (memberCount <= 1) "una persona" else "$memberCount personas"
        return """
Planifica un menú de cenas para toda la semana (de lunes a domingo).
Para cada día indica un plato sencillo y variado.
Al final, dame la lista de la compra consolidada con todos los ingredientes que necesito para el menú, con cantidades razonables para $people.
Responde en español y de forma clara y organizada.
""".trimIndent()
    }
}
