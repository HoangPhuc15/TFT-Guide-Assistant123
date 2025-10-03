package com.tftguide.assistant.screen

import android.media.Image
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Converts screen captures into a lightweight tactical summary.
 * For now this only runs OCR over the bench area to demonstrate the pipeline.
 */
class BoardAnalyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun analyze(image: Image): BoardState {
        return try {
            val inputImage = InputImage.fromMediaImage(image, 0)
            val result = Tasks.await(recognizer.process(inputImage))
            val text = result?.text ?: ""
            BoardState(rawText = text.trim())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to analyze frame", t)
            BoardState(rawText = "")
        }
    }

    data class BoardState(val rawText: String)

    companion object {
        private const val TAG = "BoardAnalyzer"
    }
}
