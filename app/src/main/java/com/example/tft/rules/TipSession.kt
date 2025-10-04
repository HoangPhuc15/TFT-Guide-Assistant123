package com.example.tft.rules

import org.json.JSONObject

data class TipState(
    val screen: String,
    val gold: Int,
    val benchUnits: Int,
    val carry: String?,
    val needItem: String?,
    val haveTrait: String?,
    val enemyThreat: String?,
    val metadata: JSONObject
)

data class TipSession(
    val state: TipState,
    val tips: List<String>
)
