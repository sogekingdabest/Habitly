package com.monsteraltech.habitly.feature.shopping.domain.util

/**
 * Un producto reconocido en un texto plano, con lo justo para dar de alta una fila de la lista.
 * La unidad por defecto es la misma que guarda Firestore (`"unidad"`, ver `DEFAULT_UNIT`).
 */
data class ParsedProduct(
    val name: String,
    val quantity: Int = 1,
    val unit: String = "unidad"
)

/**
 * Lector de listas escritas "a lo humano", sin IA: una línea por producto (texto compartido) o
 * una frase dictada ("leche, huevos y pan").
 *
 * Es el **camino de respaldo** de dos funciones:
 *  - "Compartir con Habitly" cuando el modelo local no está descargado: sin esto, el usuario
 *    llega a un callejón sin salida.
 *  - El dictado por voz, donde vale más una respuesta instantánea y predecible que una
 *    inferencia de varios segundos con un modelo de gigabytes para tres palabras.
 *
 * Funciones puras (sin Android) para poder testearlas con JUnit. Reconoce cantidad y unidad en
 * las formas habituales en español e inglés: `2 kg de tomates`, `500 g harina`,
 * `dos litros de leche`, `leche x2`, `3 huevos`. Lo que no entiende lo deja como nombre, que
 * es el fallo barato: el usuario ve la propuesta y la corrige.
 */
object PlainListParser {

    /** Tope de productos que se sacan de un texto compartido (una receta larga no da más). */
    const val MAX_ITEMS = 40

    /** Tope de productos de una frase dictada: nadie dicta veinte cosas de una vez. */
    const val MAX_SPOKEN_ITEMS = 20

    /**
     * Una línea = un producto, para el texto que llega por "Compartir". Se parte también por
     * comas: una lista enviada por WhatsApp suele venir en una sola línea ("leche, huevos, pan").
     * Por "y" no se parte aquí, que en un texto largo aparece dentro de frases normales.
     */
    fun fromLines(text: String, limit: Int = MAX_ITEMS): List<ParsedProduct> =
        parseEntries(LINE_SEPARATORS.split(text).asSequence(), limit)

    /**
     * Una frase dictada, separada por comas, punto y coma y las conjunciones "y"/"e"/"and".
     * "Leche, huevos y pan" son tres productos, no uno.
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

    /** Convierte una línea/fragmento en producto, o null si no parece un producto. */
    fun parseEntry(raw: String): ParsedProduct? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        text = text.replace(BULLET_PREFIX, "").trim()
        if (text.endsWith(':')) return null
        text = text.trim(*TRAILING_PUNCTUATION)
        if (text.isBlank()) return null

        if (text.length > MAX_ENTRY_LENGTH) return null
        if (!HAS_LETTER.containsMatchIn(text)) return null
        if (URL.containsMatchIn(text)) return null

        val tokens = text.split(WHITESPACE).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var index = 0
        var quantity: Int? = null
        var unit: String? = null

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
                else -> Unit
            }
        } else {
            NUMBER_WORDS[normalizeWord(tokens[0])]?.let { value ->
                quantity = value
                index = 1
            }
        }

        if (quantity != null) {
            if (unit == null && index < tokens.size) {
                if (tokens[index].equals("x", ignoreCase = true)) index++
                else canonicalUnit(tokens[index])?.let { unit = it; index++ }
            }
            if (index < tokens.size && normalizeWord(tokens[index]) in FILLER_WORDS) index++
        }

        var name = tokens.drop(index).joinToString(" ")

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
     * Parte un token que empieza por dígitos en (número, resto). El resto es null cuando el
     * token es solo el número. Devuelve null si no empieza por dígitos.
     */
    private fun splitNumericToken(token: String): Pair<Int, String?>? {
        val match = NUMERIC_TOKEN.find(token) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        val suffix = match.groupValues[2].takeIf { it.isNotBlank() }
        return value to suffix
    }

    /** Unidad canónica (la que guarda Firestore) a partir de cualquiera de sus sinónimos. */
    private fun canonicalUnit(token: String): String? =
        UNIT_SYNONYMS[normalizeWord(token).trimEnd('.')]

    /** Minúsculas, sin tildes y sin puntuación pegada, para comparar palabras sueltas. */
    private fun normalizeWord(token: String): String =
        ProductNameNormalizer.normalize(token).trim('.', ',', ';', ':', '(', ')')

    private const val MAX_ENTRY_LENGTH = 60
    private const val MAX_NAME_LENGTH = 40
    private const val MIN_QUANTITY = 1
    private const val MAX_QUANTITY = 999

    private val WHITESPACE = Regex("\\s+")
    private val HAS_LETTER = Regex("\\p{L}")
    private val URL = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)
    // Llaves y corchetes escapados: el motor ICU de Android 16+ rechaza uno suelto.
    private val BULLET_PREFIX = Regex("^\\s*(?:[-*•·–—>]+|\\d{1,2}[.)]|\\[[ xX]?\\])\\s*")
    private val NUMERIC_TOKEN = Regex("^(\\d{1,3})\\s*([\\p{L}]*)$")
    private val TRAILING_QUANTITY = Regex("\\s*[x×(]\\s*(\\d{1,3})\\s*\\)?$", RegexOption.IGNORE_CASE)
    private val LINE_SEPARATORS = Regex("[,;\\n]")
    private val SPEECH_SEPARATORS = Regex("[,;\\n]|\\by\\b|\\be\\b|\\band\\b", RegexOption.IGNORE_CASE)

    private val TRAILING_PUNCTUATION = charArrayOf(
        ' ', '.', ',', ';', ':', '-', '–', '—', '·', '*', '"', '\''
    )

    /** Palabras de relleno entre la cantidad y el producto: "2 kg **de** tomates". */
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
     * Sinónimos de unidad → unidad canónica. Solo las siete que maneja la app: una unidad
     * inventada ("botellas") se queda formando parte del nombre en vez de colarse en el campo.
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
