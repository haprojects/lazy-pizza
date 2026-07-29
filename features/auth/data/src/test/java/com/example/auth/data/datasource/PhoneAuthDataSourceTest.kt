package com.example.auth.data.datasource

import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import io.mockk.Runs
import io.mockk.bdd.given
import io.mockk.bdd.then
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PhoneAuthDataSourceTest {

    @MockK(relaxed = true)
    lateinit var firebaseAuth: FirebaseAuth

    @MockK(relaxed = true)
    lateinit var firebaseUser: FirebaseUser

    @InjectMockKs
    lateinit var phoneAuthDataSource: PhoneAuthDataSource

    @Test
    suspend fun `userIdFlow when auth state changes and uid is not null then emits user id`() {
        // Given
        val mockUserId = "user-id-123"
        given { firebaseAuth.currentUser } returns firebaseUser
        given { firebaseUser.uid } returns mockUserId

        // When
        val userIdFlowResult = phoneAuthDataSource.userIdFlow.first()

        // Then
        assertThat(userIdFlowResult).isEqualTo(mockUserId)
    }

    @Test
    suspend fun `userIdFlow when auth state changes and uid is null then emits null`() {
        // Given
        given { firebaseAuth.currentUser } returns null

        // When
        val userIdFlowResult = phoneAuthDataSource.userIdFlow.first()

        // Then
        assertThat(userIdFlowResult).isNull()
    }

    @Test
    suspend fun `userIdFlow when no user logged in then emits null`() {
        // Given
        given { firebaseAuth.currentUser } returns null

        // When
        val emittedUserId = phoneAuthDataSource.userIdFlow.first()

        // Then
        assertThat(emittedUserId).isNull()
    }

    @Test
    suspend fun `signInWithCode when credential valid then returns remote user`() {
        // Given
        val verificationId = "verification-id"
        val smsCode = "123456"
        val expectedUid = "uid123"
        val expectedPhoneNumber = "+123456789"

        val credential = mockk<PhoneAuthCredential>()
        val authResult = mockk<AuthResult>()
        val task = mockk<Task<AuthResult>>()

        mockkStatic(PhoneAuthProvider::class)

        given { PhoneAuthProvider.getCredential(verificationId, smsCode) } returns credential

        given { firebaseAuth.signInWithCredential(credential) } returns task
        given { authResult.user } returns firebaseUser
        given { firebaseUser.uid } returns expectedUid
        given { firebaseUser.phoneNumber } returns expectedPhoneNumber

        given { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(authResult)
            task
        }
        given { task.addOnFailureListener(any()) } returns task

        // When
        val result = phoneAuthDataSource.signInWithCode(verificationId, smsCode)

        // Then
        assertThat(result.uid).isEqualTo(expectedUid)
        assertThat(result.phoneNumber).isEqualTo(expectedPhoneNumber)
    }

    @Test
    suspend fun `signInWithCode when credential valid but user is null then throws illegal state exception`() {
        // Given
        val verificationId = "verification-id"
        val smsCode = "123456"

        val credential = mockk<PhoneAuthCredential>()
        val authResult = mockk<AuthResult>()
        val task = mockk<Task<AuthResult>>()

        mockkStatic(PhoneAuthProvider::class)
        given { PhoneAuthProvider.getCredential(verificationId, smsCode) } returns credential

        given { firebaseAuth.signInWithCredential(credential) } returns task
        given { authResult.user } returns null

        given { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(authResult)
            task
        }
        given { task.addOnFailureListener(any()) } returns task

        // When
        val result = runCatching { phoneAuthDataSource.signInWithCode(verificationId, smsCode) }

        // Then
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `getCurrentUser when user is logged in then returns remote user`() {
        // Given
        val mockUid = "remoteUser123"
        val mockPhoneNumber = "+123456789"
        given { firebaseAuth.currentUser } returns firebaseUser
        given { firebaseUser.uid } returns mockUid
        given { firebaseUser.phoneNumber } returns mockPhoneNumber

        // When
        val result = phoneAuthDataSource.getCurrentUser()

        // Then
        assertThat(result?.uid).isEqualTo(mockUid)
        assertThat(result?.phoneNumber).isEqualTo(mockPhoneNumber)
    }

    @Test
    fun `getCurrentUser when no user logged in then returns null`() {
        // Given
        given { firebaseAuth.currentUser } returns null

        // When
        val result = phoneAuthDataSource.getCurrentUser()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `signOut when called then firebase auth signs out`() {
        // Given
        given { firebaseAuth.signOut() } just Runs

        // When
        phoneAuthDataSource.signOut()

        // Then
        then { firebaseAuth.signOut() }
    }
}
