package com.monsteraltech.habitly.feature.household.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.usecase.EditHouseholdNameUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.JoinHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.LeaveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.RegenerateInviteCodeUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.RemoveMemberUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.UpdateNicknameUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.HouseholdShareSummary
import com.monsteraltech.habitly.feature.routines.domain.usecase.GetHouseholdShareUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdUiState(
    val userProfile: UserProfile? = null,
    val household: Household? = null,
    val memberProfiles: List<UserProfile> = emptyList(),
    /** Reparto de las rutinas de casa (esta semana, la pasada y la racha de la casa). */
    val share: HouseholdShareSummary = HouseholdShareSummary(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isJoining: Boolean = false,
    val joinError: String? = null,
    val joinSuccess: Boolean = false,
    val infoMessage: String? = null,
    /** Uid de la sesión. Va en el estado para poder resolver [isOwner] desde la UI. */
    val currentUserId: String = ""
) {
    /**
     * Si el usuario actual es quien creó la casa. Gobierna qué acciones destructivas se
     * ven: expulsar miembros y borrar la casa. Los demás solo pueden salirse.
     */
    val isOwner: Boolean
        get() = household?.isOwner(currentUserId) == true

    /**
     * Nombre visible de cada miembro (uid → nombre), con la reserva ya aplicada fuera: un
     * miembro que aún no ha abierto la app no tiene perfil copiado en la casa.
     */
    val memberNames: Map<String, String>
        get() = memberProfiles.associate { it.id to it.nickname.ifBlank { it.displayName } }
}

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val joinHouseholdUseCase: JoinHouseholdUseCase,
    private val editHouseholdNameUseCase: EditHouseholdNameUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase,
    private val getMemberProfilesUseCase: GetMemberProfilesUseCase,
    private val leaveHouseholdUseCase: LeaveHouseholdUseCase,
    private val removeMemberUseCase: RemoveMemberUseCase,
    private val regenerateInviteCodeUseCase: RegenerateInviteCodeUseCase,
    private val getHouseholdShareUseCase: GetHouseholdShareUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HouseholdUiState())
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "unknown_user"
        
    private var observeHouseholdJob: Job? = null
    private var shareJob: Job? = null

    /** Casa cuyo reparto ya se ha pedido, para no repetir la consulta en cada emisión. */
    private var shareLoadedFor: String? = null

    init {
        _uiState.update { it.copy(currentUserId = currentUserId) }
        observeUserProfile()
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId).collectLatest { profile ->
                _uiState.update { it.copy(userProfile = profile) }
                
                if (profile != null && profile.activeHouseholdId.isNotBlank()) {
                    observeHousehold(profile.activeHouseholdId)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun observeHousehold(householdId: String) {
        observeHouseholdJob?.cancel()
        observeHouseholdJob = viewModelScope.launch {
            observeHouseholdUseCase(householdId).collect { household ->
                _uiState.update {
                    it.copy(
                        household = household,
                        // Los perfiles salen del propio documento de la casa, así que llegan
                        // en la misma emisión: ni carga aparte ni lecturas extra.
                        memberProfiles = household?.let(getMemberProfilesUseCase::invoke).orEmpty(),
                        isLoading = false
                    )
                }
                loadShare(householdId)
            }
        }
    }

    /**
     * Reparto de las rutinas de casa. Se pide **una vez por casa**: el documento de la casa
     * emite en cada cambio (nombre, código, nicknames) y volver a leer el historial de
     * completados en cada emisión serían consultas de Firestore para nada. El panel es semanal,
     * no un contador en vivo.
     */
    private fun loadShare(householdId: String) {
        if (householdId.isBlank() || shareLoadedFor == householdId) return
        shareLoadedFor = householdId

        shareJob?.cancel()
        shareJob = viewModelScope.launch {
            val result = getHouseholdShareUseCase(currentUserId, householdId)
            result.onSuccess { summary -> _uiState.update { it.copy(share = summary) } }
            // Un fallo aquí no debe estropear la pantalla: el panel simplemente no aparece,
            // pero se permite reintentar en la siguiente entrada.
            result.onFailure { shareLoadedFor = null }
        }
    }

    fun onJoinHousehold(inviteCode: String) {
        if (inviteCode.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isJoining = true, joinError = null, joinSuccess = false) }
            val result = joinHouseholdUseCase(currentUserId, inviteCode)
            
            if (result.isSuccess) {
                _uiState.update { it.copy(isJoining = false, joinSuccess = true) }
            } else {
                _uiState.update { it.copy(isJoining = false, joinError = result.exceptionOrNull()?.message) }
            }
        }
    }
    
    fun resetJoinState() {
        _uiState.update { it.copy(joinError = null, joinSuccess = false) }
    }
    
    fun onEditHouseholdName(newName: String) {
        val currentProfile = _uiState.value.userProfile ?: return
        val currentHouseholdId = currentProfile.activeHouseholdId
        if (currentHouseholdId.isBlank()) return
        
        viewModelScope.launch {
            val result = editHouseholdNameUseCase(currentHouseholdId, newName)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }
    
    fun onUpdateNickname(newNickname: String) {
        if (currentUserId == "unknown_user") return

        viewModelScope.launch {
            // El nickname se duplica en la casa para que lo vean los demás miembros; si el
            // usuario aún no tiene casa, updateNickname omite esa parte.
            val householdId = _uiState.value.userProfile?.activeHouseholdId.orEmpty()
            val result = updateNicknameUseCase(currentUserId, householdId, newNickname)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun onLeaveHousehold() {
        val householdId = _uiState.value.userProfile?.activeHouseholdId ?: return
        if (currentUserId == "unknown_user" || householdId.isBlank()) return
        viewModelScope.launch {
            // Al salir, activeHouseholdId queda vacío y el MainViewModel conmuta
            // automáticamente al onboarding.
            val result = leaveHouseholdUseCase(currentUserId, householdId)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun onRemoveMember(memberId: String) {
        val household = _uiState.value.household ?: return
        viewModelScope.launch {
            val result = removeMemberUseCase(household, currentUserId, memberId)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun onRegenerateInviteCode() {
        val householdId = _uiState.value.userProfile?.activeHouseholdId ?: return
        if (householdId.isBlank()) return
        viewModelScope.launch {
            val result = regenerateInviteCodeUseCase(householdId)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
}
