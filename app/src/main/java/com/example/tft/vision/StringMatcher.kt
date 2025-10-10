package com.example.tft.vision

import java.util.Locale
import kotlin.math.min

/**
 * Lightweight fuzzy matcher that prefers the closest match by Levenshtein
 * distance.  Returns canonical IDs supplied at construction time.
 */
class StringMatcher(values: Collection<String>) {
    private val canonical: Map<String, String>

    init {
        val map = mutableMapOf<String, String>()
        for (value in values) {
            map[value.uppercase(Locale.ROOT)] = value
        }
        canonical = map
    }

    fun bestMatch(raw: String, minScore: Int = 72): String? {
        val cleaned = raw.trim().uppercase(Locale.ROOT)
        if (cleaned.isEmpty()) return null
        var bestId: String? = null
        var bestScore = -1
        for ((key, id) in canonical) {
            val score = similarity(cleaned, key)
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }
        return if (bestScore >= minScore) bestId else null
    }

    fun bestMatches(tokens: Collection<String>, minScore: Int = 72): List<String> {
        val result = mutableListOf<String>()
        for (token in tokens) {
            val match = bestMatch(token, minScore)
            if (match != null) {
                result += match
            }
        }
        return result
    }

    private fun similarity(lhs: String, rhs: String): Int {
        if (lhs == rhs) return 100
        val maxLength = maxOf(lhs.length, rhs.length)
        if (maxLength == 0) return 0
        val distance = levenshtein(lhs, rhs)
        val score = ((maxLength - distance).toDouble() / maxLength.toDouble()) * 100
        return score.toInt()
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(
                    min(dp[j] + 1, dp[j - 1] + 1),
                    prev + cost
                )
                prev = temp
            }
        }
        return dp[n]
    }
}
