package com.example.auth.ui.login

import app.cash.turbine.test
import com.example.auth.domain.model.AuthUser
import com.example.auth.domain.usecase.SignInWithSmsCodeUseCase
import com.example.cart.domain.usecase.TransferGuestCartToUserUseCase
import com.example.core.testing.MainDispatcherExtension
import io.mockk.Runs
import io.mockk.bdd.coGiven
import io.mockk.bdd.coThen
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@ExtendWith(MockKExtension::class)
class PhoneAuthViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @MockK
    lateinit var signInWithSmsCode: SignInWithSmsCodeUseCase

    @MockK
    lateinit var transferGuestCartToUserUseCase: TransferGuestCartToUserUseCase

    @InjectMockKs
    lateinit var viewModel: PhoneAuthViewModel

    @Nested
    inner class UpdatePhone {

        @Test
        fun `updatePhone when in enter phone then updates phone number`() {
            // Given
            val input = RAW_PHONE

            // When
            viewModel.updatePhone(input)

            // Then
            assertThat(currentEnterPhoneState().phoneNumber).isEqualTo(TRIMMED_PHONE)
        }

        @Test
        fun `updatePhone when in enter code then does nothing`() {
            // Given
            givenEnterCodeState()
            val originalPhone = currentEnterCodeState().phoneLocal

            // When
            viewModel.updatePhone(RAW_PHONE)

            // Then
            assertThat(currentEnterCodeState().phoneLocal).isEqualTo(originalPhone)
        }
    }

    @Nested
    inner class UpdateFullPhone {

        @Test
        fun `updateFullPhone when in enter phone then updates full phone number`() {
            // Given
            val input = FULL_PHONE

            // When
            viewModel.updateFullPhone(input)

            // Then
            assertThat(currentEnterPhoneState().fullPhoneNumber).isEqualTo(FULL_PHONE)
        }

        @Test
        fun `updateFullPhone when in enter code then does nothing`() {
            // Given
            givenEnterCodeState()
            val originalFull = currentEnterCodeState().fullPhoneNumber

            // When
            viewModel.updateFullPhone("+19999999")

            // Then
            assertThat(currentEnterCodeState().fullPhoneNumber).isEqualTo(originalFull)
        }
    }

    @Nested
    inner class UpdateCountryIso {

        @Test
        fun `updateCountryIso when in enter phone then updates selected country iso`() {
            // Given
            val iso = COUNTRY_ISO

            // When
            viewModel.updateCountryIso(iso)

            // Then
            assertThat(currentEnterPhoneState().selectedCountryIso).isEqualTo(COUNTRY_ISO)
        }

        @Test
        fun `updateCountryIso when in enter code then does nothing`() {
            // Given
            givenEnterCodeState()
            val originalIso = currentEnterCodeState().countryIso

            // When
            viewModel.updateCountryIso("FR")

            // Then
            assertThat(currentEnterCodeState().countryIso).isEqualTo(originalIso)
        }
    }

    @Nested
    inner class UpdatePhoneValidity {

        @Test
        fun `updatePhoneValidity when in enter phone then updates is phone valid`() {
            // Given
            val isValid = true

            // When
            viewModel.updatePhoneValidity(isValid)

            // Then
            assertThat(currentEnterPhoneState().isPhoneValid).isTrue()
        }

        @Test
        fun `updatePhoneValidity when in enter code then does nothing`() {
            // Given
            givenEnterCodeState()
            val originalCode = currentEnterCodeState()

            // When
            viewModel.updatePhoneValidity(true)

            // Then
            assertThat(currentEnterCodeState()).isEqualTo(originalCode)
        }
    }

    @Nested
    inner class VerifyPhone {

        @Test
        suspend fun `verifyPhone when continue enabled then emits start phone verification event`() {
            // Given
            givenReadyToVerifyPhone()

            viewModel.events.test {
                // When
                viewModel.verifyPhone()
                mainDispatcher.scheduler.runCurrent()

                // Then
                assertThat(awaitItem()).isEqualTo(PhoneAuthEvent.StartPhoneVerification(FULL_PHONE))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `verifyPhone when continue enabled then shows loading`() {
            // Given
            givenReadyToVerifyPhone()

            // When
            viewModel.verifyPhone()

            // Then
            assertThat(currentEnterPhoneState().isLoading).isTrue()
        }

        @Test
        suspend fun `verifyPhone when continue disabled then emits nothing`() {
            // Given
            viewModel.updateFullPhone(FULL_PHONE)

            viewModel.events.test {
                // When
                viewModel.verifyPhone()
                mainDispatcher.scheduler.runCurrent()

                // Then
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class SkipAuth {

        @Test
        suspend fun `skipAuth when called then emits skip auth event`() {
            viewModel.events.test {
                // When
                viewModel.skipAuth()
                mainDispatcher.scheduler.runCurrent()

                // Then
                assertThat(awaitItem()).isEqualTo(PhoneAuthEvent.SkipAuth)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class HandleCodeSent {

        @Test
        fun `handleCodeSent when called then updates ui state to enter code`() {
            // Given
            givenReadyToVerifyPhone()

            // When
            viewModel.handleCodeSent(VERIFICATION_ID)

            // Then
            assertThat(viewModel.uiState.value).isInstanceOf(PhoneAuthUiState.EnterCode::class.java)
        }

        @Test
        fun `handleCodeSent when called then disables resend initially`() {
            // Given
            givenReadyToVerifyPhone()

            // When
            viewModel.handleCodeSent(VERIFICATION_ID)

            // Then
            assertThat(currentEnterCodeState().canResend).isFalse()
        }

        @Test
        fun `handleCodeSent when timeout elapses then enables resend`() {
            // Given
            givenEnterCodeState()

            // When
            mainDispatcher.scheduler.advanceUntilIdle()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(currentEnterCodeState().canResend).isTrue()
        }
    }

    @Nested
    inner class UpdateCode {

        @Test
        fun `updateCode when input has non digits then updates code digits`() {
            // Given
            givenEnterCodeState()

            // When
            viewModel.updateCode(CODE_WITH_NON_DIGITS)

            // Then
            assertThat(currentEnterCodeState().code).isEqualTo(CODE_6_DIGITS)
        }

        @Test
        fun `updateCode when in enter phone then does nothing`() {
            // Given
            val originalPhone = currentEnterPhoneState().phoneNumber

            // When
            viewModel.updateCode("123456")

            // Then
            assertThat(currentEnterPhoneState().phoneNumber).isEqualTo(originalPhone)
        }
    }

    @Nested
    inner class ConfirmCode {

        @Test
        fun `confirmCode when confirm disabled then does nothing`() {
            // Given
            givenEnterCodeState()
            viewModel.updateCode("12")
            val originalLoading = currentEnterCodeState().isLoading

            // When
            viewModel.confirmCode()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(currentEnterCodeState().isLoading).isEqualTo(originalLoading)
        }

        @Test
        suspend fun `confirmCode when sign in succeeds then emits auth completed event`() {
            // Given
            givenEnterCodeState()
            givenEnteredSmsCode()
            givenSuccessfulSignIn()

            viewModel.events.test {
                // When
                viewModel.confirmCode()
                mainDispatcher.scheduler.runCurrent()

                // Then
                assertThat(awaitItem()).isEqualTo(PhoneAuthEvent.AuthCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `confirmCode when sign in succeeds then calls transfer guest cart`() {
            // Given
            givenEnterCodeState()
            givenEnteredSmsCode()
            givenSuccessfulSignIn()

            // When
            viewModel.confirmCode()
            mainDispatcher.scheduler.runCurrent()

            // Then
            coThen(exactly = 1) { transferGuestCartToUserUseCase(USER_ID) }
        }

        @Test
        fun `confirmCode when sign in succeeds then resets ui state to enter phone`() {
            // Given
            givenEnterCodeState()
            givenEnteredSmsCode()
            givenSuccessfulSignIn()

            // When
            viewModel.confirmCode()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(viewModel.uiState.value).isInstanceOf(PhoneAuthUiState.EnterPhone::class.java)
        }

        @Test
        fun `confirmCode when sign in fails then shows error message`() {
            // Given
            givenEnterCodeState()
            givenEnteredSmsCode()
            givenFailedSignIn()

            // When
            viewModel.confirmCode()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(currentEnterCodeState().errorMessage).isEqualTo(ERROR_MESSAGE)
        }

        @Test
        fun `confirmCode when sign in fails then hides loading`() {
            // Given
            givenEnterCodeState()
            givenEnteredSmsCode()
            givenFailedSignIn()

            // When
            viewModel.confirmCode()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(currentEnterCodeState().isLoading).isFalse()
        }
    }

    @Nested
    inner class ResendCode {

        @Test
        suspend fun `resendCode when resend enabled then emits resend code event`() {
            // Given
            givenResendEnabledState()

            viewModel.events.test {
                // When
                viewModel.resendCode()
                mainDispatcher.scheduler.runCurrent()

                // Then
                assertThat(awaitItem()).isEqualTo(PhoneAuthEvent.ResendCode(FULL_PHONE))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `resendCode when resend enabled then disables resend`() {
            // Given
            givenResendEnabledState()

            // When
            viewModel.resendCode()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(currentEnterCodeState().canResend).isFalse()
        }

        @Test
        fun `resendCode when resend enabled then resets seconds remaining`() {
            // Given
            givenResendEnabledState()

            // When
            viewModel.resendCode()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(currentEnterCodeState().secondsRemaining)
                .isEqualTo(PhoneVerificationManager.RESEND_TIMEOUT_SECONDS.toInt())
        }
    }

    @Nested
    inner class EditPhone {

        @Test
        fun `editPhone when in enter code then updates ui state to enter phone`() {
            // Given
            givenEnterCodeState()

            // When
            viewModel.editPhone()
            mainDispatcher.scheduler.runCurrent()

            // Then
            assertThat(viewModel.uiState.value).isInstanceOf(PhoneAuthUiState.EnterPhone::class.java)
        }
    }

    @Nested
    inner class HandleVerificationFailure {

        @Test
        fun `handleVerificationFailure when in enter phone then shows error message`() {
            // Given
            val error = IllegalStateException(ERROR_MESSAGE)

            // When
            viewModel.handleVerificationFailure(error)

            // Then
            assertThat(currentEnterPhoneState().errorMessage).isEqualTo(ERROR_MESSAGE)
        }

        @Test
        fun `handleVerificationFailure when in enter code then shows error message`() {
            // Given
            givenEnterCodeState()
            val error = IllegalStateException(ERROR_MESSAGE)

            // When
            viewModel.handleVerificationFailure(error)

            // Then
            assertThat(currentEnterCodeState().errorMessage).isEqualTo(ERROR_MESSAGE)
        }
    }

    private fun givenReadyToVerifyPhone() {
        viewModel.updatePhone(PHONE_LOCAL)
        viewModel.updateFullPhone(FULL_PHONE)
        viewModel.updatePhoneValidity(IS_PHONE_VALID)
        viewModel.updateCountryIso(COUNTRY_ISO)
    }

    private fun givenEnterCodeState() {
        givenReadyToVerifyPhone()
        viewModel.handleCodeSent(VERIFICATION_ID)
        mainDispatcher.scheduler.runCurrent()
    }

    private fun givenEnteredSmsCode() {
        viewModel.updateCode(CODE_6_DIGITS)
    }

    private fun givenSuccessfulSignIn() {
        val user = authUser()
        coGiven { signInWithSmsCode(VERIFICATION_ID, CODE_6_DIGITS) } returns Result.success(user)
        coGiven { transferGuestCartToUserUseCase(USER_ID) } just Runs
    }

    private fun givenFailedSignIn() {
        val exception = IllegalStateException(ERROR_MESSAGE)
        coGiven { signInWithSmsCode(VERIFICATION_ID, CODE_6_DIGITS) } returns Result.failure(exception)
    }

    private fun givenResendEnabledState() {
        givenEnterCodeState()
        mainDispatcher.scheduler.advanceUntilIdle()
        mainDispatcher.scheduler.runCurrent()
    }

    private fun authUser(): AuthUser = AuthUser(uid = USER_ID, phoneNumber = FULL_PHONE)

    private fun currentEnterPhoneState(): PhoneAuthUiState.EnterPhone = viewModel.uiState.value as PhoneAuthUiState.EnterPhone

    private fun currentEnterCodeState(): PhoneAuthUiState.EnterCode = viewModel.uiState.value as PhoneAuthUiState.EnterCode

    private companion object {
        private const val RAW_PHONE = "  5551234  "
        private const val TRIMMED_PHONE = "5551234"
        private const val PHONE_LOCAL = "5551234"
        private const val FULL_PHONE = "+15551234"
        private const val COUNTRY_ISO = "US"

        private const val VERIFICATION_ID = "verification-id"

        private const val CODE_WITH_NON_DIGITS = "1a2b3c4d5e6f"
        private const val CODE_6_DIGITS = "123456"

        private const val USER_ID = "user-id"
        private const val ERROR_MESSAGE = "Incorrect code"

        private const val IS_PHONE_VALID = true
    }
}
