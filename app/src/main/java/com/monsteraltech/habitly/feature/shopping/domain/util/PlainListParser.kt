package com.monsteraltech.habitly.feature.shopping.domain.util

/**
 * A product recognised in plain text, with just enough to create a list row. The default unit is
 * the one Firestore stores (`"unidad"`, see `DEFAULT_UNIT`).
 */
data class ParsedProduct(
    val name: String,
    val quantity: Int = 1,
    val unit: String = "unidad"
)

/**
 * Reads lists written the human way, with no AI: one line per product (shared text) or a dictated
 * phrase ("leche, huevos y pan").
 *
 * It is the **fallback path** for two features:
 *  - "Share with Habitly" when the local model is not downloaded — without it the user hits a
 *    dead end.
 *  - Voice dictation, where an instant, predictable answer beats several seconds of inference from
 *    a gigabyte-sized model for three words.
 *
 * Pure functions with no Android dependency, so they are testable under JUnit. Quantity and unit
 * are recognised in the usual Spanish and English shapes: `2 kg de tomates`, `500 g harina`,
 * `dos litros de leche`, `leche x2`, `3 huevos`. Anything it does not understand stays part of the
 * name, which is the cheap failure: the user sees the proposal and corrects it.
 */
object PlainListParser {

    /** Cap on products taken from shared text; not even a long recipe yields more. */
    const val MAX_ITEMS = 40

    /** Cap for a dictated phrase: nobody dictates twenty things at once. */
    const val MAX_SPOKEN_ITEMS = 20

    /**
     * One line = one product, for text arriving through "Share". Commas split too, because a list
     * sent over WhatsApp usually comes as a single line ("leche, huevos, pan"). "y" does **not**
     * split here — in a long text it turns up inside ordinary sentences.
     */
    fun fromLines(text: String, limit: Int = MAX_ITEMS): List<ParsedProduct> =
        parseEntries(LINE_SEPARATORS.split(text).asSequence(), limit)

    /**
     * A dictated phrase, split on commas, semicolons and the conjunctions "y"/"e"/"and", so
     * "Leche, huevos y pan" is three products rather than one.
     */
    fun fromSpeech(text: String, limit: Int = MAX_SPOKEN_ITEMS): List<ParsedProduct> =
        parseEntries(SPEECH_SEPARATORS.split(text).asSequence(), limit)

    private fun parseEntries(entries: Sequence<String>, limit: Int): List<ParsedProduct> {
        val byName = LinkedHashMap<String, ParsedProduct>()
        for (entry in entries) {
            val product = parseEntry(entry) ?: continue
            byName.putIfAbsent(ProductNameNormalizer.normalize(product.name), product)
            if (byName.size >= limit) break
        }
        return byName.values.toList()
    }

    /** Turns a line or fragment into a product, or null if it does not look like one. */
    fun parseEntry(raw: String): ParsedProduct? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        // Bullets, numbering and checkboxes from a list pasted in from anywhere.
        text = text.replace(BULLET_PREFIX, "").trim()
        // A heading ("Ingredientes:") is not a product.
        if (text.endsWith(':')) return null
        text = text.trim(*TRAILING_PUNCTUATION)
        if (text.isBlank()) return null

        // A paragraph of instructions is not a product, nor is a link or a line without letters.
        if (text.length > MAX_ENTRY_LENGTH) return null
        if (!HAS_LETTER.containsMatchIn(text)) return null
        if (URL.containsMatchIn(text)) return null

        val tokens = text.split(WHITESPACE).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var index = 0
        var quantity: Int? = null
        var unit: String? = null

        // 1. Quantity at the start: "2 …", "2kg …", "2x …", "dos …".
        val numeric = splitNumericToken(tokens[0])
        if (numeric != null) {
            val (value, suffix) = numeric
            val suffixUnit = suffix?.let { canonicalUnit(it) }
            when {
                suffix == null || suffix.equals("x", ignoreCase = true) -> {
                    quantity = value
                    index = 1
                }
                suffixUnit != null -> {
                    quantity = value
                    unit = suffixUnit
                    index = 1
                }
                // "2ª", "2do"… is not quantity + unit: leave it as part of the name.
                else -> Unit
            }
        } else {
            NUMBER_WORDS[normalizeWord(tokens[0])]?.let { value ->
                quantity = value
                index = 1
            }
        }

        // 2. After the quantity may come the unit and/or a filler "de"/"of".
        if (quantity != null) {
            if (unit == null && index < tokens.size) {
                if (tokens[index].equals("x", ignoreCase = true)) index++
                else canonicalUnit(tokens[index])?.let { unit = it; index++ }
            }
            if (index < tokens.size && normalizeWord(tokens[index]) in FILLER_WORDS) index++
        }

        var name = tokens.drop(index).joinToString(" ")

