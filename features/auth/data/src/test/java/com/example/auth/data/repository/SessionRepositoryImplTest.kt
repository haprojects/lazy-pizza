package com.example.auth.data.repository

import app.cash.turbine.test
import com.example.auth.data.datasource.PhoneAuthDataSource
import com.example.auth.data.model.RemoteUser
import io.mockk.bdd.given
import io.mockk.bdd.then
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SessionRepositoryImplTest {
    @MockK(relaxed = true)
    private lateinit var phoneAuthDataSource: PhoneAuthDataSource

    @InjectMockKs
    private lateinit var sessionRepositoryImpl: SessionRepositoryImpl

    @Test
    suspend fun `userIdFlow when subscribed then forwards data from phone auth data source`() {
        // Given
        val mockUserId = "user-id-123"
        val userIdFlow = MutableStateFlow<String?>(mockUserId)
        every { phoneAuthDataSource.userIdFlow } returns userIdFlow

        // When
        sessionRepositoryImpl.userIdFlow.test {
            // Then
            assertThat(awaitItem()).isEqualTo(mockUserId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    suspend fun `currentUserUid when called then returns current user uid from phone auth data source`() {
        // Given
        val mockUid = "user-id-123"
        val mockRemoteUser = mockk<RemoteUser> {
            given { uid } returns mockUid
        }
        given { phoneAuthDataSource.getCurrentUser() } returns mockRemoteUser

        // When
        val result = sessionRepositoryImpl.currentUserUid()

        // Then
        then { phoneAuthDataSource.getCurrentUser() }
        assertThat(result).isEqualTo(mockUid)
    }

    @Test
    suspend fun `currentUserUid when no user logged in then returns null`() {
        // Given
        given { phoneAuthDataSource.getCurrentUser() } returns null

        // When
        val result = sessionRepositoryImpl.currentUserUid()

        // Then
        then { phoneAuthDataSource.getCurrentUser() }
        assertThat(result).isNull()
    }
}
