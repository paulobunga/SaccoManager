package com.litesails.saccomanager.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SupabaseAuthManager {
    private const val TAG = "SupabaseAuthManager"
    private const val PREFS_NAME = "supabase_auth"

    private var prefs: SharedPreferences? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (!initialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            initialized = true
        }
    }

    private fun prefs(): SharedPreferences? = prefs

    var accessToken: String?
        get() = prefs()?.getString("access_token", null)
        set(value) { prefs()?.edit()?.putString("access_token", value)?.apply() }

    var refreshToken: String?
        get() = prefs()?.getString("refresh_token", null)
        set(value) { prefs()?.edit()?.putString("refresh_token", value)?.apply() }

    var userId: String?
        get() = prefs()?.getString("user_id", null)
        set(value) { prefs()?.edit()?.putString("user_id", value)?.apply() }

    var role: String?
        get() = prefs()?.getString("role", null)
        set(value) { prefs()?.edit()?.putString("role", value)?.apply() }

    var membershipNumber: String?
        get() = prefs()?.getString("membership_number", null)
        set(value) { prefs()?.edit()?.putString("membership_number", value)?.apply() }

    var status: String?
        get() = prefs()?.getString("status", null)
        set(value) { prefs()?.edit()?.putString("status", value)?.apply() }

    var name: String?
        get() = prefs()?.getString("name", null)
        set(value) { prefs()?.edit()?.putString("name", value)?.apply() }

    var email: String?
        get() = prefs()?.getString("email", null)
        set(value) { prefs()?.edit()?.putString("email", value)?.apply() }

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()

    fun saveSession(
        token: String? = null,
        refresh: String? = null,
        userId: String? = null,
        role: String? = null,
        membershipNumber: String? = null,
        status: String? = null,
        name: String? = null,
        email: String? = null
    ) {
        this.accessToken = token
        this.refreshToken = refresh
        this.userId = userId
        this.role = role
        this.membershipNumber = membershipNumber
        this.status = status
        this.name = name
        this.email = email
    }

    fun clear() {
        prefs()?.edit()?.clear()?.apply()
        Log.i(TAG, "Session cleared.")
    }

    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        role: String = "MEMBER"
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val functionUrl = "${BuildConfig.SUPABASE_URL}/functions/v1/auth-signup"
            val json = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("name", fullName)
                .put("phone", phone)
                .put("role", role)
                .toString()
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(functionUrl)
                .post(body)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val jsonObject = JSONObject(responseBody)

            if (response.isSuccessful && jsonObject.optBoolean("success")) {
                val userMap = jsonObject.optJSONObject("user") ?: JSONObject()
                Result.success(
                    mapOf(
                        "id" to userMap.optString("id"),
                        "email" to userMap.optString("email"),
                        "name" to userMap.optString("name"),
                        "role" to userMap.optString("role"),
                        "status" to userMap.optString("status"),
                        "membershipNumber" to userMap.optString("membershipNumber")
                    )
                )
            } else {
                Result.failure(IllegalStateException(jsonObject.optString("message", "Sign up failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val functionUrl = "${BuildConfig.SUPABASE_URL}/functions/v1/auth-login"
            val json = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(functionUrl)
                .post(body)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val jsonObject = JSONObject(responseBody)

            if (response.isSuccessful && jsonObject.optBoolean("success")) {
                val user = jsonObject.optJSONObject("user")
                val session = jsonObject.optJSONObject("session")
                saveSession(
                    token = session?.optString("access_token"),
                    refresh = session?.optString("refresh_token"),
                    userId = user?.optString("id"),
                    role = user?.optString("role"),
                    membershipNumber = user?.optString("membershipNumber"),
                    status = user?.optString("status"),
                    name = user?.optString("name"),
                    email = user?.optString("email")
                )
                Result.success(
                    mapOf(
                        "id" to user?.optString("id"),
                        "email" to user?.optString("email"),
                        "name" to user?.optString("name"),
                        "role" to user?.optString("role"),
                        "status" to user?.optString("status"),
                        "membershipNumber" to user?.optString("membershipNumber"),
                        "access_token" to session?.optString("access_token")
                    )
                )
            } else {
                Result.failure(IllegalStateException(jsonObject.optString("message", "Login failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val functionUrl = "${BuildConfig.SUPABASE_URL}/functions/v1/auth-send-password-reset"
            val json = JSONObject().put("email", email).toString()
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(functionUrl)
                .post(body)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val jsonObject = JSONObject(responseBody)

            if (response.isSuccessful && jsonObject.optBoolean("success")) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(jsonObject.optString("message", "Password reset failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        clear()
    }
}
