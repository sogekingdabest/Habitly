package com.monsteraltech.habitly.feature.household.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.household.domain.model.Household
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Rol de propietario de la casa. Antes cualquier miembro podía expulsar a los demás o
 * borrar la casa entera con su historial; ahora eso es exclusivo de quien la creó.
 *
 * Estas comprobaciones se duplican en firestore.rules: aquí para dar un mensaje decente
 * en vez de un PERMISSION_DENIED, allí porque es la única que un cliente no puede saltarse.
 */
class HouseholdOwnershipTest {

    private lateinit var repository: FakeHouseholdRepository
    private lateinit var removeMember: RemoveMemberUseCase
    private lateinit var backfillOwner: BackfillHouseholdOwnerUseCase

    private val casa = Household(
        id = "casa-1",
        ownerId = "uid-ana",
        members = listOf("uid-ana", "uid-dani")
    )

    /** Casa creada antes de que existiera ownerId: el campo llega vacío. */
    private val casaAntigua = Household(
        id = "casa-vieja",
        ownerId = "",
        members = listOf("uid-ana", "uid-dani")
    )

    @Before
    fun setUp() {
        repository = FakeHouseholdRepository()
        removeMember = RemoveMemberUseCase(repository)
        backfillOwner = BackfillHouseholdOwnerUseCase(repository)
    }

    // ── Quién es el propietario ──────────────────────────────────────────────

    @Test
    fun `el propietario es quien figura en ownerId`() {
        assertTrue(casa.isOwner("uid-ana"))
        assertFalse(casa.isOwner("uid-dani"))
    }

    @Test
    fun `en una casa antigua manda el primer miembro`() {
        // Por construcción es quien la creó: createHousehold arranca members solo con el
        // creador y las altas posteriores usan arrayUnion, que añade al final.
        assertTrue(casaAntigua.isOwner("uid-ana"))
        assertFalse(casaAntigua.isOwner("uid-dani"))
    }

    @Test
    fun `una casa sin miembros no tiene propietario y no corona a nadie`() {
        val vacia = Household(id = "casa-0")
        assertEquals("", vacia.ownerOrFallback)
        assertFalse(vacia.isOwner(""))
    }

    // ── Expulsar miembros ────────────────────────────────────────────────────

    @Test
    fun `el propietario puede expulsar a otro miembro`() = runTest {
        val result = removeMember(casa, requesterId = "uid-ana", memberId = "uid-dani")

        assertTrue(result.isSuccess)
        assertEquals(listOf("casa-1" to "uid-dani"), repository.removedMembers)
    }

    @Test
    fun `un miembro cualquiera no puede expulsar a nadie`() = runTest {
        val result = removeMember(casa, requesterId = "uid-dani", memberId = "uid-ana")

        assertTrue(result.isFailure)
        assertTrue(
            "No debería haber llegado ninguna escritura al repositorio",
            repository.removedMembers.isEmpty()
        )
    }

    @Test
    fun `el propietario no se expulsa a si mismo desde aqui`() = runTest {
        // Para eso está "salir de la casa", que además traspasa la propiedad.
        val result = removeMember(casa, requesterId = "uid-ana", memberId = "uid-ana")

        assertTrue(result.isFailure)
        assertTrue(repository.removedMembers.isEmpty())
    }

    // ── Relleno de ownerId en casas antiguas ─────────────────────────────────

    @Test
    fun `el primer miembro reclama la propiedad de una casa antigua`() = runTest {
        backfillOwner("casa-vieja", casaAntigua, "uid-ana")

        assertEquals(listOf("casa-vieja" to "uid-ana"), repository.ownershipClaims)
    }

    @Test
    fun `un miembro que no es el primero no puede autoproclamarse propietario`() = runTest {
        backfillOwner("casa-vieja", casaAntigua, "uid-dani")

        assertTrue(repository.ownershipClaims.isEmpty())
    }

    @Test
    fun `no se toca ownerId si ya esta puesto`() = runTest {
        // Incluso llamándolo con el propio propietario: escribir lo mismo en cada arranque
        // sería quemar cuota para dejar el documento igual.
        backfillOwner("casa-1", casa, "uid-ana")

        assertTrue(repository.ownershipClaims.isEmpty())
    }
}
