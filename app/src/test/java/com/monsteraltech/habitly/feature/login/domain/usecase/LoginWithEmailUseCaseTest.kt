package com.monsteraltech.habitly.feature.login.domain.usecase
import com.monsteraltech.habitly.feature.login.data.repository.FakeAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginWithEmailUseCaseTest {

    private val fakeRepository = FakeAuthRepository()
    private val useCase = LoginWithEmailUseCase(fakeRepository)

    @Test
    fun `when email is invalid, returns failure immediately`() = runBlocking {
        // Arrange
        val invalidEmail = "invalid-email"
        
        // Act
        val result = useCase(invalidEmail, "password123")
        
        // Assert
        assertTrue(result.isFailure)
        assertEquals("Formato de email inválido", result.exceptionOrNull()?.message)
    }

    @Test
    fun `when password is less than 6 chars, returns failure`() = runBlocking {
        // Act
        val result = useCase("test@test.com", "12345")
        
        // Assert
        assertTrue(result.isFailure)
        assertEquals("La contraseña debe tener al menos 6 caracteres", result.exceptionOrNull()?.message)
    }

    @Test
    fun `when credentials are valid, delegates to repository`() = runBlocking {
        // Act
        val result = useCase("test@test.com", "supersecret123")
        
        // Assert
        assertTrue(result.isSuccess)
        assertEquals("fake_access", result.getOrNull()?.accessToken)
    }
}
