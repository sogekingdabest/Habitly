package com.monsteraltech.habitly.feature.household.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.usecase.EditHouseholdNameUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.JoinHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.UpdateNicknameUseCase
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
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
    val isLoading: Boolean = true,
    val error: String? = null,
    val isJoining: Boolean = false,
    val joinError: String? = null,
    val joinSuccess: Boolean = false
)

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val joinHouseholdUseCase: JoinHouseholdUseCase,
    private val editHouseholdNameUseCase: EditHouseholdNameUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase,
    private val getMemberProfilesUseCase: GetMemberProfilesUseCase,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HouseholdUiState())
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "unknown_user"
        
    private var observeHouseholdJob: Job? = null

    init {
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
                _uiState.update { it.copy(household = household, isLoading = false) }
                
                // Cargar perfiles de miembros cuando cambia la casa
                if (household != null) {
                    loadMemberProfiles(household.members)
                }
            }
        }
    }
    
    private fun loadMemberProfiles(memberIds: List<String>) {
        viewModelScope.launch {
            val result = getMemberProfilesUseCase(memberIds)
            if (result.isSuccess) {
                _uiState.update { it.copy(memberProfiles = result.getOrDefault(emptyList())) }
            }
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
            val result = updateNicknameUseCase(currentUserId, newNickname)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun onSignOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onComplete()
        }
    }
}
