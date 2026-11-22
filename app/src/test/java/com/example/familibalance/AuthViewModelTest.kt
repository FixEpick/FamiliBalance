package com.example.familibalance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.familibalance.util.UiText
import com.example.familibalance.viewmodel.AuthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Критический путь «вход родителя»: валидация полей и санитизация PIN
 * без обращения к сети (Supabase-вызовы не доходят — пустые поля
 * отсекаются до repo.signIn).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = AuthViewModel(app)
    }

    @Test
    fun `signIn with blank credentials sets error and does not call repo`() {
        viewModel.signIn()

        val error = viewModel.uiState.value.errorMessage
        assertNotNull(error)
        assertTrue(error is UiText.Res)
        assertEquals(R.string.auth_err_credentials, (error as UiText.Res).resId)
    }

    @Test
    fun `onEmailChange and onPasswordChange update state and clear error`() {
        viewModel.signIn() // выставляет ошибку
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onEmailChange("parent@example.com")
        viewModel.onPasswordChange("secret123")

        val state = viewModel.uiState.value
        assertEquals("parent@example.com", state.email)
        assertEquals("secret123", state.password)
        assertNull(state.errorMessage)
    }

    @Test
    fun `onPinChange keeps only digits and limits to 4 chars`() {
        viewModel.onPinChange("12ab34")
        assertEquals("1234", viewModel.uiState.value.pin)

        viewModel.onPinChange("123456")
        assertEquals("1234", viewModel.uiState.value.pin)
    }

    @Test
    fun `registerPlayer with blank nickname sets error`() {
        viewModel.registerPlayer()

        val error = viewModel.uiState.value.errorMessage
        assertNotNull(error)
        assertTrue(error is UiText.Res)
        assertEquals(R.string.auth_err_nickname, (error as UiText.Res).resId)
    }

    @Test
    fun `registerPlayer with short pin sets error`() {
        viewModel.onNicknameChange("Hero")
        viewModel.onPinChange("12")
        viewModel.registerPlayer()

        val error = viewModel.uiState.value.errorMessage
        assertNotNull(error)
        assertTrue(error is UiText.Res)
        assertEquals(R.string.auth_err_pin, (error as UiText.Res).resId)
    }
}
