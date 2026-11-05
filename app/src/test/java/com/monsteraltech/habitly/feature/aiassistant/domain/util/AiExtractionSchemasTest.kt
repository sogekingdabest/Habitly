package com.monsteraltech.habitly.feature.aiassistant.domain.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.shopping.presentation.add.CATEGORIES
import com.monsteraltech.habitly.feature.shopping.presentation.add.UNITS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the contract between the schemas the engine constrains generation to and the parsers that
 * read the result.
 *
 * A schema only earns its keep if everything it allows is something the app understands, so these
 * tests walk the enums the schema can produce and feed each value through the real parser. A typo
 * in the JSON matters just as much: `ResponseFormat.json()` rejects an invalid schema by throwing,
 * and the extractor would quietly fall back to unconstrained generation for every user, with
 * nothing but a log line to show for it.
 */
class AiExtractionSchemasTest {

    private val routines = ParseAiRoutinesUseCase()
    private val shopping = ParseAiShoppingListUseCase()

    // ---------- Los esquemas son JSON válido ----------

    @Test
    fun `routines schema is valid json`() {
        assertTrue(JsonParser.parseString(AiExtractionSchemas.ROUTINES).isJsonObject)
    }

    @Test
    fun `shopping schema is valid json`() {
        assertTrue(JsonParser.parseString(AiExtractionSchemas.SHOPPING).isJsonObject)
    }

    // ---------- Lo que el esquema permite, el parser lo entiende ----------

    @Test
    fun `every frequency the routines schema allows is understood by the parser`() {
        val allowed = enumValues(AiExtractionSchemas.ROUTINES, "routines", "frequency")
        assertEquals(listOf("diaria", "semanal", "cada_n_dias"), allowed)

        val parsed = allowed.associateWith { value ->
            val json = """{"routines":[{"title":"Fregar la cocina","frequency":"$value"}]}"""
            routines(json).single().frequency
        }

        assertEquals(RoutineFrequency.DAILY, parsed["diaria"])
        assertEquals(RoutineFrequency.EVERY_N_DAYS, parsed["cada_n_dias"])
        // "semanal" SIN días sigue degradando a DAILY a propósito, y seguirá: "days" es opcional en
        // el esquema (lo que haga falta depende de la frecuencia, y eso no se puede expresar), así
        // que esta sigue siendo una salida posible del modelo. Una semanal sin días no vencería
        // nunca — ver buildSuggestion en ParseAiRoutinesUseCase.
        assertEquals(RoutineFrequency.DAILY, parsed["semanal"])
    }

    @Test
    fun `una rutina semanal con dias ya no degrada a diaria`() {
        val days = dayNames()
        assertEquals(7, days.size)

        val parsed = days.map { day ->
            val json = """{"routines":[{"title":"Barrer","frequency":"semanal","days":["$day"]}]}"""
            routines(json).single()
        }

        assertTrue(parsed.all { it.frequency == RoutineFrequency.WEEKLY })
        // Cada nombre del enum tiene que caer en un día distinto. El parser reconoce los días por
        // prefijo de tres letras, así que una errata que el enum permita ("miercole") dejaría la
        // rutina sin días, de vuelta a diaria, y el esquema no se enteraría.
        assertEquals(7, parsed.flatMap { it.scheduledDays }.distinct().size)
    }

    @Test
    fun `cada_n_dias ya puede decir cada cuantos dias`() {
        val json = """{"routines":[{"title":"Riego","frequency":"cada_n_dias","interval_days":5}]}"""

        val parsed = routines(json).single()

        assertEquals(RoutineFrequency.EVERY_N_DAYS, parsed.frequency)
        assertEquals(5, parsed.intervalDays)
    }

    @Test
    fun `el intervalo lo acota el parser, no el esquema`() {
        val interval = itemSchema(JsonParser.parseString(AiExtractionSchemas.ROUTINES).asJsonObject)
            .getAsJsonObject("properties")
            .getAsJsonObject("interval_days")

        // Un entero a secas: los límites serían gramática de más compilada en el dispositivo para
        // garantizar lo que el parser ya garantiza. Esta es la mitad que de verdad acota.
        assertEquals("integer", interval.get("type").asString)
        assertNull(interval.get("minimum"))

        val json = """{"routines":[{"title":"Riego","frequency":"cada_n_dias","interval_days":99999}]}"""

        assertEquals(365, routines(json).single().intervalDays)
    }

    @Test
    fun `shopping schema units match the units the app offers`() {
        val allowed = enumValues(AiExtractionSchemas.SHOPPING, "shopping_list", "unit")

        assertEquals(UNITS, allowed)
    }