        // 3. Quantity at the end: "leche x2", "leche (2)". Only if there was none at the start.
        if (quantity == null) {
            TRAILING_QUANTITY.find(name)?.let { match ->
                quantity = match.groupValues[1].toIntOrNull()
                name = name.removeRange(match.range).trim()
            }
        }

        name = name.trim(*TRAILING_PUNCTUATION).trim()
        if (name.isBlank() || !HAS_LETTER.containsMatchIn(name)) return null

        return ParsedProduct(
            name = name.take(MAX_NAME_LENGTH).replaceFirstChar { it.uppercase() },
            quantity = (quantity ?: 1).coerceIn(MIN_QUANTITY, MAX_QUANTITY),
            unit = unit ?: "unidad"
        )
    }

    /**
     * Splits a token starting with digits into (number, rest). The rest is null when the token is
     * only the number; the whole thing is null when it does not start with digits.
     */
    private fun splitNumericToken(token: String): Pair<Int, String?>? {
        val match = NUMERIC_TOKEN.find(token) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        val suffix = match.groupValues[2].takeIf { it.isNotBlank() }
        return value to suffix
    }

    /** The canonical unit Firestore stores, from any of its synonyms. */
    private fun canonicalUnit(token: String): String? =
        UNIT_SYNONYMS[normalizeWord(token).trimEnd('.')]

    /** Lowercased, unaccented and stripped of trailing punctuation, for comparing single words. */
    private fun normalizeWord(token: String): String =
        ProductNameNormalizer.normalize(token).trim('.', ',', ';', ':', '(', ')')

    private const val MAX_ENTRY_LENGTH = 60
    private const val MAX_NAME_LENGTH = 40
    private const val MIN_QUANTITY = 1
    private const val MAX_QUANTITY = 999

    private val WHITESPACE = Regex("\\s+")
    private val HAS_LETTER = Regex("\\p{L}")
    private val URL = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)
    // Braces and brackets escaped: the ICU engine on Android 16+ rejects a bare one.
    private val BULLET_PREFIX = Regex("^\\s*(?:[-*•·–—>]+|\\d{1,2}[.)]|\\[[ xX]?\\])\\s*")
    private val NUMERIC_TOKEN = Regex("^(\\d{1,3})\\s*([\\p{L}]*)$")
    private val TRAILING_QUANTITY = Regex("\\s*[x×(]\\s*(\\d{1,3})\\s*\\)?$", RegexOption.IGNORE_CASE)
    private val LINE_SEPARATORS = Regex("[,;\\n]")
    private val SPEECH_SEPARATORS = Regex("[,;\\n]|\\by\\b|\\be\\b|\\band\\b", RegexOption.IGNORE_CASE)

    private val TRAILING_PUNCTUATION = charArrayOf(
        ' ', '.', ',', ';', ':', '-', '–', '—', '·', '*', '"', '\''
    )

    /** Filler words between quantity and product: "2 kg **de** tomates". */
    private val FILLER_WORDS = setOf("de", "del", "of")

    private val NUMBER_WORDS = mapOf(
        "un" to 1, "una" to 1, "uno" to 1, "one" to 1,
        "dos" to 2, "two" to 2, "par" to 2, "couple" to 2,
        "tres" to 3, "three" to 3,
        "cuatro" to 4, "four" to 4,
        "cinco" to 5, "five" to 5,
        "seis" to 6, "six" to 6,
        "siete" to 7, "seven" to 7,
        "ocho" to 8, "eight" to 8,
        "nueve" to 9, "nine" to 9,
        "diez" to 10, "ten" to 10,
        "once" to 11, "eleven" to 11,
        "doce" to 12, "twelve" to 12
    )

    /**
     * Unit synonyms mapped to the canonical unit. Only the seven the app handles: a made-up unit
     * ("botellas") stays part of the name instead of slipping into the field.
     */
    private val UNIT_SYNONYMS = mapOf(
        "kg" to "kg", "kgs" to "kg", "kilo" to "kg", "kilos" to "kg",
        "kilogramo" to "kg", "kilogramos" to "kg", "kilogram" to "kg", "kilograms" to "kg",
        "g" to "g", "gr" to "g", "grs" to "g", "gramo" to "g", "gramos" to "g",
        "gram" to "g", "grams" to "g",
        "l" to "L", "lt" to "L", "lts" to "L", "litro" to "L", "litros" to "L",
        "liter" to "L", "liters" to "L", "litre" to "L", "litres" to "L",
        "ml" to "ml", "mililitro" to "ml", "mililitros" to "ml",
        "milliliter" to "ml", "milliliters" to "ml",
        "docena" to "docena", "docenas" to "docena", "dozen" to "docena", "dozens" to "docena",
        "paquete" to "paquete", "paquetes" to "paquete", "pack" to "paquete", "packs" to "paquete",
        "packet" to "paquete", "packets" to "paquete",
        "unidad" to "unidad", "unidades" to "unidad", "ud" to "unidad", "uds" to "unidad",
        "unit" to "unidad", "units" to "unidad"
    )
}
