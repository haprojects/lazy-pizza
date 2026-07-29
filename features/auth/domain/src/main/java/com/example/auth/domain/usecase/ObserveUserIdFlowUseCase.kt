package com.example.auth.domain.usecase

import com.example.auth.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUserIdFlowUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(): Flow<String?> = sessionRepository.userIdFlow
}
