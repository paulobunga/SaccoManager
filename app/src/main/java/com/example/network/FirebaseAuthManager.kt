package com.example.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Singleton wrapper around Firebase Authentication.
 *
 * All suspend functions use [suspendCoroutine] to bridge Firebase's callback-based
 * [com.google.android.gms.tasks.Task] API into coroutine-friendly [Result] types.
 *
 * Related: REQ-4 — Firebase Authentication Integration
 */
object FirebaseAuthManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** The currently signed-in Firebase user, or null if no session is active. */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Creates a new Firebase Auth account using [email] and [password].
     *
     * @return [Result.success] with the created [FirebaseUser] on success, or
     *         [Result.failure] with a human-readable exception on error.
     */
    suspend fun register(email: String, password: String): Result<FirebaseUser> =
        suspendCoroutine { continuation ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null) {
                        continuation.resume(Result.success(user))
                    } else {
                        continuation.resume(
                            Result.failure(IllegalStateException("Registration succeeded but no user was returned."))
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(mapAuthException(exception)))
                }
        }

    /**
     * Signs in an existing user with [email] and [password].
     *
     * @return [Result.success] with the authenticated [FirebaseUser] on success, or
     *         [Result.failure] with a human-readable exception on error.
     */
    suspend fun login(email: String, password: String): Result<FirebaseUser> =
        suspendCoroutine { continuation ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null) {
                        continuation.resume(Result.success(user))
                    } else {
                        continuation.resume(
                            Result.failure(IllegalStateException("Login succeeded but no user was returned."))
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(mapAuthException(exception)))
                }
        }

    /**
     * Sends a password-reset email to [email].
     *
     * @return [Result.success] with [Unit] if the email was sent, or
     *         [Result.failure] with a human-readable exception on error.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> =
        suspendCoroutine { continuation ->
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(mapAuthException(exception)))
                }
        }

    /**
     * Signs out the currently authenticated user.
     *
     * Session state is managed by the Firebase SDK; this call clears the cached token
     * so [currentUser] returns null immediately after.
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Returns true if there is an active Firebase Auth session ([currentUser] != null).
     *
     * The Firebase SDK automatically restores the session across app restarts, so this
     * can be called during [MainActivity] startup to skip the login screen for returning users.
     */
    fun isLoggedIn(): Boolean = currentUser != null

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps Firebase Auth exceptions to [Exception]s with human-readable messages.
     *
     * Firebase error codes are opaque strings; this function translates the most common
     * typed exceptions into messages suitable for display to end users.
     */
    private fun mapAuthException(exception: Exception): Exception {
        val message = when (exception) {
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists."

            is FirebaseAuthInvalidCredentialsException ->
                "Invalid email or password."

            is FirebaseAuthWeakPasswordException ->
                "Password must be at least 6 characters."

            is FirebaseAuthInvalidUserException ->
                "No account found with this email."

            else ->
                exception.message?.takeIf { it.isNotBlank() }
                    ?: "Authentication failed. Please try again."
        }
        return Exception(message, exception)
    }
}
