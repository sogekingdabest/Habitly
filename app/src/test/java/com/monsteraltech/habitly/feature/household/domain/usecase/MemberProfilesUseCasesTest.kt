package com.monsteraltech.habitly.feature.household.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.MemberProfile
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cubre la desnormalización de perfiles dentro del documento de la casa, que es lo que
 * permite tener `/users` cerrado a su propio dueño.
 */
class MemberProfilesUseCasesTest {

    private lateinit var repository: FakeHouseholdRepository
    private lateinit var syncOwnMemberProfile: SyncOwnMemberProfileUseCase
    private val getMemberProfiles = GetMemberProfilesUseCase()

    private val household = Household(
        id = "casa-1",
        name = "Casa",
        members = listOf("uid-ana", "uid-dani"),
        memberProfiles = mapOf(
            "uid-ana" to MemberProfile(displayName = "Ana García", nickname = "Ana"),
            "uid-dani" to MemberProfile(displayName = "Dani Olañeta", nickname = "Dani")
        )
    )

    @Before
    fun setUp() {
        repository = FakeHouseholdRepository()
        syncOwnMemberProfile = SyncOwnMemberProfileUseCase(repository)
    }

    // ── Resolución de nombres ────────────────────────────────────────────────

    @Test
    fun `resuelve los perfiles desde el documento de la casa`() {
        val profiles = getMemberProfiles(household)

        assertEquals(2, profiles.size)
        assertEquals("Ana", profiles.first { it.id == "uid-ana" }.nickname)
        assertEquals("Dani Olañeta", profiles.first { it.id == "uid-dani" }.displayName)
    }

    @Test
    fun `un miembro sin perfil desnormalizado sale con nombres en blanco, no se pierde`() {
        // Caso real durante la migración: una casa creada antes de que existiera
        // memberProfiles, con un compañero que todavía no ha abierto la app. Debe seguir
        // apareciendo en la lista (la UI lo traduce como "Desconocido") en vez de
        // desaparecer de la casa.
        val conHuecos = household.copy(
            members = listOf("uid-ana", "uid-sin-perfil"),
            memberProfiles = mapOf("uid-ana" to MemberProfile("Ana García", "Ana"))
        )

        val profiles = getMemberProfiles(conHuecos)

        assertEquals(listOf("uid-ana", "uid-sin-perfil"), profiles.map { it.id })
        assertEquals("", profiles.first { it.id == "uid-sin-perfil" }.nickname)
    }

    @Test
    fun `una casa sin perfiles desnormalizados no revienta`() {
        val profiles = getMemberProfiles(household.copy(memberProfiles = emptyMap()))

        assertEquals(2, profiles.size)
        assertTrue(profiles.all { it.nickname.isBlank() && it.displayName.isBlank() })
    }

    // ── Auto-relleno perezoso ────────────────────────────────────────────────

    @Test
    fun `no escribe si el perfil de la casa ya coincide`() = runTest {
        // Esta es la garantía que hace viable llamarlo en CADA arranque: sin ella sería
        // una escritura por usuario y sesión, para dejar el documento igual que estaba.
        val perfil = UserProfile(id = "uid-ana", displayName = "Ana García", nickname = "Ana")

        syncOwnMemberProfile("casa-1", household, "uid-ana", perfil)

        assertTrue(
            "No debería haber escrito nada: el perfil ya estaba al día",
            repository.syncedProfiles.isEmpty()
        )
    }

    @Test
    fun `escribe si el usuario todavia no tiene perfil en la casa`() = runTest {
        val casaAntigua = household.copy(memberProfiles = emptyMap())
        val perfil = UserProfile(id = "uid-ana", displayName = "Ana García", nickname = "Ana")

        syncOwnMemberProfile("casa-1", casaAntigua, "uid-ana", perfil)

        assertEquals(listOf(Triple("casa-1", "uid-ana", "Ana")), repository.syncedProfiles)
    }

    @Test
    fun `escribe si el nickname ha cambiado`() = runTest {
        val perfil = UserProfile(id = "uid-ana", displayName = "Ana García", nickname = "Anita")

        syncOwnMemberProfile("casa-1", household, "uid-ana", perfil)

        assertEquals(listOf(Triple("casa-1", "uid-ana", "Anita")), repository.syncedProfiles)
    }

    @Test
    fun `escribe si el displayName ha cambiado aunque el nickname siga igual`() = runTest {
        val perfil = UserProfile(id = "uid-ana", displayName = "Ana G. Pérez", nickname = "Ana")

        syncOwnMemberProfile("casa-1", household, "uid-ana", perfil)

        assertEquals(1, repository.syncedProfiles.size)
    }

    @Test
    fun `no escribe si no hay casa activa`() = runTest {
        val perfil = UserProfile(id = "uid-ana", displayName = "Ana García", nickname = "Ana")

        syncOwnMemberProfile("", Household(), "uid-ana", perfil)

        assertTrue(repository.syncedProfiles.isEmpty())
    }

    @Test
    fun `no escribe si no hay usuario`() = runTest {
        syncOwnMemberProfile("casa-1", household, "", UserProfile())

        assertTrue(repository.syncedProfiles.isEmpty())
    }
}