    @Test
    fun `every unit the shopping schema allows survives the parser`() {
        for (unit in enumValues(AiExtractionSchemas.SHOPPING, "shopping_list", "unit")) {
            val json = """{"shopping_list":[{"name":"Arroz","quantity":2,"unit":"$unit","category":"Otros"}]}"""
            val item = shopping(json).single()

            assertEquals(unit, item.unit)
            assertEquals(2, item.quantity)
        }
    }

    @Test
    fun `shopping schema categories are the keys the app knows how to resolve`() {
        val allowed = enumValues(AiExtractionSchemas.SHOPPING, "shopping_list", "category")

        assertEquals(AiShoppingCategories.BY_KEY.keys.toList(), allowed)
    }

    @Test
    fun `the schema keys are single ascii words so the engine can build its grammar`() {
        // La razón está en AiExtractionSchemas: un enum con las categorías tal y como las lee el
        // usuario mata la generación en el primer valor con un espacio. Esto es lo que queda de
        // aquel fallo, para que nadie «mejore» el enum devolviéndole los nombres bonitos.
        for (key in enumValues(AiExtractionSchemas.SHOPPING, "shopping_list", "category")) {
            assertTrue(key, key.matches(Regex("[a-z]+")))
        }
    }

    @Test
    fun `every category the schema allows ends up being one the app offers`() {
        for (key in enumValues(AiExtractionSchemas.SHOPPING, "shopping_list", "category")) {
            val json = """{"shopping_list":[{"name":"Arroz","quantity":1,"unit":"kg","category":"$key"}]}"""

            val resolved = AiShoppingCategories.resolve(shopping(json).single().category)

            assertTrue(resolved, CATEGORIES.contains(resolved))
        }
    }

    @Test
    fun `the shopping categories leave a way out when nothing fits`() {
        // Cerrar el enum solo es seguro por esto. Sin una salida, obligar a elegir de un
        // vocabulario cerrado convertiría «casi siempre mal» en «mal garantizado».
        assertTrue(enumValues(AiExtractionSchemas.SHOPPING, "shopping_list", "category").contains("otros"))
    }

    // ---------- El esquema exige lo que el parser necesita ----------

    @Test
    fun `routines schema requires title and frequency`() {
        // Y solo esos dos: "days" e "interval_days" son opcionales porque cuál hace falta depende
        // de la frecuencia. Exigirlos obligaría a una rutina diaria a inventarse días.
        assertEquals(listOf("title", "frequency"), requiredFields(AiExtractionSchemas.ROUTINES, "routines"))
    }

    @Test
    fun `shopping schema requires every field the product needs`() {
        assertEquals(
            listOf("name", "quantity", "unit", "category"),
            requiredFields(AiExtractionSchemas.SHOPPING, "shopping_list")
        )
    }

    @Test
    fun `neither schema lets the model add keys of its own`() {
        for (schema in listOf(AiExtractionSchemas.ROUTINES, AiExtractionSchemas.SHOPPING)) {
            val root = JsonParser.parseString(schema).asJsonObject
            assertFalse(root.get("additionalProperties").asBoolean)
            assertFalse(itemSchema(root).get("additionalProperties").asBoolean)
        }
    }

    @Test
    fun `shopping quantity is an integer so the parser never sees 2 kg in the number field`() {
        val root = JsonParser.parseString(AiExtractionSchemas.SHOPPING).asJsonObject
        val quantity = itemSchema(root).getAsJsonObject("properties").getAsJsonObject("quantity")

        assertEquals("integer", quantity.get("type").asString)
    }

    // ---------- Ayudas ----------

    private fun itemSchema(root: JsonObject): JsonObject =
        root.getAsJsonObject("properties")
            .entrySet()
            .single()
            .value.asJsonObject
            .getAsJsonObject("items")

    /** The day names the routines schema allows: one level deeper, in `days` → `items` → `enum`. */
    private fun dayNames(): List<String> =
        JsonParser.parseString(AiExtractionSchemas.ROUTINES).asJsonObject
            .getAsJsonObject("properties")
            .getAsJsonObject("routines")
            .getAsJsonObject("items")
            .getAsJsonObject("properties")
            .getAsJsonObject("days")
            .getAsJsonObject("items")
            .getAsJsonArray("enum")
            .map { it.asString }

    private fun enumValues(schema: String, arrayKey: String, field: String): List<String> =
        JsonParser.parseString(schema).asJsonObject
            .getAsJsonObject("properties")
            .getAsJsonObject(arrayKey)
            .getAsJsonObject("items")
            .getAsJsonObject("properties")
            .getAsJsonObject(field)
            .getAsJsonArray("enum")
            .map { it.asString }

    private fun requiredFields(schema: String, arrayKey: String): List<String> =
        JsonParser.parseString(schema).asJsonObject
            .getAsJsonObject("properties")
            .getAsJsonObject(arrayKey)
            .getAsJsonObject("items")
            .getAsJsonArray("required")
            .map { it.asString }
}
