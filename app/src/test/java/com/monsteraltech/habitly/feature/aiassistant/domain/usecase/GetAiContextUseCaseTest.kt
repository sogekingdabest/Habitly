package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakePantryRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeShoppingRepository
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class GetAiContextUseCaseTest {

    private val fakeAuthRepo = FakeAuthRepository()
    private val fakeHouseholdRepo = FakeHouseholdRepository()
    private val fakeRoutinesRepo = FakeRoutinesRepository()
    private val fakeShoppingRepo = FakeShoppingRepository()
    private val fakePantryRepo = FakePantryRepository()

    private lateinit var useCase: GetAiContextUseCase

    /** 2026-07-13 es lunes. Fecha fija para que el contexto sea determinista. */
    private val monday = LocalDate.of(2026, 7, 13)

    @Before
    fun setUp() {
        useCase = GetAiContextUseCase(
            authRepository = fakeAuthRepo,
            householdRepository = fakeHouseholdRepo,
            routinesRepository = fakeRoutinesRepo,
            shoppingRepository = fakeShoppingRepo,
            pantryRepository = fakePantryRepo
        )
    }

    @After
    fun tearDown() {
        fakeAuthRepo.reset()
        fakeHouseholdRepo.reset()
        fakeRoutinesRepo.reset()
        fakeShoppingRepo.reset()
        fakePantryRepo.reset()
    }

    private fun testUser(uid: String = "user1") = AuthUser(
        uid = uid,
        email = "test@test.com",
        displayName = "Test User",
        isEmailVerified = true,
        authToken = AuthToken("fake", "fake")
    )

    private fun givenLoggedInWithHousehold() {
        fakeAuthRepo.stubCurrentUser = testUser()
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "house1")
    }

    private fun epochOf(date: LocalDate): Long =
        date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ---------- Sin contexto ----------

    @Test
    fun `when no user is logged in, returns base personality only`() = runBlocking {
        val result = useCase()

        assertTrue(result.contains("Eres Habitly"))
        assertFalse(result.contains("Contexto Oculto"))
    }

    @Test
    fun `when user has no household, returns base personality only`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = testUser()

        val result = useCase()

        assertTrue(result.contains("Eres Habitly"))
        assertFalse(result.contains("Contexto Oculto"))
    }

    @Test
    fun `when household id is blank, returns base personality only`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = testUser()
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "")

        val result = useCase()

        assertFalse(result.contains("Contexto Oculto"))
    }

    @Test
    fun `when user has household but no data, returns empty context`() = runBlocking {
        givenLoggedInWithHousehold()

        val result = useCase()

        assertTrue(result.contains("Contexto Oculto"))
        assertTrue(result.contains("lista de la compra está vacía"))
        assertTrue(result.contains("No tienes rutinas asignadas"))
        assertTrue(result.contains("La casa no tiene rutinas compartidas"))
    }

    @Test
    fun `when timeout is very short, falls back to base personality`() = runBlocking {
        givenLoggedInWithHousehold()

        val result = useCase(timeoutMs = 1)

        assertTrue(result.contains("Eres Habitly"))
    }

    @Test
    fun `base personality includes Habitly name and role`() = runBlocking {
        val result = useCase()

        assertTrue(result.contains("Habitly"))
        assertTrue(result.contains("gestión del hogar"))
    }

    // ---------- Fecha ----------

    @Test
    fun `context includes the current date in spanish`() = runBlocking {
        givenLoggedInWithHousehold()

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Hoy es lunes, 13 de julio de 2026."))
    }

    // ---------- Lista de la compra ----------

    @Test
    fun `when user has shopping items, includes them in context`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = listOf(
            ShoppingItem(name = "Leche", isChecked = false),
            ShoppingItem(name = "Pan", isChecked = true)
        )

        val result = useCase(today = monday)

        assertTrue(result.contains("Leche"))
        assertTrue(result.contains("Pan"))
        assertTrue(result.contains("(pendiente)"))
        assertTrue(result.contains("(comprado)"))
    }

    @Test
    fun `shopping line includes quantity, category and store when they are not defaults`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = listOf(
            ShoppingItem(
                name = "Tomate",
                quantity = 6,
                unit = "kg",
                category = "Frutas y Verduras",
                store = "Mercadona"
            )
        )

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Tomate (6 kg) [Frutas y Verduras] [Mercadona] (pendiente)"))
    }

    @Test
    fun `shopping line omits defaults to save tokens`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = listOf(ShoppingItem(name = "Leche"))

        val result = useCase(today = monday)

        assertTrue(result, result.contains("- Leche (pendiente)"))
        assertFalse(result.contains("Cualquiera"))
        assertFalse(result.contains("1 unidad"))
    }

    @Test
    fun `shopping context reports pending and checked counts`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = listOf(
            ShoppingItem(name = "Leche", isChecked = false),
            ShoppingItem(name = "Pan", isChecked = true),
            ShoppingItem(name = "Huevos", isChecked = false)
        )

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Lista de la compra (2 pendientes, 1 comprados)"))
    }

    @Test
    fun `shopping list is capped and reports how many were omitted`() = runBlocking {
        givenLoggedInWithHousehold()
        val total = GetAiContextUseCase.MAX_SHOPPING_ITEMS + 5
        fakeShoppingRepo.stubItems = (1..total).map { ShoppingItem(name = "Producto $it") }

        val result = useCase(today = monday)

        assertTrue(result.contains("Producto 1"))
        assertFalse("no debe volcar más allá del tope", result.contains("Producto $total"))
        assertTrue(result, result.contains("… y 5 productos más."))
    }

    // ---------- Despensa ----------

    @Test
    fun `empty pantry is stated explicitly`() = runBlocking {
        givenLoggedInWithHousehold()

        val result = useCase(today = monday)

        assertTrue(result, result.contains("La despensa está vacía"))
    }

    @Test
    fun `pantry items are included with their quantity`() = runBlocking {
        givenLoggedInWithHousehold()
        fakePantryRepo.stubItems = listOf(
            PantryItem(id = "arroz", name = "Arroz", quantity = 2, unit = "kg"),
            PantryItem(id = "huevo", name = "Huevo", quantity = 6)
        )

        val result = useCase(today = monday)

        assertTrue(result.contains("Despensa (lo que YA hay en casa):"))
        assertTrue(result, result.contains("- Arroz (2 kg)"))
        assertTrue(result, result.contains("- Huevo (6 unidad)"))
    }

    @Test
    fun `pantry is capped and reports how many were omitted`() = runBlocking {
        givenLoggedInWithHousehold()
        val total = GetAiContextUseCase.MAX_PANTRY_ITEMS + 4
        fakePantryRepo.stubItems = (1..total).map {
            PantryItem(id = "p$it", name = "Producto $it")
        }

        val result = useCase(today = monday)

        assertTrue(result, result.contains("… y 4 productos más."))
    }

    @Test
    fun `personality tells the model to only ask for missing ingredients`() = runBlocking {
        val result = useCase()

        assertTrue(result.contains("SOLO los ingredientes que FALTEN"))
    }

    // ---------- Rutinas ----------

    @Test
    fun `when user has routines, includes them in context`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(
            Routine(title = "Limpiar cocina", lastCompletedAt = null),
            Routine(title = "Sacar basura", lastCompletedAt = epochOf(monday))
        )

        val result = useCase(today = monday)

        assertTrue(result.contains("Tus rutinas personales:"))
        assertTrue(result, result.contains("Limpiar cocina (pendiente, hoy toca)"))
        assertTrue(result, result.contains("Sacar basura (marcada, hoy toca)"))
    }

    @Test
    fun `routine completed on another day counts as pending today`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(
            Routine(title = "Sacar basura", lastCompletedAt = epochOf(monday.minusDays(3)))
        )

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Sacar basura (pendiente"))
    }

    @Test
    fun `routine not scheduled today is marked as such`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(
            Routine(
                title = "Regar plantas",
                frequency = RoutineFrequency.WEEKLY,
                scheduledDays = listOf(Calendar.SUNDAY)
            )
        )

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Regar plantas (pendiente, hoy no toca)"))
    }

    @Test
    fun `streak is included when it is worth mentioning`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(
            Routine(title = "Gimnasio", currentStreak = 5),
            Routine(title = "Leer", currentStreak = 1)
        )

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Gimnasio (pendiente, hoy toca, racha de 5 días)"))
        assertTrue(result, result.contains("Leer (pendiente, hoy toca)"))
    }

    @Test
    fun `household routines are included in their own section`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubHouseholdRoutines = listOf(
            Routine(title = "Fregar el suelo", lastCompletedAt = null)
        )

        val result = useCase(today = monday)

        assertTrue(result.contains("Rutinas compartidas de la casa:"))
        assertTrue(result, result.contains("Fregar el suelo (pendiente, hoy toca)"))
    }

    @Test
    fun `household routine distinguishes who completed it`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubHouseholdRoutines = listOf(
            Routine(
                title = "Sacar basura",
                lastCompletedAt = epochOf(monday),
                lastCompletedBy = "user1"
            ),
            Routine(
                title = "Poner lavadora",
                lastCompletedAt = epochOf(monday),
                lastCompletedBy = "otro-usuario"
            )
        )

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Sacar basura (marcada por ti"))
        assertTrue(result, result.contains("Poner lavadora (marcada por otro miembro"))
    }

    @Test
    fun `routines due today survive the cap`() = runBlocking {
        givenLoggedInWithHousehold()
        val notToday = (1..GetAiContextUseCase.MAX_ROUTINES).map {
            Routine(
                title = "No toca $it",
                frequency = RoutineFrequency.WEEKLY,
                scheduledDays = listOf(Calendar.SUNDAY)
            )
        }
        fakeRoutinesRepo.stubRoutines = notToday + Routine(title = "Sí toca hoy")

        val result = useCase(today = monday)

        assertTrue(result, result.contains("Sí toca hoy"))
        assertTrue(result, result.contains("rutinas más."))
    }
}
