package com.monsteraltech.habitly.feature.aiassistant.domain.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.shopping.presentation.add.UNITS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // "semanal" degrada a DAILY a propósito: el esquema no lleva campo de días y una rutina
        // semanal sin días no vencería nunca (ver buildSuggestion en ParseAiRoutinesUseCase).
        // Si algún día el esquema incorpora "days", esta aserción debe pasar a WEEKLY.
        assertEquals(RoutineFrequency.DAILY, parsed["semanal"])
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

    // ---------- El esquema exige lo que el parser necesita ----------

    @Test
    fun `routines schema requires title and frequency`() {
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
