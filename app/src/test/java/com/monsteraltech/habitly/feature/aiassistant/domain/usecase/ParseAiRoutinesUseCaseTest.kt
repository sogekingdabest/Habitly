package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ParseAiRoutinesUseCaseTest {

    private val useCase = ParseAiRoutinesUseCase()

    // ---------- Casos que no deben parsear ----------

    @Test
    fun `blank text returns empty`() {
        assertTrue(useCase("").isEmpty())
    }

    @Test
    fun `plain answer without block returns empty`() {
        val text = "Te propongo fregar la cocina y sacar la basura cada semana."

        assertTrue(useCase(text).isEmpty())
    }

    @Test
    fun `shopping block is not mistaken for routines`() {
        // El bloque de la compra también usa "name": sin marcador ni clave propia,
        // el parser de rutinas debe ignorarlo.
        val text = """
            Aquí tienes la lista.
            @@LISTA@@ {"shopping_list":[{"name":"Tomate","quantity":6,"unit":"unidad"}]}
        """.trimIndent()

        assertTrue(useCase(text).isEmpty())
    }

    // ---------- Parseo básico ----------

    @Test
    fun `parses a well formed block`() {
        val text = """
            Te propongo estas rutinas.
            @@RUTINA@@ {"routines":[{"title":"Fregar la cocina","description":"Después de cenar","frequency":"semanal","days":["lunes","jueves"]}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Fregar la cocina", result[0].title)
        assertEquals("Después de cenar", result[0].description)
        assertEquals(RoutineFrequency.WEEKLY, result[0].frequency)
        assertEquals(listOf(Calendar.MONDAY, Calendar.THURSDAY), result[0].scheduledDays)
    }

    @Test
    fun `parses several routines`() {
        val text = """
            @@RUTINA@@ {"routines":[
              {"title":"Barrer","frequency":"diaria"},
              {"title":"Cambiar sabanas","frequency":"cada_n_dias","interval_days":10}
            ]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(2, result.size)
        assertEquals(RoutineFrequency.DAILY, result[0].frequency)
        assertEquals(RoutineFrequency.EVERY_N_DAYS, result[1].frequency)
        assertEquals(10, result[1].intervalDays)
    }

    @Test
    fun `parses a fenced json block`() {
        val text = """
            Aquí van:
            ```json
            {"routines":[{"title":"Regar plantas","frequency":"semanal","days":["domingo"]}]}
            ```
        """.trimIndent()

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals(listOf(Calendar.SUNDAY), result[0].scheduledDays)
    }

    @Test
    fun `falls back to regex when json is malformed`() {
        // Falta una llave de cierre: Gson no puede con ello.
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Fregar","frequency":"diaria"},
        """.trimIndent()

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Fregar", result[0].title)
    }

    // ---------- Tolerancias ----------

    @Test
    fun `accepts spanish field names`() {
        val text = """
            @@RUTINA@@ {"rutinas":[{"titulo":"Barrer","descripcion":"El salón","frecuencia":"semanal","dias":["martes"]}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Barrer", result[0].title)
        assertEquals("El salón", result[0].description)
        assertEquals(listOf(Calendar.TUESDAY), result[0].scheduledDays)
    }

    @Test
    fun `accepts english day names`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Gym","frequency":"weekly","days":["monday","friday"]}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(listOf(Calendar.MONDAY, Calendar.FRIDAY), result[0].scheduledDays)
    }

    @Test
    fun `accepts days as a comma separated string`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Barrer","frequency":"semanal","days":"lunes, miercoles"}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(listOf(Calendar.MONDAY, Calendar.WEDNESDAY), result[0].scheduledDays)
    }

    @Test
    fun `day names ignore accents and case`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Barrer","frequency":"semanal","days":["Miércoles","SÁBADO"]}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(listOf(Calendar.WEDNESDAY, Calendar.SATURDAY), result[0].scheduledDays)
    }

    @Test
    fun `unknown frequency falls back to daily`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Barrer","frequency":"cuando me acuerde"}]}
        """.trimIndent()

        assertEquals(RoutineFrequency.DAILY, useCase(text)[0].frequency)
    }

    @Test
    fun `weekly without valid days is downgraded to daily`() {
        // Una semanal sin días no llegaría a tocar nunca.
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Barrer","frequency":"semanal","days":["fooday"]}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(RoutineFrequency.DAILY, result[0].frequency)
        assertTrue(result[0].scheduledDays.isEmpty())
    }

    @Test
    fun `interval routine without interval gets a default`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Cambiar sabanas","frequency":"cada_n_dias"}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(RoutineFrequency.EVERY_N_DAYS, result[0].frequency)
        assertTrue((result[0].intervalDays ?: 0) >= 1)
    }

    @Test
    fun `interval is only kept for interval routines`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Barrer","frequency":"diaria","interval_days":10}]}
        """.trimIndent()

        assertNull(useCase(text)[0].intervalDays)
    }

    @Test
    fun `textual interval takes the first number`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Cambiar sabanas","frequency":"cada_n_dias","interval_days":"cada 14 dias"}]}
        """.trimIndent()

        assertEquals(14, useCase(text)[0].intervalDays)
    }

    @Test
    fun `routines without title are skipped`() {
        val text = """
            @@RUTINA@@ {"routines":[{"frequency":"diaria"},{"title":"Barrer","frequency":"diaria"}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Barrer", result[0].title)
    }

    @Test
    fun `duplicate titles are collapsed`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Barrer"},{"title":"barrer"},{"title":"BARRER"}]}
        """.trimIndent()

        assertEquals(1, useCase(text).size)
    }

    @Test
    fun `the number of routines is capped`() {
        val many = (1..30).joinToString(",") { """{"title":"Rutina $it"}""" }
        val text = "@@RUTINA@@ {\"routines\":[$many]}"

        assertTrue(useCase(text).size <= 10)
    }

    @Test
    fun `both blocks in one answer are parsed independently`() {
        val text = """
            Aquí tienes todo.
            @@RUTINA@@ {"routines":[{"title":"Fregar","frequency":"diaria"}]}
            @@LISTA@@ {"shopping_list":[{"name":"Lejía","quantity":1}]}
        """.trimIndent()

        val routines = useCase(text)
        val shopping = ParseAiShoppingListUseCase()(text)

        assertEquals(1, routines.size)
        assertEquals("Fregar", routines[0].title)
        assertEquals(1, shopping.size)
        assertEquals("Lejía", shopping[0].name)
    }
}
