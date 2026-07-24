package com.example.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

/**
 * Data class representing a Supabase Auth User.
 * Replaces FirebaseUser in the codebase.
 */
data class SupabaseUser(
    val uid: String,
    val email: String
)

/**
 * Singleton manager for Supabase Authentication.
 * Replaces FirebaseAuthManager with support for Supabase REST API & SharedPreferences session persistence.
 */
object SupabaseAuthManager {
    private const val TAG = "SupabaseAuthManager"
    private const val PREFS_NAME = "supabase_auth_prefs"
    private const val KEY_UID = "supabase_uid"
    private const val KEY_EMAIL = "supabase_email"

    private var prefs: SharedPreferences? = null
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Status message for the UI
    var statusMessage: String = "Uninitialized"
        private set

    /** The currently signed-in Supabase user, or null if no session is active. */
    var currentUser: SupabaseUser? = null
        private set

    /** Initialize SharedPreferences and restore any existing session. */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUid = prefs?.getString(KEY_UID, null)
        val savedEmail = prefs?.getString(KEY_EMAIL, null)

        if (savedUid != null && savedEmail != null) {
            currentUser = SupabaseUser(savedUid, savedEmail)
            statusMessage = "Session Restored (Offline)"
            Log.d(TAG, "Restored cached session: $savedEmail ($savedUid)")
        } else {
            statusMessage = "No active session"
        }
    }

    /** Helper to check if credentials are valid/configured. */
    private fun isConfigured(): Boolean {
        val url = getSupabaseUrl()
        val key = getSupabaseKey()
        return url.isNotEmpty() && !url.contains("your-supabase-url") && key.isNotEmpty() && !key.contains("MY_GEMINI")
    }

    private fun getSupabaseUrl(): String {
        return try {
            BuildConfig.SUPABASE_URL
        } catch (e: Throwable) {
            ""
        }
    }

    private fun getSupabaseKey(): String {
        return try {
            BuildConfig.SUPABASE_KEY
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Registers a new user with Supabase.
     */
    suspend fun register(email: String, password: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            // Mock Sandbox Mode
            val mockUid = UUID.randomUUID().toString()
            val mockUser = SupabaseUser(mockUid, email)
            saveSession(mockUser)
            statusMessage = "Mock Sandbox Registered"
            Log.i(TAG, "[Sandbox Mode] Created mock Supabase account for $email, uid=$mockUid")
            return@withContext Result.success(mockUser)
        }

        val url = "${getSupabaseUrl()}/auth/v1/signup"
        val requestBody = mapOf("email" to email, "password" to password)
        val jsonPayload = moshi.adapter(Map::class.java).toJson(requestBody)

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", getSupabaseKey())
            .addHeader("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = parseError(bodyStr) ?: "Signup failed: ${response.code} ${response.message}"
                    return@withContext Result.failure(Exception(errMsg))
                }

                // Parse user from signup response
                val parsed = parseUserFromResponse(bodyStr)
                if (parsed != null) {
                    saveSession(parsed)
                    statusMessage = "Registered Successfully"
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("Registration succeeded but user details could not be parsed."))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error during registration: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticates an existing user in Supabase.
     */
    suspend fun login(email: String, password: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            // Mock Sandbox Mode
            val mockUid = UUID.nameUUIDFromBytes(email.toByteArray()).toString()
            val mockUser = SupabaseUser(mockUid, email)
            saveSession(mockUser)
            statusMessage = "Mock Sandbox Logged In"
            Log.i(TAG, "[Sandbox Mode] Mock login success for $email, uid=$mockUser")
            return@withContext Result.success(mockUser)
        }

        val url = "${getSupabaseUrl()}/auth/v1/token?grant_type=password"
        val requestBody = mapOf("email" to email, "password" to password)
        val jsonPayload = moshi.adapter(Map::class.java).toJson(requestBody)

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", getSupabaseKey())
            .addHeader("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = parseError(bodyStr) ?: "Invalid email or password."
                    return@withContext Result.failure(Exception(errMsg))
                }

                val parsed = parseUserFromResponse(bodyStr)
                if (parsed != null) {
                    saveSession(parsed)
                    statusMessage = "Authenticated"
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("Login succeeded but user details could not be parsed."))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error during login: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Triggers a password recovery email.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            Log.i(TAG, "[Sandbox Mode] Mock password reset email sent to $email")
            return@withContext Result.success(Unit)
        }

        val url = "${getSupabaseUrl()}/auth/v1/recover"
        val requestBody = mapOf("email" to email)
        val jsonPayload = moshi.adapter(Map::class.java).toJson(requestBody)

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", getSupabaseKey())
            .addHeader("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = parseError(bodyStr) ?: "Password reset failed: ${response.code}"
                    return@withContext Result.failure(Exception(errMsg))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error during password reset: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears local user session from memory and SharedPreferences.
     */
    fun logout() {
        currentUser = null
        statusMessage = "Logged Out"
        prefs?.edit()?.clear()?.apply()
        Log.d(TAG, "User logged out. Session cleared.")
    }

    /**
     * Returns true if there is an active authenticated session.
     */
    fun isLoggedIn(): Boolean = currentUser != null

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private fun saveSession(user: SupabaseUser) {
        currentUser = user
        prefs?.edit()?.apply {
            putString(KEY_UID, user.uid)
            putString(KEY_EMAIL, user.email)
            apply()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseUserFromResponse(json: String): SupabaseUser? {
        return try {
            val map = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any>

            // GoTrue login response embeds user inside a nested "user" object.
            // GoTrue signup response can have "user" nested or user properties directly.
            val userMap = map?.get("user") as? Map<String, Any> ?: map

            val id = userMap?.get("id") as? String
            val email = userMap?.get("email") as? String

            if (id != null && email != null) {
                SupabaseUser(id, email)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse user JSON: ${e.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseError(json: String): String? {
        return try {
            val map = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any>
            map?.get("error_description") as? String
                ?: map?.get("msg") as? String
                ?: map?.get("message") as? String
        } catch (e: Exception) {
            null
        }
    }
}
