package com.example.data.ai

import com.example.data.model.ApiConnection
import com.example.data.model.ApiProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class ProviderException(message: String) : Exception(message) {
    class QuotaExceededException(message: String) : ProviderException(message)
    class RateLimitException(message: String) : ProviderException(message)
    class InvalidAuthException(message: String) : ProviderException(message)
    class ProviderServerException(message: String) : ProviderException(message)
    class UnsupportedModelException(message: String) : ProviderException(message)
    class NetworkException(message: String) : ProviderException(message)
    class CircuitBreakerTrippedException(message: String) : ProviderException(message)
}

data class CircuitState(
    var consecutiveFailures: Int = 0,
    var lastFailureTimestamp: Long = 0L,
    var isTripped: Boolean = false
)

object MultiProviderEngine {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val circuitBreakers = java.util.concurrent.ConcurrentHashMap<String, CircuitState>()

    // Global in-memory telemetry
    var totalRequestsHandled: Long = 0L
        private set
    var totalSuccessfulRequests: Long = 0L
        private set
    var totalFailedRequests: Long = 0L
        private set
    var totalEstimatedTokens: Long = 0L
        private set

    val providerRegistry: Map<String, com.example.data.model.ProviderCapability> = mapOf(
        ApiProviderType.GEMINI.name to com.example.data.model.ProviderCapability(
            provider = ApiProviderType.GEMINI,
            supportsGrounding = true,
            supportsCodeGeneration = true,
            supportsStreaming = true,
            contextWindowTokens = 1048576,
            maxOutputTokens = 8192,
            rateLimitRpm = 15,
            costPer1MInputTokensUsd = 0.075,
            costPer1MOutputTokensUsd = 0.30
        ),
        ApiProviderType.OPENAI.name to com.example.data.model.ProviderCapability(
            provider = ApiProviderType.OPENAI,
            supportsGrounding = false,
            supportsCodeGeneration = true,
            supportsStreaming = true,
            contextWindowTokens = 128000,
            maxOutputTokens = 16384,
            rateLimitRpm = 500,
            costPer1MInputTokensUsd = 2.50,
            costPer1MOutputTokensUsd = 10.00
        ),
        ApiProviderType.CLAUDE.name to com.example.data.model.ProviderCapability(
            provider = ApiProviderType.CLAUDE,
            supportsGrounding = false,
            supportsCodeGeneration = true,
            supportsStreaming = true,
            contextWindowTokens = 200000,
            maxOutputTokens = 8192,
            rateLimitRpm = 50,
            costPer1MInputTokensUsd = 3.00,
            costPer1MOutputTokensUsd = 15.00
        ),
        ApiProviderType.OPENAI_COMPATIBLE.name to com.example.data.model.ProviderCapability(
            provider = ApiProviderType.OPENAI_COMPATIBLE,
            supportsGrounding = false,
            supportsCodeGeneration = true,
            supportsStreaming = true,
            contextWindowTokens = 131072,
            maxOutputTokens = 8192,
            rateLimitRpm = 30,
            costPer1MInputTokensUsd = 0.59,
            costPer1MOutputTokensUsd = 0.79
        )
    )

