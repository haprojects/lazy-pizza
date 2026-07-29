package com.example.auth.data.repository

import com.example.auth.data.datasource.PhoneAuthDataSource
import com.example.auth.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val phoneAuthDataSource: PhoneAuthDataSource,
) : SessionRepository {

    override val userIdFlow: Flow<String?>
        get() = phoneAuthDataSource.userIdFlow

    override suspend fun currentUserUid(): String? = phoneAuthDataSource.getCurrentUser()?.uid
}
