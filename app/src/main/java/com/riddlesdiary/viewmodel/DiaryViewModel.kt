package com.riddlesdiary.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riddlesdiary.ai.AIException
import com.riddlesdiary.ai.AIModel
import com.riddlesdiary.ai.AIProvider
import com.riddlesdiary.ai.DiaryAI
import com.riddlesdiary.ai.getModelsForProvider
import com.riddlesdiary.recognition.HandwritingRecognizer
import com.riddlesdiary.recognition.StrokeData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the diary screen.
 */
data class DiaryUiState(
    val status: DiaryStatus = DiaryStatus.Idle,
    val aiResponse: String = "",
    val displayedResponse: String = "",
    val errorMessage: String? = null,
    val inkAlpha: Float = 1f,
    val isDrawingEnabled: Boolean = true,
    val isModelReady: Boolean = false,
    val strokeCount: Int = 0,
    val canvasClearToken: Int = 0,

    // Provider settings
    val selectedProvider: AIProvider = AIProvider.GROQ,
    val selectedModelId: String = AIProvider.GROQ.defaultModel,
    val apiKey: String = "",

    val conversationHistory: List<Pair<String, String>> = emptyList(),

    // Fallback: true if ML Kit handwriting recognition is permanently unavailable
    val recognitionUnavailable: Boolean = false,
    val typedText: String = ""
)

/**
 * Possible states of the diary.
 */
sealed class DiaryStatus {
    data object Idle : DiaryStatus()
    data object Writing : DiaryStatus()
    data object Fading : DiaryStatus()
    data object Recognizing : DiaryStatus()
    data object Thinking : DiaryStatus()
    data object Responding : DiaryStatus()
    data class Error(val message: String) : DiaryStatus()
}

/**
 * ViewModel for the Riddle's Notebook app.
 * Coordinates handwriting recognition and multi-provider AI responses.
 */
