package com.riddlesdiary.recognition

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionCandidate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps ML Kit's Digital Ink Recognition for handwriting recognition from stroke data.
 * Takes stroke data (collected from the drawing canvas), converts to ML Kit Ink format,
 * and returns recognized text.
 */
class HandwritingRecognizer(private val context: Context) {

    private var isModelReady = false
    private val modelIdentifier: DigitalInkRecognitionModelIdentifier =
        DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            ?: throw IllegalStateException("English handwriting model not available")

    private val model: DigitalInkRecognitionModel =
        DigitalInkRecognitionModel.builder(modelIdentifier).build()

    private val recognizer by lazy {
        DigitalInkRecognition.getClient(
            RemoteModelManager.getInstance(),
            model
        )
    }

    /**
     * Ensures the handwriting recognition model is downloaded and ready.
     * Downloads the model if not already present (~20MB).
     */
    suspend fun ensureModelReady(): Result<Unit> {
        if (isModelReady) return Result.success(Unit)

        return try {
            val isDownloaded = RemoteModelManager.getInstance()
                .isModelDownloaded(model)
                .await()

            if (!isDownloaded) {
                val conditions = DownloadConditions.Builder()
                    .requireWifi(false)
                    .build()
                RemoteModelManager.getInstance()
                    .download(model, conditions)
                    .await()
            }

            isModelReady = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(RecognitionException("Failed to download handwriting model", e))
        }
    }

    /**
     * Recognizes text from a list of strokes.
     * Each stroke contains a list of timed points (x, y, timestamp).
     */
    suspend fun recognize(strokes: List<StrokeData>): Result<String> {
        if (!isModelReady) {
            return Result.failure(RecognitionException("Model not ready. Call ensureModelReady first."))
        }

        return try {
            val ink = convertStrokesToInk(strokes)
            val result = recognizer.recognize(ink).await()
            val text = result.candidates
                .firstOrNull()
                ?.text
                ?.trim()
                ?: ""

            if (text.isEmpty()) {
                Result.failure(RecognitionException("Could not read your handwriting. Please try again."))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(RecognitionException("Recognition failed", e))
        }
    }

    /**
     * Converts our stroke data format to ML Kit's Ink format.
     */
    private fun convertStrokesToInk(strokes: List<StrokeData>): Ink {
        val inkBuilder = Ink.builder()

        for (stroke in strokes) {
            val strokeBuilder = Ink.Stroke.builder()
            for (point in stroke.points) {
                strokeBuilder.addPoint(
                    Ink.Point.create(
                        point.x,
                        point.y,
                        point.timestampMs
                    )
                )
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        return inkBuilder.build()
    }

    /**
     * Returns whether the model is downloaded and ready.
     */
    fun isReady(): Boolean = isModelReady
}

/**
 * Data class representing a single stroke (pen down to pen up).
 */
data class StrokeData(
    val points: List<TimedPoint>
)

/**
 * A single point in a stroke, with position and timestamp.
 */
data class TimedPoint(
    val x: Float,
    val y: Float,
    val timestampMs: Long
)

/**
 * Exception for recognition errors.
 */
class RecognitionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
