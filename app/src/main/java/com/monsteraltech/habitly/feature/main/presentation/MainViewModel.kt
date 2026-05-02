package com.monsteraltech.habitly.feature.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.usecase.InitializeUserAndHouseholdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val initializeUseCase: InitializeUserAndHouseholdUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    init {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore("@") ?: "Usuario"
            viewModelScope.launch {
                initializeUseCase(user.uid, displayName)
            }
        }
    }
}
