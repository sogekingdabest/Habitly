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
                                if (household != null && !household.members.contains(userId)) {
                                    clearActiveHouseholdUseCase(userId)
                                    MainUiState(isLoading = false, hasHousehold = false)
                                } else {
                                    if (household != null && profile != null) {
                                        syncOwnMemberProfileUseCase(
                                            householdId, household, userId, profile
                                        )
                                        backfillHouseholdOwnerUseCase(
                                            householdId, household, userId
                                        )
                                    }
                                    MainUiState(isLoading = false, hasHousehold = true)
                                }
                            }
                            .catch { e ->
                                if ((e as? FirebaseFirestoreException)?.code ==
                                    FirebaseFirestoreException.Code.PERMISSION_DENIED
                                ) {
                                    clearActiveHouseholdUseCase(userId)
                                    emit(MainUiState(isLoading = false, hasHousehold = false))
                                } else {
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
