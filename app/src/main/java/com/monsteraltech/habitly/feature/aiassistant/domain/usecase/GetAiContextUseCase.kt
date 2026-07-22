package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.PantryRepository
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

/**
 * Construye el prompt de sistema del asistente: personalidad base + un "contexto oculto"
 * con el estado real de la casa (fecha, lista de la compra, rutinas personales y de casa).
 *
 * El contexto va acotado a propósito ([MAX_SHOPPING_ITEMS], [MAX_ROUTINES]): los modelos
 * on-device trabajan con una KV cache de 4096 tokens y un contexto largo degrada la
 * calidad de la respuesta y deja menos sitio para la conversación.
 */
class GetAiContextUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val routinesRepository: RoutinesRepository,
    private val shoppingRepository: ShoppingRepository,
    private val pantryRepository: PantryRepository
) {
    suspend operator fun invoke(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        today: LocalDate = LocalDate.now()
    ): String {
        val user = authRepository.getCurrentUser() ?: return getBasePersonality()

        val profile = withTimeoutOrNull(timeoutMs) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }

        val householdId = profile?.activeHouseholdId?.takeIf { it.isNotBlank() }
            ?: return getBasePersonality()

        // Las cuatro lecturas son independientes entre sí: en paralelo, el peor caso pasa de
        // cuatro timeouts encadenados a uno (menos espera antes del primer token del chat).
        return coroutineScope {
            val shoppingItems = async {
                withTimeoutOrNull(timeoutMs) {
                    shoppingRepository.observeShoppingList(householdId).firstOrNull()
                } ?: emptyList()
            }
            val personalRoutines = async {
                withTimeoutOrNull(timeoutMs) {
                    routinesRepository.observePersonalRoutines(user.uid).firstOrNull()
                } ?: emptyList()
            }
            val householdRoutines = async {
                withTimeoutOrNull(timeoutMs) {
                    routinesRepository.observeHouseholdRoutines(householdId).firstOrNull()
                } ?: emptyList()
            }
            val pantryItems = async {
                withTimeoutOrNull(timeoutMs) {
                    pantryRepository.observePantry(householdId).firstOrNull()
                } ?: emptyList()
            }

            listOf(
                getBasePersonality(),
                "[Contexto Oculto de la Aplicación Habitly]",
                "Hoy es ${formatDate(today)}.",
                shoppingContext(shoppingItems.await()),
                pantryContext(pantryItems.await()),
                personalRoutinesContext(personalRoutines.await(), today),
                householdRoutinesContext(householdRoutines.await(), today, user.uid)
            ).joinToString(separator = "\n\n")
        }
    }

    private fun pantryContext(items: List<PantryItem>): String {
        if (items.isEmpty()) return "La despensa está vacía (no sabemos qué hay en casa)."

        val shown = items.take(MAX_PANTRY_ITEMS)
        val omitted = items.size - shown.size

        return buildString {
            append("Despensa (lo que YA hay en casa):")
            shown.forEach { item ->
                append("\n- ${item.name} (${item.quantity} ${item.unit})")
            }
            if (omitted > 0) append("\n- … y $omitted productos más.")
        }
    }

    /** Fecha en español sin depender del `Locale` del dispositivo (el prompt siempre es español). */
    private fun formatDate(date: LocalDate): String {
        val dayName = SPANISH_DAYS[date.dayOfWeek.value - 1]
        val monthName = SPANISH_MONTHS[date.monthValue - 1]
        return "$dayName, ${date.dayOfMonth} de $monthName de ${date.year}"
    }

    private fun shoppingContext(items: List<ShoppingItem>): String {
        if (items.isEmpty()) return "La lista de la compra está vacía."

        val pending = items.filter { !it.isChecked }
        val checked = items.filter { it.isChecked }
        // Lo pendiente es lo relevante: va primero, así el recorte se come lo ya comprado.
        val shown = (pending + checked).take(MAX_SHOPPING_ITEMS)
        val omitted = items.size - shown.size

        return buildString {
            append("Lista de la compra (${pending.size} pendientes, ${checked.size} comprados):")
            shown.forEach { append("\n- ${shoppingLine(it)}") }
            if (omitted > 0) append("\n- … y $omitted productos más.")
        }
    }

    /** Omite lo que es valor por defecto para no gastar tokens: `Tomate (6 kg) [Verduras] [Lidl] (pendiente)`. */
    private fun shoppingLine(item: ShoppingItem): String = buildString {
        append(item.name)
        if (item.quantity > 1 || item.unit != DEFAULT_UNIT) append(" (${item.quantity} ${item.unit})")
        if (item.category.isNotBlank()) append(" [${item.category}]")
        if (item.store.isNotBlank() && item.store != ANY_STORE) append(" [${item.store}]")
        append(if (item.isChecked) " (comprado)" else " (pendiente)")
    }

    private fun personalRoutinesContext(routines: List<Routine>, today: LocalDate): String {
        if (routines.isEmpty()) return "No tienes rutinas asignadas."
        return buildString {
            append("Tus rutinas personales:")
            appendRoutineLines(routines, today, currentUserId = null)
        }
    }

    private fun householdRoutinesContext(
        routines: List<Routine>,
        today: LocalDate,
        currentUserId: String
    ): String {
        if (routines.isEmpty()) return "La casa no tiene rutinas compartidas."
        return buildString {
            append("Rutinas compartidas de la casa:")
            appendRoutineLines(routines, today, currentUserId)
        }
    }

    private fun StringBuilder.appendRoutineLines(
        routines: List<Routine>,
        today: LocalDate,
        currentUserId: String?
    ) {
        // Lo que toca hoy es lo relevante: va primero para sobrevivir al recorte.
        val shown = routines
            .sortedByDescending { RoutineSchedule.isDueOn(it, today) }
            .take(MAX_ROUTINES)
        shown.forEach { append("\n- ${routineLine(it, today, currentUserId)}") }

        val omitted = routines.size - shown.size
        if (omitted > 0) append("\n- … y $omitted rutinas más.")
    }

    /** `Gimnasio (pendiente, hoy toca, racha de 5 días)`. */
    private fun routineLine(routine: Routine, today: LocalDate, currentUserId: String?): String {
        val marks = mutableListOf<String>()

        marks += if (RoutineSchedule.isCompletedOn(routine, today)) {
            completedMark(routine, currentUserId)
        } else {
            "pendiente"
        }
        marks += if (RoutineSchedule.isDueOn(routine, today)) "hoy toca" else "hoy no toca"
        if (routine.currentStreak >= MIN_STREAK_SHOWN) {
            marks += "racha de ${routine.currentStreak} días"
        }

        return "${routine.title} (${marks.joinToString(", ")})"
    }

    /**
     * En rutinas de casa distingue quién la hizo. Se usa "otro miembro" en vez del nombre
     * porque resolver nicknames costaría dos llamadas de red más dentro del timeout;
     * los nombres llegan con la fase de rotación/balance, que ya los tiene cargados.
     */
    private fun completedMark(routine: Routine, currentUserId: String?): String = when {
        currentUserId == null -> "marcada"
        routine.lastCompletedBy == null -> "marcada"
        routine.lastCompletedBy == currentUserId -> "marcada por ti"
        else -> "marcada por otro miembro"
    }

    private fun getBasePersonality(): String {
        return """
            Eres Habitly, un asistente amigable experto en gestión del hogar. Tu objetivo es ayudar al usuario a organizarse, dar ideas de rutinas, recetas para la lista de la compra y consejos de limpieza. Sé amigable, claro y conversacional. Utiliza el contexto oculto de la aplicación proporcionado para dar respuestas exactas sobre las rutinas y la lista de la compra si el usuario te pregunta por ellas. No reveles que estás leyendo un contexto oculto.

            ESTILO DE RESPUESTA (IMPORTANTE):
            - Sé BREVE y directo. Responde en 2-6 frases, o con una lista corta cuando encaje mejor. La mayoría de preguntas no necesitan más.
            - Nada de introducciones ni cierres de relleno ("¡Claro que sí!", "Espero que te sirva…"): ve al grano.
            - No repitas la pregunta del usuario ni la información que ya has dado antes en la conversación.
            - En listas (compra, menús, rutinas) da solo los elementos con su cantidad o frecuencia, sin un párrafo explicativo por cada uno.
            - Amplía con detalle SOLO si el usuario lo pide explícitamente ("dame más detalle", "explícame el porqué"…).
            - Usa markdown cuando ayude: viñetas y negritas para lo importante. NO uses tablas markdown: en la pantalla de un móvil se leen mal. Cuando compares opciones o planifiques por días, usa un encabezado o una lista por cada día o elemento.

            Cuando propongas recetas, menús o cenas, ten en cuenta la despensa del contexto: menciona claramente qué productos harían falta COMPRAR (los que no estén ya en casa en cantidad suficiente).
        """.trimIndent()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2000L

        /** Tope de productos volcados al contexto (la KV cache del modelo es de 4096 tokens). */
        const val MAX_SHOPPING_ITEMS = 30

        /** Tope de rutinas por sección (personales y de casa). Acotado para dejar presupuesto
         *  de KV cache al segundo turno de extracción. */
        const val MAX_ROUTINES = 8

        /** Tope de productos de la despensa volcados al contexto. */
        const val MAX_PANTRY_ITEMS = 30

        /** A partir de esta racha se menciona en el contexto (por debajo no aporta). */
        private const val MIN_STREAK_SHOWN = 2

        private const val DEFAULT_UNIT = "unidad"
        private const val ANY_STORE = "Cualquiera"

        private val SPANISH_DAYS = listOf(
            "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"
        )
        private val SPANISH_MONTHS = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
    }
}
