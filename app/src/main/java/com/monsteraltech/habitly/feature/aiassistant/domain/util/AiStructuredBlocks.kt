package com.monsteraltech.habitly.feature.aiassistant.domain.util

/**
 * Conventions shared by the prompt, the parsers and the UI for the structured blocks the model
 * appends to an answer so the app can act on them.
 *
 * The model ends its answer with a line such as:
 *   @@LISTA@@ {"shopping_list":[{"name":"Tomate","quantity":6,"unit":"unidad"}]}
 *   @@RUTINA@@ {"routines":[{"title":"Fregar la cocina","frequency":"semanal","days":["lunes"]}]}
 *
 * The parsers read whatever follows their own marker; the UI hides both the markers and their JSON
 * so the user never sees the noise. Marker names and JSON keys stay in Spanish because they are
 * part of the prompt contract with the model, not user-facing text.
 */
object AiStructuredBlocks {

    const val SHOPPING_MARKER = "@@LISTA@@"
    const val ROUTINES_MARKER = "@@RUTINA@@"

    private val MARKERS = listOf(SHOPPING_MARKER, ROUTINES_MARKER)

    private val FENCED_BLOCK =
        Regex("```(?:json|JSON)?\\s*\\{[\\s\\S]*?(?:shopping_list|routines)[\\s\\S]*?```")

    /**
     * Returns the text shown to the user, with the structured blocks removed. Tolerates streaming
     * answers, where a marker or a fence may be half-written.
     */
    fun stripFromDisplay(content: String): String {
        var text = content

        // 1. Everything from the first marker onwards is metadata and is not shown.
        val markerIdx = MARKERS.mapNotNull { marker ->
            text.indexOf(marker).takeIf { it != -1 }
        }.minOrNull()
        if (markerIdx != null) text = text.substring(0, markerIdx)

        // 2. If the model used a ```json block instead of the marker, strip it.
        text = FENCED_BLOCK.replace(text, "")

        // 3. While streaming an unclosed fence can appear; hide it.
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
     * Whether the still-streaming content has started writing a structured block. The UI uses it to
     * signal that more is coming: after the visible text the model can spend tens of seconds
     * generating the hidden JSON, and without the hint it looks frozen.
     *
     * Detecting "@@" is enough — it is the markers' common prefix and does not appear in ordinary
     * conversational text.
     */
    fun hasPendingStructuredBlock(content: String): Boolean = content.contains("@@")

    /**
     * Isolates the slice of text holding a block's JSON, preferring its [marker]. If the marker is
     * absent, it looks for loose JSON anywhere in the text.
     */
    fun extractJsonRegion(text: String, marker: String): String? {
        val markerIdx = text.indexOf(marker)
        var scope = if (markerIdx != -1) text.substring(markerIdx + marker.length) else text

        // If the answer carries both blocks, cut at the next marker so the other one's JSON is not
        // swallowed.
        MARKERS.filter { it != marker }
            .mapNotNull { other -> scope.indexOf(other).takeIf { it != -1 } }
            .minOrNull()
            ?.let { scope = scope.substring(0, it) }

        // Fenced ```json ... ``` block.
        FENCE_REGEX.find(scope)?.let { match ->
            val body = match.groupValues[1].trim()
            if (body.contains("[") || body.contains("{")) return body
        }

        // Array [ ... ]. If it arrives unclosed — a response truncated for lack of tokens — take it
        // from '[' to the end so the tolerant parser can recover the last object, instead of losing
        // it by cutting at the final ']'.
        val start = scope.indexOf('[')
        if (start != -1) {
            val end = scope.lastIndexOf(']')
            return if (end > start) scope.substring(start, end + 1) else scope.substring(start)
        }

        // Loose object { ... }
        val objStart = scope.indexOf('{')
        val objEnd = scope.lastIndexOf('}')
        if (objStart != -1 && objEnd > objStart) return scope.substring(objStart, objEnd + 1)

        return null
    }

    private val FENCE_REGEX = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```")
}
