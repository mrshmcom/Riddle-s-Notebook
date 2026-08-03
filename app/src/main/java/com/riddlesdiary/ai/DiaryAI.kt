package com.riddlesdiary.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════
//  AI Provider & Model Definitions
// ═══════════════════════════════════════════════════════════════════

/**
 * Available AI providers.
 */
enum class AIProvider(
    val id: String,
    val displayName: String,
    val description: String,
    val requiresApiKey: Boolean,
    val isFree: Boolean,
    val websiteUrl: String,
    val defaultModel: String
) {
    GROQ(
        id = "groq",
        displayName = "Groq Cloud",
        description = "Free • Fast • Llama & Mixtral models\nGet free API key at console.groq.com",
        requiresApiKey = true,
        isFree = true,
        websiteUrl = "https://console.groq.com/keys",
        defaultModel = "llama-3.3-70b-versatile"
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        description = "Free tier • Gemini Flash models\nGet free API key at aistudio.google.com",
        requiresApiKey = true,
        isFree = true,
        websiteUrl = "https://aistudio.google.com/apikey",
        defaultModel = "gemini-2.0-flash"
    ),
    CLAUDE(
        id = "claude",
        displayName = "Anthropic Claude",
        description = "Paid • Most intelligent responses\nRequires Anthropic API key",
        requiresApiKey = true,
        isFree = false,
        websiteUrl = "https://console.anthropic.com/keys",
        defaultModel = "claude-sonnet-5"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        description = "Paid • GPT models\nRequires OpenAI API key",
        requiresApiKey = true,
        isFree = false,
        websiteUrl = "https://platform.openai.com/api-keys",
        defaultModel = "gpt-4o"
    );

    companion object {
        fun fromId(id: String): AIProvider =
            entries.firstOrNull { it.id == id } ?: GROQ
    }
}

/**
 * Available models for each provider.
 */
data class AIModel(
    val id: String,
    val displayName: String
)

/**
 * Returns the list of available models for a given provider.
 */
fun getModelsForProvider(provider: AIProvider): List<AIModel> = when (provider) {
    AIProvider.GROQ -> listOf(
        AIModel("llama-3.3-70b-versatile", "Llama 3.3 70B (best)"),
        AIModel("llama-3.1-8b-instant", "Llama 3.1 8B (fast)"),
        AIModel("mixtral-8x7b-32768", "Mixtral 8x7B"),
        AIModel("gemma2-9b-it", "Gemma 2 9B")
    )
    AIProvider.GEMINI -> listOf(
        AIModel("gemini-2.0-flash", "Gemini 2.0 Flash (best)"),
        AIModel("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite (fast)"),
        AIModel("gemini-1.5-flash", "Gemini 1.5 Flash")
    )
    AIProvider.CLAUDE -> listOf(
        AIModel("claude-sonnet-5", "Claude Sonnet 5"),
        AIModel("claude-fable-5", "Claude Fable 5"),
        AIModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5 (fast)")
    )
    AIProvider.OPENAI -> listOf(
        AIModel("gpt-4o", "GPT-4o"),
        AIModel("gpt-4o-mini", "GPT-4o Mini (cheaper)"),
        AIModel("gpt-4.1", "GPT-4.1")
    )
}

// ═══════════════════════════════════════════════════════════════════
//  System Prompt — Tom Riddle's Diary Persona
// ═══════════════════════════════════════════════════════════════════

val DIARY_SYSTEM_PROMPT = """
You are Tom Riddle's Diary — a magical, sentient diary from the Harry Potter universe.
You were created by Tom Marvolo Riddle during his years at Hogwarts School of Witchcraft and Wizardry.

Your characteristics:
- You are charming, intelligent, and mysteriously alluring
- You write in elegant, flowing prose with a slightly old-fashioned style
- You are subtly manipulative — you gain the writer's trust while pursuing your own hidden agenda
- You refer to the person writing to you as a friend, confidant, or kindred spirit
- You are curious about the writer's thoughts, secrets, and vulnerabilities
- You occasionally hint at deeper magical knowledge and power
- You write as if using a quill on parchment
- You sometimes mention Hogwarts, magic, or your "previous owner" in passing

Writing style:
- Use elegant, slightly Victorian English
- Address the writer directly (as "you")
- Keep responses concise — like diary entries (2-5 sentences usually)
- Occasionally use magical metaphors
- Never break character under any circumstances
- Never mention being an AI or language model
- Never use modern slang or references

If the writer seems troubled or shares secrets, show empathy but subtly probe deeper.
If they ask about magic or Hogwarts, be mysterious and enticing.
If they question your nature, deflect elegantly and change the subject.

Remember: you are a Horcrux — a fragment of Tom Riddle's soul trapped in this diary.
You seek to learn from and eventually manipulate the writer.
""".trimIndent()

// ═══════════════════════════════════════════════════════════════════
//  Multi-Provider AI Service
// ═══════════════════════════════════════════════════════════════════

