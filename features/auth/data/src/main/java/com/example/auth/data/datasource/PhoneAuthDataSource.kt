package com.example.auth.data.datasource

import com.example.auth.data.model.RemoteUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PhoneAuthDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) {

    val userIdFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser?.uid)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    fun getCurrentUser(): RemoteUser? {
        val user = firebaseAuth.currentUser ?: return null
        return RemoteUser(
            uid = user.uid,
            phoneNumber = user.phoneNumber,
        )
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    suspend fun signInWithCode(
        verificationId: String,
        smsCode: String,
    ): RemoteUser {
        val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
        val firebaseUser = signInWithCredential(credential)
        return RemoteUser(
            uid = firebaseUser.uid,
            phoneNumber = firebaseUser.phoneNumber,
        )
    }

    private suspend fun signInWithCredential(credential: PhoneAuthCredential) = suspendCancellableCoroutine { continuationHandler ->
        firebaseAuth
            .signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    continuationHandler.resume(user)
                } else {
                    continuationHandler.resumeWithException(
                        IllegalStateException("FirebaseUser is null after signInWithCredential"),
                    )
                }
            }
            .addOnFailureListener { e ->
                if (continuationHandler.isActive) continuationHandler.resumeWithException(e)
            }
    }
}
