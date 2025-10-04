package com.example.tft.rules

import android.content.Context
import com.example.tft.data.TftRepository
import org.json.JSONArray
import org.json.JSONObject

class TipEngine private constructor(
    private val rulesJson: JSONObject
) {
    val patch: String = rulesJson.optJSONObject("meta")?.optString("patch") ?: "unknown"

    fun evaluate(state: TipState): TipSession {
        val matches = mutableListOf<String>()
        val array = rulesJson.optJSONArray("states") ?: JSONArray()
        for (i in 0 until array.length()) {
            val rule = array.getJSONObject(i)
            val match = rule.optJSONObject("match") ?: continue
            if (matches(state, match)) {
                val tips = rule.optJSONArray("tips") ?: continue
                for (j in 0 until tips.length()) {
                    matches += tips.optString(j)
                }
            }
        }
        return TipSession(state, matches)
    }

    private fun matches(state: TipState, requirement: JSONObject): Boolean {
        requirement.optString("screen")?.let {
            if (!state.screen.equals(it, ignoreCase = true)) return false
        }
        requirement.optJSONObject("gold")?.let { gold ->
            val lt = gold.optInt("lt", Int.MIN_VALUE)
            val gte = gold.optInt("gte", Int.MIN_VALUE)
            if (lt != Int.MIN_VALUE && state.gold >= lt) return false
            if (gte != Int.MIN_VALUE && state.gold < gte) return false
        }
        requirement.optJSONObject("benchUnits")?.let { bench ->
            val gte = bench.optInt("gte", Int.MIN_VALUE)
            if (gte != Int.MIN_VALUE && state.benchUnits < gte) return false
        }
        requirement.optString("needItem")?.let {
            if (!state.needItem.equals(it, ignoreCase = true)) return false
        }
        requirement.optString("carry")?.let {
            if (!state.carry.equals(it, ignoreCase = true)) return false
        }
        requirement.optString("haveTrait")?.let {
            if (!state.haveTrait.equals(it, ignoreCase = true)) return false
        }
        requirement.optString("enemyThreat")?.let {
            if (!state.enemyThreat.equals(it, ignoreCase = true)) return false
        }
        return true
    }

    companion object {
        fun from(context: Context): TipEngine {
            val repo = TftRepository.from(context)
            return TipEngine(repo.rules())
        }
    }
}
