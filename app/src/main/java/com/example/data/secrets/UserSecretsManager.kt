package com.example.data.secrets

import android.content.Context
import android.content.SharedPreferences
import com.example.data.ai.GeminiController
import com.example.data.model.ProjectSecret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object UserSecretsManager {

    private const val PREFS_NAME = "omni_user_secrets"
    private const val KEY_GEMINI_API_KEY = "user_gemini_api_key"
    private const val KEY_PROJECT_SECRETS_PREFIX = "proj_secrets_"

    private var prefs: SharedPreferences? = null

    private val _geminiApiKeyFlow = MutableStateFlow("")
    val geminiApiKeyFlow: StateFlow<String> = _geminiApiKeyFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedKey = prefs?.getString(KEY_GEMINI_API_KEY, "") ?: ""
            _geminiApiKeyFlow.value = savedKey
            if (savedKey.isNotBlank()) {
                GeminiController.setCustomApiKey(savedKey)
            }
        }
    }

    fun getGeminiApiKey(): String {
        return _geminiApiKeyFlow.value.ifBlank {
            prefs?.getString(KEY_GEMINI_API_KEY, "") ?: ""
        }
    }

    fun saveGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        prefs?.edit()?.putString(KEY_GEMINI_API_KEY, trimmed)?.apply()
        _geminiApiKeyFlow.value = trimmed
        GeminiController.setCustomApiKey(trimmed.ifBlank { null })
    }

    fun clearGeminiApiKey() {
        prefs?.edit()?.remove(KEY_GEMINI_API_KEY)?.apply()
        _geminiApiKeyFlow.value = ""
        GeminiController.setCustomApiKey(null)
    }

    fun getProjectSecrets(projectId: String): List<ProjectSecret> {
        val jsonStr = prefs?.getString("$KEY_PROJECT_SECRETS_PREFIX$projectId", null)
        val defaultList = mutableListOf(
            ProjectSecret(
                id = "${projectId}_gemini",
                projectId = projectId,
                key = "GEMINI_API_KEY",
                value = getGeminiApiKey().ifBlank { "AIzaSy••••••••••••••••••••" },
                description = "Google Gemini / AI Studio Personal Key"
            ),
            ProjectSecret(
                id = "${projectId}_env",
                projectId = projectId,
                key = "APP_ENV",
                value = "production",
                description = "Runtime Deployment Environment"
            )
        )

        if (jsonStr.isNullOrBlank()) {
            return defaultList
        }

        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ProjectSecret>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ProjectSecret(
                        id = obj.optString("id", "${projectId}_secret_$i"),
                        projectId = projectId,
                        key = obj.optString("key", ""),
                        value = obj.optString("value", ""),
                        description = obj.optString("description", "")
                    )
                )
            }
            // Ensure GEMINI_API_KEY reflects current key
            val geminiIdx = list.indexOfFirst { it.key == "GEMINI_API_KEY" }
            val currentKey = getGeminiApiKey()
            if (geminiIdx >= 0 && currentKey.isNotBlank()) {
                list[geminiIdx] = list[geminiIdx].copy(value = currentKey)
            } else if (geminiIdx < 0) {
                list.add(
                    0,
                    ProjectSecret(
                        id = "${projectId}_gemini",
                        projectId = projectId,
                        key = "GEMINI_API_KEY",
                        value = currentKey.ifBlank { "AIzaSy••••••••••••••••••••" },
                        description = "Google Gemini / AI Studio Personal Key"
                    )
                )
            }
            return list
        } catch (e: Exception) {
            return defaultList
        }
    }

    fun saveProjectSecrets(projectId: String, secrets: List<ProjectSecret>) {
        val arr = JSONArray()
        for (s in secrets) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("projectId", projectId)
                put("key", s.key)
                put("value", s.value)
                put("description", s.description)
            }
            arr.put(obj)
            // If GEMINI_API_KEY is updated here, sync with global key as well
            if (s.key.equals("GEMINI_API_KEY", ignoreCase = true) && s.value.isNotBlank() && !s.value.contains("••••")) {
                saveGeminiApiKey(s.value)
            }
        }
        prefs?.edit()?.putString("$KEY_PROJECT_SECRETS_PREFIX$projectId", arr.toString())?.apply()
    }
}
