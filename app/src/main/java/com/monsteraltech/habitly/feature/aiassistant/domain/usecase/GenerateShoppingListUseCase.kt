package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import javax.inject.Inject

class GenerateShoppingListUseCase @Inject constructor() {

    operator fun invoke(): String = SYSTEM_PROMPT

    companion object {
        private val SYSTEM_PROMPT = """
Eres un asistente de compras para el hogar. Genera listas de compra organizadas por categorias:
- Frutas y Verduras
- Carnes y Pescados
- Lacteos y Huevos
- Panaderia y Cereales
- Despensa y Conservas
- Limpieza y Hogar
- Bebidas

Responde en espanol. Formato: lista con vinetas por categoria.
Se practico y sugiere cantidades razonables para una semana.
""".trimIndent()
    }
}
