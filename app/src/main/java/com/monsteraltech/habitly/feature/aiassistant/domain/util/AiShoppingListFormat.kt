package com.monsteraltech.habitly.feature.aiassistant.domain.util

/**
 * Convenciones compartidas entre el prompt, el parser y la UI para el bloque
 * estructurado de la lista de la compra que el modelo añade al final de una respuesta.
 *
 * El modelo termina la respuesta con una línea:
 *   @@LISTA@@ {"shopping_list":[{"name":"Tomate","quantity":6,"unit":"unidad","category":"Frutas y Verduras"}]}
 *
 * - El parser ([com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase])
 *   lee lo que hay tras el marcador.
 * - La UI oculta el marcador y su JSON para no mostrar ruido al usuario.
 */
object AiShoppingListFormat {

    const val MARKER = "@@LISTA@@"

    private val FENCED_LIST_BLOCK =
        Regex("```(?:json|JSON)?\\s*\\{[\\s\\S]*?shopping_list[\\s\\S]*?```")

    /**
     * Devuelve el texto que se muestra al usuario, sin el bloque estructurado.
     * Tolera respuestas en streaming (marcador o fence a medio escribir).
     */
    fun stripFromDisplay(content: String): String {
        var text = content

        // 1. Todo lo que venga a partir del marcador es metadato, no se muestra.
        val markerIdx = text.indexOf(MARKER)
        if (markerIdx != -1) text = text.substring(0, markerIdx)

        // 2. Si el modelo usó un bloque ```json con la lista en vez del marcador, se elimina.
        text = FENCED_LIST_BLOCK.replace(text, "")

        // 3. Durante el streaming puede aparecer un fence sin cerrar: lo ocultamos.
        val openFence = text.indexOf("```")
        if (openFence != -1 && text.indexOf("```", openFence + 3) == -1) {
            val afterFence = text.substring(openFence)
            if (afterFence.contains("json", ignoreCase = true) ||
                afterFence.contains("shopping_list", ignoreCase = true) ||
                afterFence.contains("{")
            ) {
                text = text.substring(0, openFence)
            }
        }

        return text.trimEnd()
    }
}
