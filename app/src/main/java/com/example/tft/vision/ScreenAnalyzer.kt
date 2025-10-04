package com.example.tft.vision

import android.graphics.Bitmap
import android.util.Log
import com.example.tft.data.TftRepository
import com.example.tft.rules.TipState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ScreenAnalyzer(private val repository: TftRepository) {
    suspend fun analyze(bitmap: Bitmap): TipState = withContext(Dispatchers.Default) {
        // Placeholder heuristics – replace with OCR or CV pipeline.
        val dominantColor = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val screen = when {
            dominantColor and 0xFF0000 > 0x7F0000 -> "COMBAT"
            dominantColor and 0x0000FF > 0x00007F -> "CAROUSEL"
            else -> "PLANNING"
        }

        Log.d(TAG, "Heuristic screen detection -> $screen")

        TipState(
            screen = screen,
            gold = 18,
            benchUnits = 6,
            carry = "Ashe",
            needItem = "RecurveBow",
            haveTrait = "Sniper",
            enemyThreat = "Assassin",
            metadata = JSONObject().apply {
                put("source", "heuristic")
                put("timestamp", System.currentTimeMillis())
            }
        )
    }

    companion object {
        private const val TAG = "ScreenAnalyzer"
    }
}
