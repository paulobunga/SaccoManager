package com.litesails.saccomanager.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a signed-in user via Clerk.
 */
data class ClerkUser(
    val uid: String,   // Clerk userId (user_xxx)
    val email: String
)

/**
 * Auth manager backed by Clerk.
 *
 * Token sharing with Supabase:
 *   1. Clerk issues a JWT using a custom "supabase" JWT template configured
 *      in the Clerk Dashboard.
 *   2. [getSupabaseToken] returns the current session token when available.
 *   3. Pass it as `Authorization: Bearer <token>` on Supabase REST calls
 *      so Supabase Row Level Security can verify the caller's identity.
 *
 * Setup:
 *   - Add CLERK_PUBLISHABLE_KEY to .env
 *   - Call [initialize] once from Application or MainActivity.onCreate().
 */
object ClerkAuthManager {
    private const val TAG = "ClerkAuthManager"
    private const val PREFS_NAME = "clerk_auth_prefs"
    private const val KEY_UID = "clerk_uid"
    private const val KEY_EMAIL = "clerk_email"

    private var prefs: SharedPreferences? = null

    var statusMessage: String = "Uninitialized"
        private set

    /** Currently authenticated user, or null when signed out. */
    var currentUser: ClerkUser? = null
        private set

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Must be called once before any auth operations (e.g. in MainActivity.onCreate).
     * Restores a cached session so the user stays logged in across app restarts.
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Restore cached session if present
        val savedUid = prefs?.getString(KEY_UID, null)
        val savedEmail = prefs?.getString(KEY_EMAIL, null)
        if (savedUid != null && savedEmail != null) {
            currentUser = ClerkUser(savedUid, savedEmail)
            statusMessage = "Session Restored"
            Log.d(TAG, "Restored cached Clerk session: $savedEmail ($savedUid)")
        } else {
            statusMessage = "No active session"
        }
    }

    // -------------------------------------------------------------------------
    // Auth Operations
    // -------------------------------------------------------------------------

    /**
     * Register a new user via Clerk.
     *
     * IMPORTANT: This requires the real Clerk SDK/backend to be configured.
     * This method no longer uses local sandbox stubs.
     */
    suspend fun register(email: String, password: String): Result<ClerkUser> = withContext(Dispatchers.IO) {
        Result.failure(IllegalStateException("Clerk registration is not configured in this build."))
    }

    /**
     * Sign in an existing user via Clerk.
     *
     * IMPORTANT: This requires the real Clerk SDK/backend to be configured.
     * This method no longer uses local sandbox stubs.
     */
    suspend fun login(email: String, password: String): Result<ClerkUser> = withContext(Dispatchers.IO) {
        Result.failure(IllegalStateException("Clerk sign-in is not configured in this build."))
    }

    /**
     * Send a password reset email via Clerk.
     *
     * IMPORTANT: This requires the real Clerk SDK/backend to be configured.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        Result.failure(IllegalStateException("Clerk password reset is not configured in this build."))
    }

    /**
     * Fetch a short-lived JWT from Clerk's "supabase" template.
     * Attach this as `Authorization: Bearer <token>` on all Supabase REST requests
     * so Supabase Row Level Security can verify the caller's identity.
     *
     * Returns null if not signed in or if the integration is not configured.
     */
    suspend fun getSupabaseToken(): String? = null

    /**
     * Sign the current user out.
     */
    fun logout() {
        currentUser = null
        statusMessage = "Logged Out"
        prefs?.edit()?.clear()?.apply()
        Log.d(TAG, "User signed out.")
    }

    fun isLoggedIn(): Boolean = currentUser != null

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun saveSession(user: ClerkUser) {
        currentUser = user
        prefs?.edit()?.apply {
            putString(KEY_UID, user.uid)
            putString(KEY_EMAIL, user.email)
            apply()
        }
    }
}
