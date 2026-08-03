package com.litesails.saccomanager.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ClerkUser(
    val uid: String,
    val email: String
)

object ClerkAuthManager {
    private const val TAG = "ClerkAuthManager"

    fun initialize(context: Context) {
        SupabaseAuthManager.initialize(context)
    }

    suspend fun register(email: String, password: String): Result<ClerkUser> = withContext(Dispatchers.IO) {
        SupabaseAuthManager.signUp(email, password, email, "")
            .map { ClerkUser(it["id"] ?: "", it["email"] ?: email) }
    }

    suspend fun login(email: String, password: String): Result<ClerkUser> = withContext(Dispatchers.IO) {
        SupabaseAuthManager.signIn(email, password)
            .map { ClerkUser(it["id"] ?: "", it["email"] ?: email) }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = SupabaseAuthManager.sendPasswordReset(email)

    suspend fun getSupabaseToken(): String? = SupabaseAuthManager.accessToken

    fun logout() {
        SupabaseAuthManager.logout()
        Log.d(TAG, "User signed out.")
    }

    fun isLoggedIn(): Boolean = SupabaseAuthManager.isLoggedIn

    var currentUser: ClerkUser?
        get() = SupabaseAuthManager.userId?.let { ClerkUser(it, SupabaseAuthManager.email ?: "") }
        set(value) {
            if (value == null) SupabaseAuthManager.clear() else SupabaseAuthManager.saveSession(userId = value.uid, email = value.email)
        }
}