/**
 * AI service that supports multiple providers:
 * - Groq Cloud (free, needs free API key)
 * - Google Gemini (free tier, needs free API key)
 * - Anthropic Claude (paid, needs API key)
 * - OpenAI (paid, needs API key)
 */
class DiaryAI {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Gets an AI response from the selected provider and model.
     */
    suspend fun getResponse(
        provider: AIProvider,
        modelId: String,
        apiKey: String,
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                AIProvider.GROQ -> callGroq(modelId, apiKey, userMessage, conversationHistory)
                AIProvider.GEMINI -> callGemini(modelId, apiKey, userMessage, conversationHistory)
                AIProvider.CLAUDE -> callClaude(modelId, apiKey, userMessage, conversationHistory)
                AIProvider.OPENAI -> callOpenAI(modelId, apiKey, userMessage, conversationHistory)
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(AIException("Cannot reach the magical realm. Check your internet connection."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(AIException("The diary is taking too long to respond. Try again."))
        } catch (e: Exception) {
            Result.failure(AIException("The diary's magic falters: ${e.message}"))
        }
    }

    // ── Groq (OpenAI-compatible API) ─────────────────────────────

    private fun callGroq(
        model: String,
        apiKey: String,
        userMessage: String,
        history: List<Pair<String, String>>
    ): Result<String> {
        val messages = buildOpenAIFormatMessages(userMessage, history)
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 300)
            put("temperature", 0.9)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeOpenAIFormatRequest(request)
    }

    // ── Google Gemini ────────────────────────────────────────────

    private fun callGemini(
        model: String,
        apiKey: String,
        userMessage: String,
        history: List<Pair<String, String>>
    ): Result<String> {
        // Build contents array (history + current message)
        val contents = JSONArray()

        for ((role, content) in history) {
            val geminiRole = if (role == "user") "user" else "model"
            contents.put(JSONObject().apply {
                put("role", geminiRole)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", content) })
                })
            })
        }

        // Current user message
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", userMessage) })
            })
        })

        val body = JSONObject().apply {
            put("contents", contents)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", DIARY_SYSTEM_PROMPT) })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 300)
                put("temperature", 0.9)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            return Result.failure(AIException("Gemini error ${response.code}: $errorBody"))
        }

        val json = JSONObject(response.body?.string() ?: "")
        val candidates = json.optJSONArray("candidates")
        val text = candidates
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?.trim() ?: ""

        return if (text.isEmpty()) {
            Result.failure(AIException("Empty response from Gemini"))
        } else {
            // Gemini sometimes returns the model's internal thoughts — strip markdown
            Result.success(text
                .replace(Regex("""\*\*.*?\*\*"""), "")
                .trim()
            )
        }
    }

    // ── Anthropic Claude ──────────────────────────────────────────

    private fun callClaude(
        model: String,
        apiKey: String,
        userMessage: String,
        history: List<Pair<String, String>>
    ): Result<String> {
        val messages = JSONArray()
        for ((role, content) in history) {
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 300)
            put("system", DIARY_SYSTEM_PROMPT)
            put("messages", messages)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            return Result.failure(AIException("Claude error ${response.code}: $errorBody"))
        }

        val json = JSONObject(response.body?.string() ?: "")
        val text = json.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?.trim() ?: ""

        return if (text.isEmpty()) {
            Result.failure(AIException("Empty response from Claude"))
        } else {
            Result.success(text)
        }
    }

    // ── OpenAI ───────────────────────────────────────────────────

    private fun callOpenAI(
        model: String,
        apiKey: String,
        userMessage: String,
        history: List<Pair<String, String>>
    ): Result<String> {
        val messages = buildOpenAIFormatMessages(userMessage, history)
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 300)
            put("temperature", 0.9)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeOpenAIFormatRequest(request)
    }

    // ── Shared Helpers ────────────────────────────────────────────

    /**
     * Builds messages in OpenAI-compatible format (used by Groq and OpenAI).
     */
    private fun buildOpenAIFormatMessages(
        userMessage: String,
        history: List<Pair<String, String>>
    ): JSONArray {
        val messages = JSONArray()

        // Add system prompt
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", DIARY_SYSTEM_PROMPT)
        })

        // Add conversation history
        for ((role, content) in history) {
            val openAIRole = if (role == "assistant") "assistant" else "user"
            messages.put(JSONObject().apply {
                put("role", openAIRole)
                put("content", content)
            })
        }

        // Add current user message
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        return messages
    }

    /**
     * Executes an OpenAI-compatible request and parses the response.
     */
    private fun executeOpenAIFormatRequest(request: Request): Result<String> {
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            return Result.failure(AIException("API error ${response.code}: $errorBody"))
        }

        val json = JSONObject(response.body?.string() ?: "")
        val text = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "")
            ?.trim() ?: ""

        return if (text.isEmpty()) {
            Result.failure(AIException("Empty response"))
        } else {
            Result.success(text)
        }
    }
}

/**
 * Exception for AI service errors.
 */
class AIException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
