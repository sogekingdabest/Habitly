package com.monsteraltech.habitly.feature.login.presentation

import com.monsteraltech.habitly.feature.login.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.login.domain.usecase.SendPasswordResetUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: ForgotPasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = ForgotPasswordViewModel(SendPasswordResetUseCase(fakeRepository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun advance() = testDispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `valid email sends reset and marks as sent`() {
        viewModel.onEmailChange("user@example.com")
        viewModel.onSendClick()
        advance()

        val state = viewModel.uiState.value
        assertTrue(state.isSent)
        assertFalse(state.isLoading)
        assertFalse(state.error)
        assertEquals(1, fakeRepository.passwordResetCallCount)
        assertEquals("user@example.com", fakeRepository.lastPasswordResetEmail)
    }

    @Test
    fun `invalid email is rejected without calling the repository`() {
        viewModel.onEmailChange("not-an-email")
        viewModel.onSendClick()
        advance()

        val state = viewModel.uiState.value
        assertTrue(state.emailInvalid)
        assertFalse(state.isSent)
        assertFalse(state.error)
        assertEquals(0, fakeRepository.passwordResetCallCount)
    }

    @Test
    fun `repository failure surfaces a generic error`() {
        fakeRepository.willFail = true

        viewModel.onEmailChange("user@example.com")
        viewModel.onSendClick()
        advance()

        val state = viewModel.uiState.value
        assertTrue(state.error)
        assertFalse(state.isSent)
        assertFalse(state.emailInvalid)
    }

    @Test
    fun `once sent, tapping again does not re-send`() {
        viewModel.onEmailChange("user@example.com")
        viewModel.onSendClick()
        advance()
        viewModel.onSendClick()
        advance()

        assertEquals(1, fakeRepository.passwordResetCallCount)
    }
}
