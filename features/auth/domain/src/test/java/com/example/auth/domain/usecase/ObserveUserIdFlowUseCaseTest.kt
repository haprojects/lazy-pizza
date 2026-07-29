package com.example.auth.domain.usecase

import app.cash.turbine.test
import com.example.auth.domain.repository.SessionRepository
import io.mockk.bdd.given
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.MutableStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ObserveUserIdFlowUseCaseTest {

    @MockK
    private lateinit var sessionRepository: SessionRepository

    @InjectMockKs
    private lateinit var useCase: ObserveUserIdFlowUseCase

    @Test
    suspend fun `invoke when is signed in emits values then emits same values`() {
        // Given
        val userId: MutableStateFlow<String?> = MutableStateFlow(null)
        given { sessionRepository.userIdFlow } returns userId

        // When
        useCase().test {
            // Then
            userId.tryEmit(null)
            assertThat(awaitItem()).isNull()

            userId.tryEmit("user-id-123")
            assertThat(awaitItem()).isEqualTo("user-id-123")

            cancelAndIgnoreRemainingEvents()
        }
    }
}
