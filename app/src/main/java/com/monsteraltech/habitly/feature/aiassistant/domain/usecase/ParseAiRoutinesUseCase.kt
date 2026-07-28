package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.util.AiStructuredBlocks
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import java.io.StringReader
import java.text.Normalizer
import java.util.Calendar
import javax.inject.Inject

/**
 * Extracts structured routines from an assistant answer — see [AiStructuredBlocks] for the block
 * format the model is prompted to emit.
 *
 * Deliberately tolerant, because small on-device models produce imperfect JSON. Unlike the shopping
 * parser, this one **requires its own marker or key**: without one it parses nothing, so a shopping
 * block (which also uses "name") is never mistaken for a list of routines.
 *
 * When nothing parses it returns an empty list and the UI shows no button.
 */
class ParseAiRoutinesUseCase @Inject constructor() {

    operator fun invoke(text: String): List<AiRoutineSuggestion> {
        if (text.isBlank()) return emptyList()
        if (!looksLikeRoutinesBlock(text)) return emptyList()

        val region = AiStructuredBlocks.extractJsonRegion(text, AiStructuredBlocks.ROUTINES_MARKER)
            ?: return emptyList()

        val structured = structuredArray(region)?.let { arrayToSuggestions(it) }.orEmpty()
        if (structured.isNotEmpty()) return structured

        return regexFallback(region)
    }

    private fun looksLikeRoutinesBlock(text: String): Boolean =
        text.contains(AiStructuredBlocks.ROUTINES_MARKER) ||
            ARRAY_KEY_JSON.containsMatchIn(text)

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

