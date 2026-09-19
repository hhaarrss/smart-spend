package com.smartspend.app.ui.auth

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Outcomes of a single verifyPhoneNumber() call — Firebase can reach any of these. */
internal sealed interface PhoneVerificationEvent {
    /**
     * The device itself confirmed the number (SIM auto-read or an already-trusted device) —
     * no OTP was ever shown to the user, so the OTP-entry screen should be skipped entirely.
     */
    data class AutoVerified(val credential: PhoneAuthCredential) : PhoneVerificationEvent
    data class CodeSent(val verificationId: String) : PhoneVerificationEvent
    data class Failed(val message: String) : PhoneVerificationEvent
}

/**
 * Thin coroutine wrapper around Firebase's callback-based Phone Auth API.
 *
 * Not a singleton/object on purpose: it's created fresh per auth attempt so a stale
 * verificationId from an earlier phone number can't leak into a later one.
 */
internal class PhoneAuthController(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    /**
     * Starts phone verification. The returned Flow emits at most one event — Firebase only
     * ever calls one of onVerificationCompleted/onVerificationFailed/onCodeSent per attempt.
     *
     * @param phoneNumber E.164 format, e.g. "+919876543210".
     */
    fun verifyPhoneNumber(activity: Activity, phoneNumber: String): Flow<PhoneVerificationEvent> =
        callbackFlow {
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    trySend(PhoneVerificationEvent.AutoVerified(credential))
                    close()
                }

                override fun onVerificationFailed(exception: FirebaseException) {
                    trySend(PhoneVerificationEvent.Failed(readableError(exception)))
                    close()
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    trySend(PhoneVerificationEvent.CodeSent(verificationId))
                    close()
                }
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)

            awaitClose { }
        }

    /** Signs in with a manually-entered OTP against a prior verifyPhoneNumber() call. */
    suspend fun signInWithCode(verificationId: String, code: String): Result<String> {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return signInWithCredential(credential)
    }

    /** Signs in with a credential Firebase already confirmed on-device (AutoVerified path). */
    suspend fun signInWithCredential(credential: PhoneAuthCredential): Result<String> = try {
        val result = auth.signInWithCredential(credential).await()
        val idToken = result.user?.getIdToken(false)?.await()?.token
        if (idToken.isNullOrEmpty()) {
            Result.failure(IllegalStateException("Signed in but no ID token was issued."))
        } else {
            Result.success(idToken)
        }
    } catch (e: Exception) {
        Result.failure(Exception(readableError(e), e))
    }

    private fun readableError(e: Throwable): String = when {
        e.message?.contains("invalid", ignoreCase = true) == true &&
            e.message?.contains("code", ignoreCase = true) == true ->
            "That code didn't match. Check it and try again."
        e is FirebaseException -> e.message ?: "Verification failed. Try again."
        else -> e.message ?: "Something went wrong. Try again."
    }
}

/**
 * A minimal `Task.await()` so this file doesn't need the kotlinx-coroutines-play-services
 * dependency just for this one call site.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(task.exception ?: IllegalStateException("Task failed with no exception"))
        }
    }
}
