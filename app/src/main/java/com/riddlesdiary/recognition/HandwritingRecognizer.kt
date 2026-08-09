package com.riddlesdiary.recognition

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HandwritingRecognizer {

    companion object {
        private const val TAG = "HandwritingRecognizer"
    }

    private var model: DigitalInkRecognitionModel? = null
    private var recognizer: com.google.mlkit.vision.digitalink.DigitalInkRecognizer? = null
    private var isModelReady = false

    /**
     * Tries to find a supported handwriting model. Tries multiple language tags
     * in case the device doesn't support "en-US".
     */
    private fun findModel(): DigitalInkRecognitionModel? {
        // Try common English language tags
        val tags = listOf("en-US", "en", "en-GB", "en-X")
        for (tag in tags) {
            val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
            if (id != null) {
                Log.d(TAG, "Found model for language: $tag")
                return DigitalInkRecognitionModel.builder(id).build()
            }
        }
        Log.e(TAG, "No handwriting model found for any English language tag")
        return null
    }

    /**
     * Creates the recognizer for the given model.
     */
    private fun createRecognizer(model: DigitalInkRecognitionModel): com.google.mlkit.vision.digitalink.DigitalInkRecognizer {
        val options = DigitalInkRecognizerOptions.builder(model).build()
        return DigitalInkRecognition.getClient(options)
    }

    /**
     * Ensures the handwriting recognition model is downloaded and ready.
     * Returns a Result indicating success or a user-friendly error message.
     */
    suspend fun ensureModelReady(): Result<Unit> {
        if (isModelReady) return Result.success(Unit)

        return withContext(Dispatchers.IO) {
            try {
                // Find a compatible model
                val foundModel = findModel()
                if (foundModel == null) {
                    return@withContext Result.failure(
                        RecognitionException(
                            "Your device doesn't support handwriting recognition. " +
                            "Please make sure Google Play Services is installed and up to date."
                        )
                    )
                }

                model = foundModel
                recognizer = createRecognizer(foundModel)

                // Download the model if needed
                val remoteModelManager = RemoteModelManager.getInstance()
                val isDownloaded = Tasks.await(remoteModelManager.isModelDownloaded(foundModel))

                if (!isDownloaded) {
                    val conditions = DownloadConditions.Builder().build()
                    Tasks.await(remoteModelManager.download(foundModel, conditions))
                }

                isModelReady = true
                Result.success(Unit)
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception during model download, trying direct approach", e)
                // Try without explicit download — recognizer may auto-download
                try {
                    if (model != null && recognizer == null) {
                        recognizer = createRecognizer(model!!)
                    }
                    if (recognizer != null) {
                        val dummyInk = Ink.builder()
                            .addStroke(
                                Ink.Stroke.builder()
                                    .addPoint(Ink.Point.create(0f, 0f, 0L))
                                    .addPoint(Ink.Point.create(1f, 1f, 100L))
                                    .build()
                            )
                            .build()
                        recognizer!!.recognize(dummyInk)
                        isModelReady = true
                        Result.success(Unit)
                    } else {
                        Result.failure(RecognitionException(
                            "Cannot access handwriting on this device. " +
                            "Please update Google Play Services."
                        ))
                    }
                } catch (e2: Exception) {
                    Result.failure(RecognitionException(
                        "Handwriting is not available. Please ensure Google Play Services is installed and updated, " +
                        "then restart the app. (${e2.message})"
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model", e)
                Result.failure(RecognitionException(
                    "Could not load handwriting model. Please check your internet connection and try again."
                ))
            }
        }
    }

    /**
     * Recognizes text from a list of strokes.
     */
    suspend fun recognize(strokes: List<StrokeData>): Result<String> {
        val rec = recognizer
        if (rec == null) {
            return Result.failure(RecognitionException("Handwriting recognizer is not ready"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val ink = convertStrokesToInk(strokes)
                if (ink.strokes.isEmpty()) {
                    return@withContext Result.failure(RecognitionException("Write something first"))
                }

                val result = Tasks.await(rec.recognize(ink))
                val text = result.candidates.firstOrNull()?.text?.trim() ?: ""

                if (text.isEmpty()) {
                    Result.failure(RecognitionException("Could not read your handwriting. Try writing more clearly."))
                } else {
                    Result.success(text)
                }
            } catch (e: Exception) {
                Result.failure(RecognitionException("Recognition failed: ${e.message}"))
            }
        }
    }

    private fun convertStrokesToInk(strokes: List<StrokeData>): Ink {
        val inkBuilder = Ink.builder()
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val sb = Ink.Stroke.builder()
            for (point in stroke.points) {
                sb.addPoint(Ink.Point.create(point.x, point.y, point.timestampMs))
            }
            inkBuilder.addStroke(sb.build())
        }
        return inkBuilder.build()
    }
}

data class StrokeData(val points: List<TimedPoint>)
data class TimedPoint(val x: Float, val y: Float, val timestampMs: Long)

class RecognitionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
