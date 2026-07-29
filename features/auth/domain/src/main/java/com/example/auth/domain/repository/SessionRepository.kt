package com.example.auth.domain.repository

import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    val userIdFlow: Flow<String?>
    suspend fun currentUserUid(): String?
}