    private fun arrayToSuggestions(array: JsonArray): List<AiRoutineSuggestion> {
        val byTitle = LinkedHashMap<String, AiRoutineSuggestion>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val title = obj.stringField(TITLE_KEYS)?.cleanTitle() ?: continue

            val frequency = obj.stringField(FREQUENCY_KEYS).toFrequency()
            val days = obj.daysField()
            val interval = obj.intField(INTERVAL_KEYS)

            byTitle.putIfAbsent(
                title.normalizeKey(),
                buildSuggestion(
                    title = title,
                    description = obj.stringField(DESCRIPTION_KEYS)?.trim().orEmpty(),
                    frequency = frequency,
                    days = days,
                    interval = interval
                )
            )
            if (byTitle.size >= MAX_ROUTINES) break
        }
        return byTitle.values.toList()
    }

    /**
     * Last resort: pulls the fields out of each `{ ... }` object with regexes. It also recovers a
     * final **truncated** object with no closing brace, which is what a model running out of token
     * budget mid-JSON leaves behind; without this the last routine was lost.
     */
    private fun regexFallback(region: String): List<AiRoutineSuggestion> {
        val byTitle = LinkedHashMap<String, AiRoutineSuggestion>()
        for (match in OBJECT_REGEX.findAll(region)) {
            addFromBody(match.value, byTitle)
            if (byTitle.size >= MAX_ROUTINES) break
        }

        // Unclosed final object: whatever follows the last '}' and contains a '{'.
        val lastClose = region.lastIndexOf('}')
        val tail = if (lastClose == -1) region else region.substring(lastClose + 1)
        val open = tail.indexOf('{')
        if (open != -1) addFromBody(tail.substring(open), byTitle)

        return byTitle.values.toList()
    }

    /** Builds a suggestion from an object body (closed or truncated) and adds it. */
    private fun addFromBody(body: String, byTitle: LinkedHashMap<String, AiRoutineSuggestion>) {
        if (byTitle.size >= MAX_ROUTINES) return
        val title = TITLE_REGEX.find(body)?.groupValues?.get(1)?.cleanTitle() ?: return

        val frequency = FREQUENCY_REGEX.find(body)?.groupValues?.get(1).toFrequency()
        val days = DAYS_ARRAY_REGEX.find(body)?.groupValues?.get(1)
            ?.split(',')
            ?.mapNotNull { it.trim().trim('"').toCalendarDay() }
            .orEmpty()
        val interval = INTERVAL_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull()

        byTitle.putIfAbsent(
            title.normalizeKey(),
            buildSuggestion(
                title = title,
                description = DESCRIPTION_REGEX.find(body)?.groupValues?.get(1)?.trim().orEmpty(),
                frequency = frequency,
                days = days,
                interval = interval
            )
        )
    }

    /**
     * Applies the consistency rules the model often skips: a weekly routine with no valid days
     * would never come due, so it degrades to daily; and only interval routines keep `intervalDays`.
     */
    private fun buildSuggestion(
        title: String,
        description: String,
        frequency: RoutineFrequency,
        days: List<Int>,
        interval: Int?
    ): AiRoutineSuggestion {
        val effectiveFrequency = when {
            frequency == RoutineFrequency.WEEKLY && days.isEmpty() -> RoutineFrequency.DAILY
            else -> frequency
        }
        return AiRoutineSuggestion(
            title = title,
            description = description,
            frequency = effectiveFrequency,
            scheduledDays = if (effectiveFrequency == RoutineFrequency.WEEKLY ||
                effectiveFrequency == RoutineFrequency.CUSTOM
            ) days else emptyList(),
            intervalDays = if (effectiveFrequency == RoutineFrequency.EVERY_N_DAYS) {
                (interval ?: DEFAULT_INTERVAL).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
            } else {
                null
            }
        )
    }

    private fun JsonObject.daysField(): List<Int> {
        for (key in DAYS_KEYS) {
            val element = get(key) ?: continue
            if (element.isJsonArray) {
                return element.asJsonArray.mapNotNull { item ->
                    runCatching { item.asString }.getOrNull()?.toCalendarDay()
                }.distinct()
            }
            // Some models return "lunes, jueves" instead of an array.
            runCatching { element.asString }.getOrNull()?.let { raw ->
                return raw.split(',', ';').mapNotNull { it.toCalendarDay() }.distinct()
            }
        }
        return emptyList()
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
            runCatching {
                DIGITS_REGEX.find(element.asString)?.value?.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun String?.toFrequency(): RoutineFrequency {
        val value = this?.normalizeKey() ?: return RoutineFrequency.DAILY
        return when {
            value.isBlank() -> RoutineFrequency.DAILY
            value.contains("semanal") || value.contains("weekly") -> RoutineFrequency.WEEKLY
            value.contains("cada") || value.contains("interval") || value.contains("every") ->
                RoutineFrequency.EVERY_N_DAYS
            value.contains("personaliz") || value.contains("custom") -> RoutineFrequency.CUSTOM
            else -> RoutineFrequency.DAILY
        }
    }

    /** Day names in Spanish or English, independent of accents and case. */
    private fun String.toCalendarDay(): Int? {
        val value = normalizeKey()
        return DAY_NAMES.entries.firstOrNull { (prefix, _) -> value.startsWith(prefix) }?.value
    }

    private fun String.cleanTitle(): String? =
        trim().take(MAX_TITLE_LENGTH).takeIf { it.isNotBlank() }

    /** Lowercased and unaccented, for comparing and deduplicating. */
    private fun String.normalizeKey(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")

    private companion object {
        const val MAX_ROUTINES = 10
        const val MAX_TITLE_LENGTH = 60
        const val DEFAULT_INTERVAL = 3
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 365

        val DIACRITICS_REGEX = Regex("\\p{Mn}+")
        // Literal braces always escaped: the ICU engine on Android 16+ rejects a bare `}`.
        val OBJECT_REGEX = Regex("\\{[^\\{\\}]*\\}")
        val DIGITS_REGEX = Regex("\\d+")

        val TITLE_REGEX = Regex("\"(?:title|titulo|t[ií]tulo|nombre)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        val DESCRIPTION_REGEX = Regex("\"(?:description|descripcion|descripci[oó]n|desc)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        val FREQUENCY_REGEX = Regex("\"(?:frequency|frecuencia|freq)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        val DAYS_ARRAY_REGEX = Regex("\"(?:days|dias|d[ií]as)\"\\s*:\\s*\\[([^\\]]*)\\]", RegexOption.IGNORE_CASE)
        val INTERVAL_REGEX = Regex("\"(?:interval_days|intervalo|cada|interval)\"\\s*:\\s*\"?(\\d+)", RegexOption.IGNORE_CASE)

        val ARRAY_KEYS = listOf("routines", "rutinas", "habits", "habitos")
        // The key in JSON form (e.g. `"routines": [`), not the bare word: stops the parser firing
        // just because the text happens to mention "rutinas" in ordinary prose.
        val ARRAY_KEY_JSON = Regex("\"(?:routines|rutinas|habits|habitos)\"\\s*:\\s*\\[", RegexOption.IGNORE_CASE)
        val TITLE_KEYS = listOf("title", "titulo", "título", "nombre")
        val DESCRIPTION_KEYS = listOf("description", "descripcion", "descripción", "desc")
        val FREQUENCY_KEYS = listOf("frequency", "frecuencia", "freq")
        val DAYS_KEYS = listOf("days", "dias", "días", "scheduled_days")
        val INTERVAL_KEYS = listOf("interval_days", "intervalo", "cada", "interval")

        /** Prefixes long enough to tell each day apart in Spanish and English. */
        val DAY_NAMES = linkedMapOf(
            "lun" to Calendar.MONDAY,
            "mon" to Calendar.MONDAY,
            "mar" to Calendar.TUESDAY,
            "tue" to Calendar.TUESDAY,
            "mie" to Calendar.WEDNESDAY,
            "wed" to Calendar.WEDNESDAY,
            "jue" to Calendar.THURSDAY,
            "thu" to Calendar.THURSDAY,
            "vie" to Calendar.FRIDAY,
            "fri" to Calendar.FRIDAY,
            "sab" to Calendar.SATURDAY,
            "sat" to Calendar.SATURDAY,
            "dom" to Calendar.SUNDAY,
            "sun" to Calendar.SUNDAY
        )
    }
}