    fun maskKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.length <= 8) return "••••••••"
        return trimmed.take(4) + "••••••••" + trimmed.takeLast(4)
    }

    fun hashKey(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun checkCircuit(providerKey: String) {
        val state = circuitBreakers[providerKey] ?: return
        if (state.isTripped) {
            val elapsed = System.currentTimeMillis() - state.lastFailureTimestamp
            if (elapsed < 60_000L) {
                val remSeconds = (60_000L - elapsed) / 1000
                throw ProviderException.CircuitBreakerTrippedException(
                    "Circuit breaker tripped for $providerKey due to consecutive failures. Cooling down (${remSeconds}s remaining)."
                )
            } else {
                state.isTripped = false
            }
        }
    }

    private fun recordFailure(providerKey: String) {
        val state = circuitBreakers.computeIfAbsent(providerKey) { CircuitState() }
        state.consecutiveFailures++
        state.lastFailureTimestamp = System.currentTimeMillis()
        if (state.consecutiveFailures >= 3) {
            state.isTripped = true
        }
    }

    private fun recordSuccess(providerKey: String) {
        val state = circuitBreakers[providerKey] ?: return
        state.consecutiveFailures = 0
        state.isTripped = false
    }

    suspend fun testConnection(connection: ApiConnection, rawApiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = executePrompt(
                connection = connection,
                rawApiKey = rawApiKey,
                systemInstruction = "You are an API validator. Respond with 'OK'.",
                userPrompt = "Test connection ping"
            )
            Result.success("Connection verified. Response: ${response.take(100)}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executePrompt(
        connection: ApiConnection,
        rawApiKey: String,
        systemInstruction: String,
        userPrompt: String,
        temperature: Float = 0.4f
    ): String = withContext(Dispatchers.IO) {
        val providerKey = "${connection.provider}:${connection.selectedModel}"
        checkCircuit(providerKey)
        totalRequestsHandled++

        var attempt = 0
        val maxAttempts = 2
        var lastException: Exception? = null

        while (attempt <= maxAttempts) {
            try {
                val start = System.currentTimeMillis()
                val result = when (connection.provider) {
                    ApiProviderType.GEMINI.name -> executeGemini(connection, rawApiKey, systemInstruction, userPrompt, temperature)
                    ApiProviderType.OPENAI.name -> executeOpenAI(connection, rawApiKey, systemInstruction, userPrompt, temperature)
                    ApiProviderType.CLAUDE.name -> executeClaude(connection, rawApiKey, systemInstruction, userPrompt, temperature)
                    ApiProviderType.OPENAI_COMPATIBLE.name -> executeOpenAICompatible(connection, rawApiKey, systemInstruction, userPrompt, temperature)
                    else -> executeOpenAICompatible(connection, rawApiKey, systemInstruction, userPrompt, temperature)
                }
                recordSuccess(providerKey)
                totalSuccessfulRequests++
                val estTokens = (userPrompt.length + result.length) / 4
                totalEstimatedTokens += estTokens
                return@withContext result
            } catch (e: ProviderException.ProviderServerException) {
                lastException = e
                attempt++
                if (attempt <= maxAttempts) {
                    val backoff = (1000L * (1 shl attempt)) + (Math.random() * 400).toLong()
                    kotlinx.coroutines.delay(backoff)
                }
            } catch (e: Exception) {
                recordFailure(providerKey)
                totalFailedRequests++
                throw e
            }
        }

        recordFailure(providerKey)
        totalFailedRequests++
        throw (lastException ?: ProviderException.NetworkException("Request failed after retries"))
    }

    private fun executeGemini(
        conn: ApiConnection,
        apiKey: String,
        systemInstruction: String,
        prompt: String,
        temperature: Float
    ): String {
        val model = conn.selectedModel.ifBlank { "gemini-3.8-flash" }
        val baseUrl = conn.customBaseUrl?.ifBlank { null } ?: "https://generativelanguage.googleapis.com/"
        val endpoint = "${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent?key=${apiKey.trim()}"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                put("maxOutputTokens", 8192)
            })
            if (systemInstruction.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val code = response.code
        val bodyString = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            classifyAndThrow(code, bodyString, "Gemini ($model)")
        }

        val resJson = JSONObject(bodyString)
        val candidates = resJson.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text")
        return text ?: "No output received from Gemini"
    }

    private fun executeOpenAI(
        conn: ApiConnection,
        apiKey: String,
        systemInstruction: String,
        prompt: String,
        temperature: Float
    ): String {
        val model = conn.selectedModel.ifBlank { "gpt-4o" }
        val baseUrl = conn.customBaseUrl?.ifBlank { null } ?: "https://api.openai.com/v1/"
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"

        val messages = JSONArray().apply {
            if (systemInstruction.isNotBlank()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val code = response.code
        val bodyString = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            classifyAndThrow(code, bodyString, "OpenAI ($model)")
        }

        val resJson = JSONObject(bodyString)
        val choices = resJson.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0)
        val message = firstChoice?.optJSONObject("message")
        return message?.optString("content") ?: "No output received from OpenAI"
    }

    private fun executeClaude(
        conn: ApiConnection,
        apiKey: String,
        systemInstruction: String,
        prompt: String,
        temperature: Float
    ): String {
        val model = conn.selectedModel.ifBlank { "claude-3-5-sonnet-20241022" }
        val baseUrl = conn.customBaseUrl?.ifBlank { null } ?: "https://api.anthropic.com/v1/"
        val endpoint = "${baseUrl.trimEnd('/')}/messages"

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", 8192)
            put("messages", messages)
            put("temperature", temperature)
            if (systemInstruction.isNotBlank()) {
                put("system", systemInstruction)
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("x-api-key", apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val code = response.code
        val bodyString = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            classifyAndThrow(code, bodyString, "Claude ($model)")
        }

        val resJson = JSONObject(bodyString)
        val content = resJson.optJSONArray("content")
        val firstBlock = content?.optJSONObject(0)
        return firstBlock?.optString("text") ?: "No output received from Claude"
    }

    private fun executeOpenAICompatible(
        conn: ApiConnection,
        apiKey: String,
        systemInstruction: String,
        prompt: String,
        temperature: Float
    ): String {
        val model = conn.selectedModel.ifBlank { "llama-3.3-70b" }
        val baseUrl = conn.customBaseUrl?.ifBlank { null } ?: "https://api.groq.com/openai/v1/"
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"

        val messages = JSONArray().apply {
            if (systemInstruction.isNotBlank()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val code = response.code
        val bodyString = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            classifyAndThrow(code, bodyString, "Endpoint ($model)")
        }

        val resJson = JSONObject(bodyString)
        val choices = resJson.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0)
        val message = firstChoice?.optJSONObject("message")
        return message?.optString("content") ?: "No output received"
    }

    private fun classifyAndThrow(statusCode: Int, body: String, providerLabel: String): Nothing {
        val lowerBody = body.lowercase()
        when {
            statusCode == 429 || lowerBody.contains("quota") || lowerBody.contains("resource_exhausted") -> {
                throw ProviderException.QuotaExceededException(
                    "API Quota Exceeded on $providerLabel (HTTP 429). Completed work safely saved. Add or select another API key to continue without losing progress."
                )
            }
            statusCode == 401 || statusCode == 403 || lowerBody.contains("invalid_api_key") || lowerBody.contains("permission_denied") -> {
                throw ProviderException.InvalidAuthException(
                    "Authentication Failed on $providerLabel (HTTP $statusCode). Please check your API key."
                )
            }
            statusCode >= 500 -> {
                throw ProviderException.ProviderServerException(
                    "Upstream Provider Outage on $providerLabel (HTTP $statusCode). Progress checkpointed."
                )
            }
            else -> {
                throw ProviderException.NetworkException(
                    "Provider request failed on $providerLabel (HTTP $statusCode): ${body.take(200)}"
                )
            }
        }
    }
}
