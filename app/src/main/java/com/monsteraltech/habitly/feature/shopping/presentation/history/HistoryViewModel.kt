package com.monsteraltech.habitly.feature.shopping.presentation.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingHistory
import com.monsteraltech.habitly.feature.shopping.domain.usecase.ObserveShoppingHistoryUseCase
import com.monsteraltech.habitly.feature.shopping.domain.usecase.RestoreHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val historyList: List<ShoppingHistory> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRestoring: Boolean = false,
    val restoredId: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observeShoppingHistoryUseCase: ObserveShoppingHistoryUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val restoreHistoryUseCase: RestoreHistoryUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "unknown_user"
        
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId)
                .mapNotNull { it?.activeHouseholdId }
                .distinctUntilChanged()
                .collectLatest { householdId ->
                    startObserving(householdId)
                }
        }
    }

    private fun startObserving(householdId: String) {
        observeJob?.cancel()
        
        observeJob = viewModelScope.launch {
            observeShoppingHistoryUseCase(householdId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { history ->
                    _uiState.update { it.copy(historyList = history, isLoading = false, error = null) }
                }
        }
    }

    fun onRestoreHistory(historyId: String) {
        val householdId = currentUserId.let { uid ->
            _uiState.value.historyList.firstOrNull()?.id?.let { "" }
            null
        }
        
        viewModelScope.launch {
            val householdId = getCurrentHouseholdId() ?: return@launch
            _uiState.update { it.copy(isRestoring = true) }
            val result = restoreHistoryUseCase(householdId, historyId)
            _uiState.update { it.copy(isRestoring = false) }
            
            if (result.isSuccess) {
                _uiState.update { it.copy(restoredId = historyId) }
            } else {
                Log.e("HistoryViewModel", "Error restoring history", result.exceptionOrNull())
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearRestoreFeedback() {
        _uiState.update { it.copy(restoredId = null) }
    }

    private suspend fun getCurrentHouseholdId(): String? {
        var householdId: String? = null
        observeUserProfileUseCase(currentUserId)
            .mapNotNull { it?.activeHouseholdId }
            .collectLatest { id ->
                householdId = id
            }
        return householdId
    }
}
