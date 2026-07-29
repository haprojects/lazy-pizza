package com.example.auth.data.repository

import com.example.auth.data.datasource.PhoneAuthDataSource
import com.example.auth.data.model.RemoteUser
import com.example.auth.domain.model.AuthUser
import io.mockk.Runs
import io.mockk.bdd.coGiven
import io.mockk.bdd.coThen
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PhoneAuthRepositoryImplTest {

    @MockK
    private lateinit var phoneAuthDataSource: PhoneAuthDataSource

    @InjectMockKs
    private lateinit var phoneAuthRepository: PhoneAuthRepositoryImpl

    @Test
    suspend fun `signInWithCode when sign in succeeds then returns auth user`() {
        // Given
        val mockRemoteUser = RemoteUser(uid = "uid123", phoneNumber = "+123456789")
        val expected = AuthUser(uid = "uid123", phoneNumber = "+123456789")

        coGiven { phoneAuthDataSource.signInWithCode("", "") } returns mockRemoteUser

        // When
        val result = phoneAuthRepository.signInWithCode("", "")

        // Then
        assertThat(result.isSuccess).isTrue
        assertThat(result.getOrNull()).isEqualTo(expected)
        coThen { phoneAuthDataSource.signInWithCode("", "") }
    }

    @Test
    suspend fun `signInWithCode when exception thrown then returns failure`() {
        // Given
        val exception = IllegalStateException("Invalid code")

        coGiven { phoneAuthDataSource.signInWithCode("", "") } throws exception

        // When
        val result = phoneAuthRepository.signInWithCode("", "")

        // Then
        assertThat(result.isFailure).isTrue
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
        coThen { phoneAuthDataSource.signInWithCode("", "") }
    }

    @Test
    suspend fun `signOut when called then invokes data source sign out`() {
        // Given
        coGiven { phoneAuthDataSource.signOut() } just Runs

        // When
        phoneAuthRepository.signOut()

        // Then
        coThen { phoneAuthDataSource.signOut() }
    }
}
