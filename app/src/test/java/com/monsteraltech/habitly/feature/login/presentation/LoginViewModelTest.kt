package com.monsteraltech.habitly.feature.login.presentation

import com.monsteraltech.habitly.feature.login.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.login.domain.usecase.LoginWithEmailUseCase
import com.monsteraltech.habitly.feature.login.domain.usecase.LoginWithGoogleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios del LoginViewModel.
 *
 * No usa Hilt — el ViewModel se construye directamente con el FakeAuthRepository,
 * siguiendo el principio de la skill android-testing-unit:
 * "narrowest verification strategy that still catches the likely regressions".
 *
 * FakeAuthModule (@TestInstallIn) queda disponible para tests de integración
 * que sí necesiten el grafo Hilt completo (p.ej. tests de Activity con Hilt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = LoginViewModel(
            loginWithEmailUseCase = LoginWithEmailUseCase(fakeRepository),
            loginWithGoogleUseCase = LoginWithGoogleUseCase(fakeRepository)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Estado inicial ---

    @Test
    fun `initial state is empty and not loading`() {
        val state = viewModel.uiState.value
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isLoginSuccessful)
    }

    // --- Eventos de campos ---

    @Test
    fun `EmailChanged updates email in state`() {
        viewModel.onEvent(LoginEvent.EmailChanged("user@test.com"))
        assertEquals("user@test.com", viewModel.uiState.value.email)
    }

    @Test
    fun `PasswordChanged updates password in state`() {
        viewModel.onEvent(LoginEvent.PasswordChanged("secret123"))
        assertEquals("secret123", viewModel.uiState.value.password)
    }

    @Test
    fun `EmailChanged clears previous error message`() = runTest {
        // Setup: provocar un error primero
        fakeRepository.willFail = true
        viewModel.onEvent(LoginEvent.EmailChanged("valid@test.com"))
        viewModel.onEvent(LoginEvent.PasswordChanged("password123"))
        viewModel.onEvent(LoginEvent.LoginClicked)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.errorMessage != null)

        // Act: cambiar el email limpia el error
        viewModel.onEvent(LoginEvent.EmailChanged("other@test.com"))

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // --- Login exitoso ---

    @Test
    fun `LoginClicked with valid credentials sets isLoginSuccessful`() = runTest {
        viewModel.onEvent(LoginEvent.EmailChanged("user@test.com"))
        viewModel.onEvent(LoginEvent.PasswordChanged("superpassword"))
        viewModel.onEvent(LoginEvent.LoginClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoginSuccessful)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `LoginClicked calls repository exactly once`() = runTest {
        viewModel.onEvent(LoginEvent.EmailChanged("user@test.com"))
        viewModel.onEvent(LoginEvent.PasswordChanged("superpassword"))
        viewModel.onEvent(LoginEvent.LoginClicked)
        advanceUntilIdle()

        assertEquals(1, fakeRepository.loginCallCount)
    }

    // --- Login fallido ---

    @Test
    fun `LoginClicked when repository fails shows error message`() = runTest {
        fakeRepository.willFail = true
        fakeRepository.errorMessage = "Credenciales incorrectas"

        viewModel.onEvent(LoginEvent.EmailChanged("user@test.com"))
        viewModel.onEvent(LoginEvent.PasswordChanged("wrongpass"))
        viewModel.onEvent(LoginEvent.LoginClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoginSuccessful)
        assertFalse(state.isLoading)
        assertEquals("Credenciales incorrectas", state.errorMessage)
    }

    // --- Validación del UseCase (dominio) ---

    @Test
    fun `LoginClicked with invalid email shows validation error without calling repository`() = runTest {
        viewModel.onEvent(LoginEvent.EmailChanged("not-an-email"))
        viewModel.onEvent(LoginEvent.PasswordChanged("password123"))
        viewModel.onEvent(LoginEvent.LoginClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoginSuccessful)
        assertEquals("Formato de email inválido", state.errorMessage)
        assertEquals(0, fakeRepository.loginCallCount)
    }

    @Test
    fun `LoginClicked with short password shows validation error without calling repository`() = runTest {
        viewModel.onEvent(LoginEvent.EmailChanged("user@test.com"))
        viewModel.onEvent(LoginEvent.PasswordChanged("123"))
        viewModel.onEvent(LoginEvent.LoginClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoginSuccessful)
        assertEquals("La contraseña debe tener al menos 6 caracteres", state.errorMessage)
        assertEquals(0, fakeRepository.loginCallCount)
    }

    @Test
    fun `GoogleAuthTokenReceived with valid token sets isLoginSuccessful`() = runTest {
        viewModel.onEvent(LoginEvent.GoogleAuthTokenReceived("fake_token"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoginSuccessful)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(1, fakeRepository.googleSignInCallCount)
    }

    @Test
    fun `GoogleAuthTokenReceived when repository fails shows error message`() = runTest {
        fakeRepository.willFail = true
        fakeRepository.errorMessage = "Google login failed"

        viewModel.onEvent(LoginEvent.GoogleAuthTokenReceived("fake_token"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoginSuccessful)
        assertFalse(state.isLoading)
        assertEquals("Google login failed", state.errorMessage)
    }
}
