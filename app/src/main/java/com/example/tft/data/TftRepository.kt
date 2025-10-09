package com.example.tft.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TftRepository private constructor(private val context: Context) {
    private val cache = mutableMapOf<String, JSONArray>()
    private val liveDir = File(context.filesDir, "data/sets/live")

    fun champions(): JSONArray = loadJsonArray(CHAMPIONS)
    fun items(): JSONArray = loadJsonArray(ITEMS)
    fun traits(): JSONArray = loadJsonArray(TRAITS)
    fun augments(): JSONArray = loadJsonArray(AUGMENTS)
    fun rules(): JSONObject = loadJsonObject(RULES)

    private fun loadJsonArray(fileName: String): JSONArray {
        return cache.getOrPut(fileName) { JSONArray(readText(fileName)) }
    }

    private fun loadJsonObject(fileName: String): JSONObject {
        return JSONObject(readText(fileName))
    }

    private fun readText(fileName: String): String {
        val liveFile = liveDir.resolve(fileName)
        if (liveFile.exists()) {
            return liveFile.readText()
        }
        context.assets.open("$ASSET_ROOT/$fileName").use { stream ->
            return stream.readBytes().decodeToString()
        }
    }

    companion object {
        private const val ASSET_ROOT = "data/sets/15.19"
        private const val CHAMPIONS = "tft-champions.json"
        private const val ITEMS = "tft-items.json"
        private const val TRAITS = "tft-traits.json"
        private const val AUGMENTS = "tft-augments.json"
        private const val RULES = "tips-rules.json"

        fun from(context: Context): TftRepository = TftRepository(context.applicationContext)
    }
}
