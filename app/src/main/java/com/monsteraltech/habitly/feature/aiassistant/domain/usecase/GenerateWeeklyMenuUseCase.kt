package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import javax.inject.Inject

/**
 * Prompt del planificador de menús. Pide a la IA un menú semanal de cenas y la lista
 * de la compra consolidada. Al ser un menú, la personalidad base hace que el modelo
 * añada el bloque estructurado (@@LISTA@@), por lo que aparece el botón "Añadir a la lista".
 */
class GenerateWeeklyMenuUseCase @Inject constructor() {

    operator fun invoke(): String = PROMPT

    companion object {
        private val PROMPT = """
Planifica un menú de cenas para toda la semana (de lunes a domingo).
Para cada día indica un plato sencillo y variado.
Al final, dame la lista de la compra consolidada con todos los ingredientes que necesito para el menú, con cantidades razonables para una persona.
Responde en español y de forma clara y organizada.
""".trimIndent()
    }
}
