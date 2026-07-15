package com.example.network

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
import java.util.concurrent.TimeUnit

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Dynamic model resolution to comply with gemini-api skill rules
    private const val DEFAULT_MODEL = "gemini-3.5-flash"

    suspend fun generateContent(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API Key is not configured. Falling back to local offline financial evaluation.")
            return@withContext "OFFLINE_MODE"
        }

        // Construct request JSON manually to avoid complex mapping classes issues
        val systemInstructionBlock = if (systemInstruction != null) {
            """
            ,"systemInstruction": {
                "parts": [{"text": ${escapeJson(systemInstruction)}}]
            }
            """.trimIndent()
        } else ""

        val requestJson = """
            {
                "contents": [
                    {
                        "parts": [
                            {"text": ${escapeJson(prompt)}}
                        ]
                    }
                ]
                $systemInstructionBlock
            }
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$DEFAULT_MODEL:generateContent?key=$apiKey"
        
        try {
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString == null) {
                    Log.e(TAG, "API error code ${response.code}: $bodyString")
                    return@withContext "API_ERROR: ${response.code}"
                }

                // Parse response manually to extract text safely without crashing on missing structures
                val text = extractTextFromResponse(bodyString)
                return@withContext text ?: "Could not extract analysis from AI response."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gemini API: ${e.message}", e)
            return@withContext "API_EXCEPTION: ${e.message}"
        }
    }

    private fun escapeJson(value: String): String {
        return Moshi.Builder().build().adapter(String::class.java).toJson(value)
    }

    private fun extractTextFromResponse(jsonResponse: String): String? {
        try {
            // Find "text": "..." value in JSON using regex for robust fault-tolerance
            val pattern = "\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
            val matches = pattern.findAll(jsonResponse)
            val result = StringBuilder()
            for (match in matches) {
                val groupValue = match.groups[1]?.value ?: continue
                // Unescape JSON string
                val unescaped = unescapeJson(groupValue)
                result.append(unescaped).append("\n")
            }
            if (result.isNotEmpty()) {
                return result.toString().trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}")
        }
        return null
    }

    private fun unescapeJson(escaped: String): String {
        var str = escaped
        str = str.replace("\\\"", "\"")
        str = str.replace("\\\\", "\\")
        str = str.replace("\\n", "\n")
        str = str.replace("\\r", "\r")
        str = str.replace("\\t", "\t")
        return str
    }
}
