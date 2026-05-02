package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import javax.inject.Inject

class GenerateShoppingListUseCase @Inject constructor(
    private val repository: AiAssistantRepository
) {
    private val systemPrompt = """
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

    suspend fun getSystemPrompt(): String = systemPrompt
}
