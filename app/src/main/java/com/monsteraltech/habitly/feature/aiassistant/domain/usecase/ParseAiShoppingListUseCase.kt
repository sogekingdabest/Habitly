package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.util.AiStructuredBlocks
import java.io.StringReader
import javax.inject.Inject

/**
 * Extracts a structured shopping list from an assistant answer — see [AiStructuredBlocks] for the
 * block format the model is prompted to emit.
 *
 * Deliberately tolerant, because small on-device models produce imperfect JSON: the block is taken
 * with or without ```fences``` and with or without its marker, as a bare array or wrapped in an
 * object, with field names in Spanish or English; and malformed JSON falls back to regex extraction.
 *
 * When nothing parses it returns an empty list and the UI shows no button.
 */
class ParseAiShoppingListUseCase @Inject constructor() {

    operator fun invoke(text: String): List<AiShoppingSuggestion> {
        if (text.isBlank()) return emptyList()
        val region = AiStructuredBlocks.extractJsonRegion(text, AiStructuredBlocks.SHOPPING_MARKER)
            ?: return emptyList()

        val structured = structuredArray(region)?.let { arrayToSuggestions(it) }.orEmpty()
        if (structured.isNotEmpty()) return structured

        return regexFallback(region)
    }

    private fun structuredArray(region: String): JsonArray? {
        val element = parseLenient(region) ?: return null
        return when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> {
                val obj = element.asJsonObject
                ARRAY_KEYS.firstNotNullOfOrNull { key ->
                    obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
                }
            }
            else -> null
        }
    }

    @Suppress("DEPRECATION") // setStrictness(LENIENT) does not exist in Gson 2.10.1; isLenient is the valid API.
    private fun parseLenient(region: String): JsonElement? {
        return try {
            val reader = JsonReader(StringReader(region))
            reader.isLenient = true
            JsonParser.parseReader(reader)
        } catch (_: Exception) {
            null
        }
    }

    private fun arrayToSuggestions(array: JsonArray): List<AiShoppingSuggestion> {
        val byName = LinkedHashMap<String, AiShoppingSuggestion>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val name = obj.stringField(NAME_KEYS)?.cleanName() ?: continue
            byName.putIfAbsent(
                name.lowercase(),
                AiShoppingSuggestion(
                    name = name,
                    quantity = obj.intField(QUANTITY_KEYS)?.coerceIn(1, 999) ?: 1,
                    unit = obj.stringField(UNIT_KEYS)?.trim()?.takeIf { it.isNotBlank() } ?: "unidad",
                    category = obj.stringField(CATEGORY_KEYS)?.trim().orEmpty()
                )
            )
        }
        return byName.values.toList()
    }

    /** Last resort: pulls the fields out of each `{ ... }` object with regexes. */
    private fun regexFallback(region: String): List<AiShoppingSuggestion> {
        val byName = LinkedHashMap<String, AiShoppingSuggestion>()
        for (match in OBJECT_REGEX.findAll(region)) {
            val body = match.value
            val name = NAME_REGEX.find(body)?.groupValues?.get(1)?.cleanName() ?: continue
            byName.putIfAbsent(
                name.lowercase(),
                AiShoppingSuggestion(
                    name = name,
                    quantity = QUANTITY_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 999) ?: 1,
                    unit = UNIT_REGEX.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: "unidad",
                    category = CATEGORY_REGEX.find(body)?.groupValues?.get(1)?.trim().orEmpty()
                )
            )
        }
        return byName.values.toList()
    }

    private fun JsonObject.stringField(keys: List<String>): String? {
        for (key in keys) {
            val element = get(key) ?: continue
            if (element.isJsonNull || !element.isJsonPrimitive) continue
            runCatching { return element.asString }
        }
        return null
    }

    private fun JsonObject.intField(keys: List<String>): Int? {
        for (key in keys) {
            val element = get(key) ?: continue
            if (element.isJsonNull || !element.isJsonPrimitive) continue
            runCatching { return element.asInt }
            // "2 kg" and other textual values: take the first number.
            runCatching {
                DIGITS_REGEX.find(element.asString)?.value?.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun String.cleanName(): String? {
        val trimmed = trim().take(60)
        return trimmed.takeIf { it.isNotBlank() }
    }

    private companion object {
        // Literal braces always escaped: the ICU engine on Android 16+ rejects a bare `}`.
        val OBJECT_REGEX = Regex("\\{[^\\{\\}]*\\}")
        val DIGITS_REGEX = Regex("\\d+")

        val NAME_REGEX = Regex("\"(?:name|nombre|producto|item)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        val QUANTITY_REGEX = Regex("\"(?:quantity|cantidad|qty)\"\\s*:\\s*\"?(\\d+)", RegexOption.IGNORE_CASE)
        val UNIT_REGEX = Regex("\"(?:unit|unidad|units)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        val CATEGORY_REGEX = Regex("\"(?:category|categor[ií]a|cat)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)

        val ARRAY_KEYS = listOf("shopping_list", "shoppingList", "items", "productos", "lista", "list", "products")
        val NAME_KEYS = listOf("name", "nombre", "producto", "item")
        val QUANTITY_KEYS = listOf("quantity", "cantidad", "qty")
        val UNIT_KEYS = listOf("unit", "unidad", "units")
        val CATEGORY_KEYS = listOf("category", "categoria", "categoría", "cat")
    }
}
