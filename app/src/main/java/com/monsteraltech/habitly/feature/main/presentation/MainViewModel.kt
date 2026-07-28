package com.monsteraltech.habitly.feature.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.monsteraltech.habitly.feature.household.domain.usecase.BackfillHouseholdOwnerUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ClearActiveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.EnsureUserProfileUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.SyncOwnMemberProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = true,
    val hasHousehold: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val ensureUserProfileUseCase: EnsureUserProfileUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val clearActiveHouseholdUseCase: ClearActiveHouseholdUseCase,
    private val syncOwnMemberProfileUseCase: SyncOwnMemberProfileUseCase,
    private val backfillHouseholdOwnerUseCase: BackfillHouseholdOwnerUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val userId: String = firebaseAuth.currentUser?.uid ?: ""

    val uiState: StateFlow<MainUiState> =
        if (userId.isBlank()) {
            MutableStateFlow(MainUiState(isLoading = false)).asStateFlow()
        } else {
            observeUserProfileUseCase(userId)
                .flatMapLatest { profile ->
                    val householdId = profile?.activeHouseholdId
                    if (householdId.isNullOrBlank()) {
                        flowOf(MainUiState(isLoading = false, hasHousehold = false))
                    } else {
                        observeHouseholdUseCase(householdId)
                            .map { household ->
                                // Self-healing: if we were removed (no longer in members), reset
                                // the active household and go back to onboarding.
                                if (household != null && !household.members.contains(userId)) {
                                    clearActiveHouseholdUseCase(userId)
                                    MainUiState(isLoading = false, hasHousehold = false)
                                } else {
                                    // Fills in our public profile copy inside the household. This
                                    // is what lets households created before memberProfiles
                                    // existed complete themselves without a migration script:
                                    // each member writes their own on first launch. Idempotent,
                                    // so later launches produce no write.
                                    if (household != null && profile != null) {
                                        syncOwnMemberProfileUseCase(
                                            householdId, household, userId, profile
                                        )
                                        // Same idea for the owner: households older than the
                                        // field have it empty, and whoever created it claims
                                        // it on next launch.
                                        backfillHouseholdOwnerUseCase(
                                            householdId, household, userId
                                        )
                                    }
                                    MainUiState(isLoading = false, hasHousehold = true)
                                }
                            }
                            .catch { e ->
                                // Permission denied means removed; treat it as no household.
                                if ((e as? FirebaseFirestoreException)?.code ==
                                    FirebaseFirestoreException.Code.PERMISSION_DENIED
                                ) {
                                    clearActiveHouseholdUseCase(userId)
                                    emit(MainUiState(isLoading = false, hasHousehold = false))
                                } else {
                                    // Transient error (offline reads from cache): keep access.
                                    emit(MainUiState(isLoading = false, hasHousehold = true))
                                }
                            }
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = MainUiState(isLoading = true)
                )
        }

    init {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Usuario"
            viewModelScope.launch {
                ensureUserProfileUseCase(user.uid, displayName)
            }
        }
    }
}
