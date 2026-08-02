package com.litesails.saccomanager.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.clerk.android.Clerk
import com.clerk.android.resource.SignIn
import com.clerk.android.resource.SignUp
import com.litesails.saccomanager.BuildConfig
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
 * Auth manager backed by Clerk Android SDK.
 *
 * Token sharing with Supabase:
 *   1. Clerk issues a JWT using a custom "supabase" JWT template configured
 *      in the Clerk Dashboard (Settings → JWT Templates → New template → Supabase).
 *   2. [getSupabaseToken] fetches this short-lived token.
 *   3. Pass it as the Authorization: Bearer header on every Supabase REST call.
 *      Supabase trusts Clerk tokens once the Clerk JWKS URL is configured in
 *      the Supabase project: Authentication → JWT Settings → JWKS URL =
 *      https://clerk.your-domain.com/.well-known/jwks.json
 *
 * Setup:
 *   - Add CLERK_PUBLISHABLE_KEY to .env (and .env.example).
 *   - Call [initialize] once from Application or MainActivity.onCreate().
 */
object ClerkAuthManager {
    private const val TAG = "ClerkAuthManager"
    private const val PREFS_NAME = "clerk_auth_prefs"
    private const val KEY_UID = "clerk_uid"
    private const val KEY_EMAIL = "clerk_email"

    /** Name of the JWT template defined in the Clerk Dashboard for Supabase. */
    private const val SUPABASE_JWT_TEMPLATE = "supabase"

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

        // Sync with live Clerk session (non-blocking; updates currentUser if active)
        val clerk = getClerk() ?: return
        val activeUser = clerk.user
        if (activeUser != null) {
            val email = activeUser.primaryEmailAddress?.emailAddress ?: savedEmail ?: ""
            val user = ClerkUser(activeUser.id, email)
            saveSession(user)
            statusMessage = "Session Active"
        }
    }

    // -------------------------------------------------------------------------
    // Auth Operations
    // -------------------------------------------------------------------------

    /**
     * Register a new user with Clerk email/password sign-up.
     */
    suspend fun register(email: String, password: String): Result<ClerkUser> = withContext(Dispatchers.IO) {
        val clerk = getClerk() ?: return@withContext sandboxRegister(email)

        runCatching {
            val signUp: SignUp = clerk.signUp(
                emailAddress = email,
                password = password
            )
            val uid = signUp.createdUserId
                ?: throw IllegalStateException("Clerk sign-up succeeded but no userId returned.")
            val user = ClerkUser(uid, email)
            saveSession(user)
            statusMessage = "Registered Successfully"
            Log.i(TAG, "Clerk sign-up success for $email, uid=$uid")
            user
        }.onFailure { e ->
            Log.e(TAG, "Clerk sign-up failed: ${e.message}")
        }
    }

    /**
     * Sign in an existing user with Clerk email/password.
     */
    suspend fun login(email: String, password: String): Result<ClerkUser> = withContext(Dispatchers.IO) {
        val clerk = getClerk() ?: return@withContext sandboxLogin(email)

        runCatching {
            val signIn: SignIn = clerk.signIn(
                identifier = email,
                password = password
            )
            val uid = signIn.createdSessionId
                ?: throw IllegalStateException("Clerk sign-in succeeded but no sessionId returned.")
            val clerkUser = clerk.user
            val resolvedUid = clerkUser?.id ?: uid
            val user = ClerkUser(resolvedUid, email)
            saveSession(user)
            statusMessage = "Authenticated"
            Log.i(TAG, "Clerk login success for $email")
            user
        }.onFailure { e ->
            Log.e(TAG, "Clerk login failed: ${e.message}")
        }
    }

    /**
     * Send a password reset email via Clerk.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clerk = getClerk() ?: run {
            Log.i(TAG, "[Sandbox] Mock password reset for $email")
            return@withContext Result.success(Unit)
        }

        runCatching {
            clerk.resetPassword(emailAddress = email)
            Log.i(TAG, "Clerk password reset email sent to $email")
        }.onFailure { e ->
            Log.e(TAG, "Clerk password reset failed: ${e.message}")
        }
    }

    /**
     * Fetch a short-lived JWT from Clerk's "supabase" template.
     * Attach this as `Authorization: Bearer <token>` on all Supabase REST requests
     * so Supabase Row Level Security can verify the caller's identity.
     *
     * Returns null if not signed in or if the SDK is not configured.
     */
    suspend fun getSupabaseToken(): String? = withContext(Dispatchers.IO) {
        try {
            val clerk = getClerk() ?: return@withContext null
            clerk.getToken(template = SUPABASE_JWT_TEMPLATE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch Supabase token from Clerk: ${e.message}")
            null
        }
    }

    /**
     * Sign the current user out.
     */
    fun logout() {
        try { getClerk()?.signOut() } catch (_: Exception) {}
        currentUser = null
        statusMessage = "Logged Out"
        prefs?.edit()?.clear()?.apply()
        Log.d(TAG, "User signed out.")
    }

    fun isLoggedIn(): Boolean = currentUser != null

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun getClerk(): Clerk? {
        return try {
            val key = BuildConfig.CLERK_PUBLISHABLE_KEY
            if (key.isBlank() || key.startsWith("pk_test_placeholder")) null
            else Clerk.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveSession(user: ClerkUser) {
        currentUser = user
        prefs?.edit()?.apply {
            putString(KEY_UID, user.uid)
            putString(KEY_EMAIL, user.email)
            apply()
        }
    }

    // Sandbox fallbacks when Clerk is not yet configured
    private fun sandboxRegister(email: String): Result<ClerkUser> {
        val user = ClerkUser("sandbox-${email.hashCode()}", email)
        saveSession(user)
        statusMessage = "Sandbox Registered"
        Log.i(TAG, "[Sandbox] Mock Clerk registration for $email")
        return Result.success(user)
    }

    private fun sandboxLogin(email: String): Result<ClerkUser> {
        val user = ClerkUser("sandbox-${email.hashCode()}", email)
        saveSession(user)
        statusMessage = "Sandbox Authenticated"
        Log.i(TAG, "[Sandbox] Mock Clerk login for $email")
        return Result.success(user)
    }
}