class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private val recognizer = HandwritingRecognizer()
    private val diaryAI = DiaryAI()

    private val strokes = mutableListOf<StrokeData>()
    private var typewriterJob: Job? = null

    private val prefs = application.getSharedPreferences("riddle_notebook_prefs", Context.MODE_PRIVATE)

    init {
        loadSettings()
        downloadModel()
    }

    /**
     * Loads saved settings from SharedPreferences, with fallback to
     * the default API key compiled into the app via BuildConfig.
     *
     * Priority:
     * 1. User-saved preference (if they changed it) → SharedPreferences
     * 2. Default compiled key (from developer setup) → BuildConfig
     * 3. Empty (app will prompt) → ""
     */
    private fun loadSettings() {
        // Check if user has previously chosen a different provider
        val providerId = prefs.getString("provider", null)

        if (providerId != null) {
            // User has explicitly chosen a provider — use their preference
            val provider = AIProvider.fromId(providerId)
            val modelId = prefs.getString("model_${provider.id}", provider.defaultModel) ?: provider.defaultModel
            val apiKey = prefs.getString("apikey_${provider.id}", "") ?: ""

            _uiState.update {
                it.copy(
                    selectedProvider = provider,
                    selectedModelId = modelId,
                    apiKey = apiKey
                )
            }
        } else {
            // First launch — use the compiled-in default Groq key
            val defaultKey = com.riddlesdiary.BuildConfig.DEFAULT_GROQ_API_KEY
            if (defaultKey.isNotBlank() && !defaultKey.startsWith("REPLACE_")) {
                // Developer has configured a default key
                _uiState.update {
                    it.copy(
                        selectedProvider = AIProvider.GROQ,
                        selectedModelId = AIProvider.GROQ.defaultModel,
                        apiKey = defaultKey
                    )
                }
                // Save it so it persists
                prefs.edit()
                    .putString("provider", AIProvider.GROQ.id)
                    .putString("model_${AIProvider.GROQ.id}", AIProvider.GROQ.defaultModel)
                    .putString("apikey_${AIProvider.GROQ.id}", defaultKey)
                    .apply()
            } else {
                // No default key configured — default to Groq but user needs to set key
                _uiState.update {
                    it.copy(
                        selectedProvider = AIProvider.GROQ,
                        selectedModelId = AIProvider.GROQ.defaultModel,
                        apiKey = ""
                    )
                }
            }
        }
    }

    /**
     * Downloads the handwriting recognition model.
     * If it fails permanently, enables text input fallback mode.
     */
    private fun downloadModel() {
        viewModelScope.launch {
            val result = recognizer.ensureModelReady()
            _uiState.update { state ->
                state.copy(
                    isModelReady = result.isSuccess,
                    recognitionUnavailable = !result.isSuccess,
                    errorMessage = if (result.isFailure) {
                        result.exceptionOrNull()?.message
                    } else null
                )
            }
        }
    }

    /**
     * Submits typed text directly to the AI (fallback when handwriting recognition
     * is not available on this device). Bypasses the entire recognition flow.
     */
    fun submitTypedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val state = _uiState.value
        val provider = state.selectedProvider
        val modelId = state.selectedModelId
        val apiKey = state.apiKey

        if (provider.requiresApiKey && apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    status = DiaryStatus.Error("Please set your ${provider.displayName} API key in Settings."),
                    errorMessage = "Please set your ${provider.displayName} API key in Settings.",
                    typedText = ""
                )
            }
            return
        }

        _uiState.update { it.copy(status = DiaryStatus.Thinking, typedText = "") }

        viewModelScope.launch {
            val aiResult = diaryAI.getResponse(
                provider = provider,
                modelId = modelId,
                apiKey = apiKey,
                userMessage = trimmed,
                conversationHistory = state.conversationHistory
            )

            if (aiResult.isFailure) {
                val error = aiResult.exceptionOrNull()
                val message = when (error) {
                    is AIException -> error.message ?: "The diary cannot respond right now."
                    else -> "An unexpected magical disturbance occurred."
                }
                _uiState.update {
                    it.copy(status = DiaryStatus.Error(message), errorMessage = message)
                }
                return@launch
            }

            val response = aiResult.getOrThrow()
            val newHistory = state.conversationHistory.toMutableList()
            newHistory.add("user" to trimmed)
            newHistory.add("assistant" to response)
            val trimmedHistory = if (newHistory.size > 20) newHistory.takeLast(20) else newHistory

            _uiState.update { it.copy(conversationHistory = trimmedHistory, aiResponse = response) }
            _uiState.update { it.copy(status = DiaryStatus.Responding) }
            animateTypewriter(response)
            _uiState.update {
                it.copy(status = DiaryStatus.Idle, canvasClearToken = it.canvasClearToken + 1)
            }
        }
    }

    /**
     * Updates the typed text field value.
     */
    fun onTypedTextChanged(text: String) {
        _uiState.update { it.copy(typedText = text) }
    }

    // ── Drawing Callbacks ────────────────────────────────────────

    fun onStrokeStarted() {
        typewriterJob?.cancel()
        _uiState.update { state ->
            state.copy(
                status = DiaryStatus.Writing,
                aiResponse = "",
                displayedResponse = "",
                errorMessage = null,
                inkAlpha = 1f,
                isDrawingEnabled = true
            )
        }
    }

    fun onStrokeEnded(stroke: StrokeData) {
        strokes.add(stroke)
        _uiState.update { state ->
            state.copy(strokeCount = strokes.size)
        }
    }

    // ── Main Flow: Write → Recognize → AI → Display ──────────────

    fun onWritingPaused() {
        val currentState = _uiState.value
        if (currentState.status != DiaryStatus.Writing || strokes.isEmpty()) return

        val capturedStrokes = strokes.toList()

        viewModelScope.launch {
            // Phase 1: Fade ink
            _uiState.update { it.copy(status = DiaryStatus.Fading) }
            fadeInk()

            // Phase 2: Recognize handwriting
            _uiState.update { it.copy(status = DiaryStatus.Recognizing) }
            val recognizedText = recognizeText(capturedStrokes)

            if (recognizedText.isFailure) {
                handleError(recognizedText.exceptionOrNull()?.message ?: "Could not read your handwriting.")
                return@launch
            }

            val text = recognizedText.getOrThrow()
            if (text.isBlank()) {
                handleError("Could not read your handwriting. Try writing more clearly.")
                return@launch
            }

            // Recognition succeeded — clear strokes
            strokes.clear()

            // Phase 3: Get AI response
            _uiState.update { it.copy(status = DiaryStatus.Thinking) }

            val state = _uiState.value
            val provider = state.selectedProvider
            val modelId = state.selectedModelId
            val apiKey = state.apiKey

            // Validate that we have an API key for providers that need one
            if (provider.requiresApiKey && apiKey.isBlank()) {
                handleError(
                    "Please set your ${provider.displayName} API key in Settings.\n\n" +
                    "Get a free key at: ${provider.websiteUrl}"
                )
                return@launch
            }

            val aiResult = diaryAI.getResponse(
                provider = provider,
                modelId = modelId,
                apiKey = apiKey,
                userMessage = text,
                conversationHistory = state.conversationHistory
            )

            if (aiResult.isFailure) {
                val error = aiResult.exceptionOrNull()
                val message = when (error) {
                    is AIException -> error.message ?: "The diary cannot respond right now."
                    else -> "An unexpected magical disturbance occurred."
                }
                handleError(message)
                return@launch
            }

            val response = aiResult.getOrThrow()

            // Update conversation history
            val newHistory = state.conversationHistory.toMutableList()
            newHistory.add("user" to text)
            newHistory.add("assistant" to response)
            val trimmedHistory = if (newHistory.size > 20) newHistory.takeLast(20) else newHistory

            _uiState.update { it.copy(conversationHistory = trimmedHistory, aiResponse = response) }

            // Phase 4: Typewriter animation
            _uiState.update { it.copy(status = DiaryStatus.Responding) }
            animateTypewriter(response)

            // Phase 5: Done
            _uiState.update {
                it.copy(
                    status = DiaryStatus.Idle,
                    isDrawingEnabled = true,
                    inkAlpha = 1f,
                    canvasClearToken = it.canvasClearToken + 1
                )
            }
        }
    }

    private fun handleError(message: String) {
        _uiState.update {
            it.copy(
                status = DiaryStatus.Error(message),
                errorMessage = message
            )
        }
        showInkAgain()
    }

    // ── Animations ───────────────────────────────────────────────

    private suspend fun fadeInk() {
        val steps = 20
        for (i in steps downTo 0) {
            _uiState.update { it.copy(inkAlpha = i.toFloat() / steps) }
            delay(30L)
        }
        _uiState.update { it.copy(inkAlpha = 0f) }
    }

    private fun showInkAgain() {
        viewModelScope.launch {
            val steps = 20
            for (i in 0..steps) {
                _uiState.update { it.copy(inkAlpha = i.toFloat() / steps) }
                delay(15L)
            }
            _uiState.update { it.copy(inkAlpha = 1f, isDrawingEnabled = true) }
        }
    }

    private suspend fun recognizeText(strokesToRecognize: List<StrokeData>): Result<String> {
        if (strokesToRecognize.isEmpty()) {
            return Result.failure(Exception("No strokes to recognize"))
        }
        val modelResult = recognizer.ensureModelReady()
        if (modelResult.isFailure) {
            return Result.failure(modelResult.exceptionOrNull() ?: Exception("Model download failed"))
        }
        return recognizer.recognize(strokesToRecognize)
    }

    private fun animateTypewriter(fullText: String) {
        typewriterJob?.cancel()
        typewriterJob = viewModelScope.launch {
            val delayPerChar = 55L
            for (i in 0..fullText.length) {
                _uiState.update { it.copy(displayedResponse = fullText.take(i)) }
                val delay = when {
                    i >= fullText.length -> 0L
                    fullText.getOrNull(i) in setOf('.', '!', '?') -> delayPerChar * 5
                    fullText.getOrNull(i) == ',' -> delayPerChar * 3
                    fullText.getOrNull(i) == ' ' -> delayPerChar * 2
                    else -> delayPerChar
                }
                delay(delay)
            }
        }
    }

    // ── User Actions ──────────────────────────────────────────────

    fun clearPage() {
        typewriterJob?.cancel()
        strokes.clear()
        _uiState.update { current ->
            DiaryUiState(
                apiKey = current.apiKey,
                selectedProvider = current.selectedProvider,
                selectedModelId = current.selectedModelId,
                conversationHistory = current.conversationHistory,
                isModelReady = current.isModelReady,
                canvasClearToken = current.canvasClearToken + 1
            )
        }
    }

    fun dismissError() {
        _uiState.update {
            it.copy(
                status = DiaryStatus.Idle,
                errorMessage = null,
                isDrawingEnabled = true,
                inkAlpha = 1f
            )
        }
    }

    // ── Settings ──────────────────────────────────────────────────

    /**
     * Updates the selected AI provider and loads its saved settings.
     */
    fun setProvider(provider: AIProvider) {
        val savedModel = prefs.getString("model_${provider.id}", provider.defaultModel) ?: provider.defaultModel
        val savedKey = prefs.getString("apikey_${provider.id}", "") ?: ""

        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedModelId = savedModel,
                apiKey = savedKey
            )
        }

        prefs.edit().putString("provider", provider.id).apply()
    }

    /**
     * Updates the selected model for the current provider.
     */
    fun setModel(modelId: String) {
        val provider = _uiState.value.selectedProvider
        _uiState.update { it.copy(selectedModelId = modelId) }
        prefs.edit().putString("model_${provider.id}", modelId).apply()
    }

    /**
     * Saves the API key for the current provider.
     */
    fun setApiKey(key: String) {
        val trimmed = key.trim()
        val provider = _uiState.value.selectedProvider
        _uiState.update { it.copy(apiKey = trimmed) }
        prefs.edit().putString("apikey_${provider.id}", trimmed).apply()
    }

    /**
     * Returns the models available for the current (or given) provider.
     */
    fun getModels(provider: AIProvider? = null): List<AIModel> {
        return getModelsForProvider(provider ?: _uiState.value.selectedProvider)
    }
}
