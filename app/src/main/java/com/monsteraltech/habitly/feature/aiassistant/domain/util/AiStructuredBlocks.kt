package com.monsteraltech.habitly.feature.aiassistant.domain.util

/**
 * Convenciones compartidas entre el prompt, los parsers y la UI para los bloques estructurados
 * que el modelo añade al final de una respuesta para que la app pueda actuar sobre ellos.
 *
 * El modelo termina la respuesta con una línea como:
 *   @@LISTA@@ {"shopping_list":[{"name":"Tomate","quantity":6,"unit":"unidad"}]}
 *   @@RUTINA@@ {"routines":[{"title":"Fregar la cocina","frequency":"semanal","days":["lunes"]}]}
 *
 * - Los parsers ([com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase]
 *   y [com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase])
 *   leen lo que hay tras su marcador.
 * - La UI oculta los marcadores y su JSON para no mostrar ruido al usuario.
 */
object AiStructuredBlocks {

    const val SHOPPING_MARKER = "@@LISTA@@"
    const val ROUTINES_MARKER = "@@RUTINA@@"

    private val MARKERS = listOf(SHOPPING_MARKER, ROUTINES_MARKER)

    private val FENCED_BLOCK =
        Regex("```(?:json|JSON)?\\s*\\{[\\s\\S]*?(?:shopping_list|routines)[\\s\\S]*?```")

    /**
     * Devuelve el texto que se muestra al usuario, sin los bloques estructurados.
     * Tolera respuestas en streaming (marcador o fence a medio escribir).
     */
    fun stripFromDisplay(content: String): String {
        var text = content

        // 1. Todo lo que venga a partir del primer marcador es metadato, no se muestra.
        val markerIdx = MARKERS.mapNotNull { marker ->
            text.indexOf(marker).takeIf { it != -1 }
        }.minOrNull()
        if (markerIdx != null) text = text.substring(0, markerIdx)

        // 2. Si el modelo usó un bloque ```json en vez del marcador, se elimina.
        text = FENCED_BLOCK.replace(text, "")

        // 3. Durante el streaming puede aparecer un fence sin cerrar: lo ocultamos.
        val openFence = text.indexOf("```")
        if (openFence != -1 && text.indexOf("```", openFence + 3) == -1) {
            val afterFence = text.substring(openFence)
            if (afterFence.contains("json", ignoreCase = true) ||
                afterFence.contains("shopping_list", ignoreCase = true) ||
                afterFence.contains("routines", ignoreCase = true) ||
                afterFence.contains("{")
            ) {
                text = text.substring(0, openFence)
            }
        }

        return text.trimEnd()
    }

    /**
     * Indica si el contenido (aún en streaming) ha empezado a escribir un bloque
     * estructurado. La UI lo usa para avisar de que "viene algo más": tras el texto
     * visible, el modelo puede pasar decenas de segundos generando el JSON oculto y
     * sin este aviso parece que se ha quedado colgado.
     *
     * Basta con detectar "@@": es el prefijo común de los marcadores y no aparece en
     * texto conversacional normal.
     */
    fun hasPendingStructuredBlock(content: String): Boolean = content.contains("@@")

    /**
     * Aísla la porción de texto que contiene el JSON de un bloque, priorizando su [marker].
     * Si el marcador no aparece, busca el JSON suelto en todo el texto.
     */
    fun extractJsonRegion(text: String, marker: String): String? {
        val markerIdx = text.indexOf(marker)
        var scope = if (markerIdx != -1) text.substring(markerIdx + marker.length) else text

        // Si la respuesta trae los dos bloques, cortamos en el siguiente marcador para no
        // invadir el JSON del otro.
        MARKERS.filter { it != marker }
            .mapNotNull { other -> scope.indexOf(other).takeIf { it != -1 } }
            .minOrNull()
            ?.let { scope = scope.substring(0, it) }

        // Bloque con fences ```json ... ```
        FENCE_REGEX.find(scope)?.let { match ->
            val body = match.groupValues[1].trim()
            if (body.contains("[") || body.contains("{")) return body
        }

        // Array suelto [ ... ]
        val start = scope.indexOf('[')
        val end = scope.lastIndexOf(']')
        if (start != -1 && end > start) return scope.substring(start, end + 1)

        // Objeto suelto { ... }
        val objStart = scope.indexOf('{')
        val objEnd = scope.lastIndexOf('}')
        if (objStart != -1 && objEnd > objStart) return scope.substring(objStart, objEnd + 1)

        return null
    }

    private val FENCE_REGEX = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```")
}
