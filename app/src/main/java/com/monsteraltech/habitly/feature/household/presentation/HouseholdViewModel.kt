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
    /** Household routine split (this week, last week and the household streak). */
    val share: HouseholdShareSummary = HouseholdShareSummary(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isJoining: Boolean = false,
    val joinError: String? = null,
    val joinSuccess: Boolean = false,
    val infoMessage: String? = null,
    /** Session uid. Kept in state so the UI can resolve [isOwner]. */
    val currentUserId: String = ""
) {
    /**
     * Whether the current user created the household. Governs which destructive actions are
     * visible: removing members and deleting the household. Everyone else can only leave.
     */
    val isOwner: Boolean
        get() = household?.isOwner(currentUserId) == true

    /**
     * Display name of each member (uid → name), with the fallback already applied outside: a
     * member who has not opened the app yet has no profile copied into the household.
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

    /** Household whose split has already been requested, to avoid re-querying on every emission. */
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
                        // Profiles come from the household document itself, so they arrive in the
                        // same emission: no separate load, no extra reads.
                        memberProfiles = household?.let(getMemberProfilesUseCase::invoke).orEmpty(),
                        isLoading = false
                    )
                }
                loadShare(householdId)
            }
        }
    }

    /**
     * Household routine split. Requested **once per household**: the household document emits on
     * every change (name, code, nicknames) and re-reading the completion history each time would
     * be Firestore queries for nothing. The panel is weekly, not a live counter.
     */
    private fun loadShare(householdId: String) {
        if (householdId.isBlank() || shareLoadedFor == householdId) return
        shareLoadedFor = householdId

        shareJob?.cancel()
        shareJob = viewModelScope.launch {
            val result = getHouseholdShareUseCase(currentUserId, householdId)
            result.onSuccess { summary -> _uiState.update { it.copy(share = summary) } }
            // A failure here must not break the screen: the panel simply does not appear, and a
            // retry is allowed on the next visit.
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
            // The nickname is duplicated into the household so other members see it; with no
            // household yet, updateNickname skips that part.
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
            // Leaving empties activeHouseholdId, and MainViewModel switches to onboarding by
            // itself.
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
