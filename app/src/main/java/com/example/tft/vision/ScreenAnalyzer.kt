package com.example.tft.vision

import android.graphics.Bitmap
import com.example.tft.data.TftRepository
import com.example.tft.rules.Stage
import com.example.tft.rules.TipState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject
import java.util.Locale
import kotlin.math.min

class ScreenAnalyzer(private val repository: TftRepository) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    private val championIndex = loadChampions()
    private val itemIndex = loadItems()
    private val augmentTerms = loadAugments()
    private val championMatcher = StringMatcher(championIndex.keys)
    private val itemMatcher = StringMatcher(itemIndex.keys)
    private val augmentMatcher = StringMatcher(augmentTerms)
    private var lastState: TipState? = null

    suspend fun analyze(bitmap: Bitmap): TipState = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val textResult = runCatching { recognize(image) }.getOrNull()
        val rawText = textResult?.text ?: ""
        val lines = rawText.lines().filter { it.isNotBlank() }
        val tokens = tokenize(lines)

        val previous = lastState
        val stage = parseStage(tokens) ?: previous?.stage ?: Stage(2, 1)
        val screen = detectScreen(tokens, stage, previous)
        val level = parseLevel(tokens) ?: previous?.level ?: defaultLevelFor(stage)
        val gold = parseGold(tokens) ?: previous?.gold ?: 20
        val interest = min(gold / 10, 5)
        val health = parseHealth(tokens) ?: previous?.health ?: -1
        val shopUnitsRaw = detectShop(tokens)
        val allChampions = championMatcher.bestMatches(tokens)
        val benchUnitsRaw = detectBench(tokens, allChampions, shopUnitsRaw, level)
        val boardUnitsRaw = detectBoard(allChampions, benchUnitsRaw, shopUnitsRaw, level)
        val itemsRaw = detectItems(tokens)
        val augmentsRaw = detectAugments(tokens)
        val boardUnits = if (boardUnitsRaw.isNotEmpty()) boardUnitsRaw else previous?.boardUnits ?: emptyList()
        val benchUnits = if (benchUnitsRaw.isNotEmpty()) benchUnitsRaw else previous?.benchUnits ?: emptyList()
        val shopUnits = if (shopUnitsRaw.isNotEmpty()) shopUnitsRaw else previous?.shopUnits ?: emptyList()
        val items = if (itemsRaw.isNotEmpty()) itemsRaw else previous?.itemsInventory ?: emptyList()
        val augments = if (augmentsRaw.isNotEmpty()) augmentsRaw else previous?.augmentOptions ?: emptyList()
        val carryCandidates = inferCarry(boardUnits, benchUnits)
        val traits = inferTraits(boardUnits.ifEmpty { previous?.boardUnits ?: emptyList() })
        val deviations = inferDeviations(stage, level, gold, interest, items, carryCandidates, boardUnits, benchUnits)
        val metadata = JSONObject().apply {
            put("source", if (textResult != null) "ocr" else "heuristic")
            put("timestamp", System.currentTimeMillis())
            put("rawText", rawText)
            put("screen", screen)
        }

        val state = TipState(
            screen = screen,
            stage = stage,
            gold = gold,
            interest = interest,
            level = level,
            health = health,
            boardUnits = boardUnits,
            benchUnits = benchUnits,
            shopUnits = shopUnits,
            augmentOptions = augments,
            itemsInventory = items,
            carryCandidates = carryCandidates,
            traitFocus = traits,
            deviationFlags = deviations,
            metadata = metadata
        )
        lastState = state
        state
    }

    private suspend fun recognize(image: InputImage): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCanceledListener { cont.cancel() }
    }

    private fun tokenize(lines: List<String>): List<String> {
        val tokens = mutableListOf<String>()
        for (line in lines) {
            line.split(' ', '\n', '\t', '/', '-', ':', '(', ')', ',', '.').forEach { raw ->
                val token = raw.trim()
                if (token.isNotEmpty()) {
                    tokens += token
                }
            }
        }
        return tokens
    }

    private fun parseStage(tokens: List<String>): Stage? {
        val regex = Regex("(\\d)-(\\d)")
        val match = tokens.firstOrNull { regex.containsMatchIn(it) } ?: return null
        val result = regex.find(match) ?: return null
        return Stage.parse(result.value)
    }

    private fun parseLevel(tokens: List<String>): Int? {
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.equals("LV", ignoreCase = true) || token.equals("LEVEL", ignoreCase = true)) {
                val next = tokens.getOrNull(index + 1)?.filter { it.isDigit() }
                val parsed = next?.toIntOrNull()
                if (parsed != null) return parsed
            }
        }
        return tokens.firstOrNull { it.all(Char::isDigit) && it.length == 1 }?.toIntOrNull()
    }

    private fun parseGold(tokens: List<String>): Int? {
        val goldKeywords = setOf("GOLD", "VANG", "VÀNG", "G")
        for (index in tokens.indices) {
            val token = tokens[index].uppercase(Locale.ROOT)
            if (goldKeywords.contains(token)) {
                val candidate = tokens.getOrNull(index + 1)?.filter { it.isDigit() }
                val parsed = candidate?.toIntOrNull()
                if (parsed != null) return parsed
            }
        }
        val numbers = tokens.mapNotNull { it.filter { ch -> ch.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull() }
        return numbers.maxOrNull()?.takeIf { it in 0..80 }
    }

    private fun parseHealth(tokens: List<String>): Int? {
        val hpKeywords = setOf("HP", "MAU", "MÁU", "HEALTH")
        for (index in tokens.indices) {
            val token = tokens[index].uppercase(Locale.ROOT)
            if (hpKeywords.contains(token)) {
                val candidate = tokens.getOrNull(index + 1)?.filter { it.isDigit() }
                val parsed = candidate?.toIntOrNull()
                if (parsed != null) return parsed
            }
        }
        return null
    }

    private fun detectScreen(tokens: List<String>, stage: Stage, previous: TipState?): String {
        val upper = tokens.map { it.uppercase(Locale.ROOT) }
        return when {
            upper.any { it.contains("CAROUSEL") || it.contains("ĐI CHỢ") } -> "CAROUSEL"
            upper.any { it.contains("AUGMENT") || it.contains("LÕI") } -> "AUGMENT"
            previous?.screen == "COMBAT" && upper.contains("VICTORY") -> "PLANNING"
            previous?.screen == "PLANNING" && upper.contains("VS") -> "COMBAT"
            stage.minor == 7 || stage.minor == 6 -> "COMBAT"
            else -> previous?.screen ?: "PLANNING"
        }
    }

    private fun detectShop(tokens: List<String>): List<String> {
        val keywords = setOf("SHOP", "CỬA", "MUA")
        val matches = mutableListOf<String>()
        var inShopSection = false
        for (token in tokens) {
            val upper = token.uppercase(Locale.ROOT)
            if (keywords.any { upper.contains(it) }) {
                inShopSection = true
                continue
            }
            if (inShopSection) {
                val champion = championMatcher.bestMatch(token)
                if (champion != null && matches.size < 5) {
                    matches += champion
                }
                if (matches.size >= 5) break
            }
        }
        if (matches.isEmpty()) {
            matches += championMatcher.bestMatches(tokens).take(5)
        }
        return matches.distinct().take(5)
    }

    private fun detectBench(
        tokens: List<String>,
        allChampions: List<String>,
        shopUnits: List<String>,
        level: Int
    ): List<String> {
        val keywords = setOf("BENCH", "GHẾ", "DỰ", "TRỮ")
        val benchMatches = mutableListOf<String>()
        var inBench = false
        for (token in tokens) {
            val upper = token.uppercase(Locale.ROOT)
            if (keywords.any { upper.contains(it) }) {
                inBench = true
                continue
            }
            if (inBench) {
                val champion = championMatcher.bestMatch(token)
                if (champion != null) {
                    benchMatches += champion
                }
            }
        }
        if (benchMatches.isEmpty()) {
            val candidatePool = allChampions.filterNot { shopUnits.contains(it) }
            benchMatches += candidatePool.drop(level).take(10)
        }
        return benchMatches.distinct()
    }

    private fun detectBoard(
        allChampions: List<String>,
        benchUnits: List<String>,
        shopUnits: List<String>,
        level: Int
    ): List<String> {
        val board = mutableListOf<String>()
        for (champion in allChampions) {
            if (shopUnits.contains(champion)) continue
            if (benchUnits.contains(champion) && board.size >= level) continue
            if (board.size < level) {
                board += champion
            }
        }
        if (board.isEmpty()) {
            board += allChampions.take(level)
        }
        return board.distinct()
    }

    private fun detectItems(tokens: List<String>): List<String> {
        val matches = itemMatcher.bestMatches(tokens, minScore = 68)
        return matches.take(6).distinct()
    }

    private fun detectAugments(tokens: List<String>): List<String> {
        val matches = augmentMatcher.bestMatches(tokens, minScore = 70)
        return matches.take(3).distinct()
    }

    private fun inferCarry(boardUnits: List<String>, benchUnits: List<String>): List<String> {
        if (boardUnits.isEmpty() && benchUnits.isEmpty()) return emptyList()
        val carryPool = (boardUnits + benchUnits).distinct()
        val ranked = carryPool.sortedWith(compareByDescending<String> { championIndex[it]?.cost ?: 0 }
            .thenByDescending { championIndex[it]?.roles?.count { role -> role == "carry" } ?: 0 }
        )
        return ranked.take(2)
    }

    private fun inferTraits(boardUnits: List<String>): List<String> {
        val counts = mutableMapOf<String, Int>()
        for (champion in boardUnits) {
            val traits = championIndex[champion]?.traits ?: continue
            for (trait in traits) {
                counts[trait.uppercase(Locale.ROOT)] = counts.getOrDefault(trait.uppercase(Locale.ROOT), 0) + 1
            }
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(3)
    }

    private fun inferDeviations(
        stage: Stage,
        level: Int,
        gold: Int,
        interest: Int,
        items: List<String>,
        carryCandidates: List<String>,
        boardUnits: List<String>,
        benchUnits: List<String>
    ): Set<String> {
        val flags = mutableSetOf<String>()
        val expectedLevel = defaultLevelFor(stage)
        if (level < expectedLevel - 1) {
            flags += "LEVEL_BEHIND"
        }
        if (gold < 20 && stage.major >= 2 && stage.minor >= 5) {
            flags += "ECON_BEHIND"
        }
        if (interest < min(stage.major + 1, 5)) {
            flags += "LOW_INTEREST"
        }
        if (items.size >= 3) {
            flags += "ITEMS_UNUSED"
        }
        if (benchUnits.size >= 8) {
            flags += "BENCH_FULL"
        }
        if (carryCandidates.isNotEmpty() && items.isEmpty()) {
            flags += "CARRY_UNEQUIPPED"
        }
        if (boardUnits.isNotEmpty()) {
            val frontline = boardUnits.count { championIndex[it]?.roles?.contains("frontline") == true }
            val backline = boardUnits.count { championIndex[it]?.roles?.contains("carry") == true }
            if (frontline == 0 && backline > 0) {
                flags += "NO_FRONTLINE"
            }
        }
        return flags
    }

    private fun defaultLevelFor(stage: Stage): Int {
        return when (stage.major) {
            1 -> 3
            2 -> if (stage.minor >= 5) 6 else 5
            3 -> if (stage.minor >= 5) 7 else 6
            4 -> if (stage.minor >= 5) 8 else 7
            5 -> 8
            else -> 9
        }
    }

    private fun loadChampions(): Map<String, ChampionInfo> {
        val result = mutableMapOf<String, ChampionInfo>()
        val array = repository.champions()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            val name = obj.optString("name", id)
            val traits = obj.optJSONArray("traits")?.let { jsonArray ->
                val list = mutableListOf<String>()
                for (j in 0 until jsonArray.length()) {
                    val trait = jsonArray.optString(j)
                    if (trait.isNotBlank()) list += trait
                }
                list
            } ?: emptyList()
            val recommendedItems = obj.optJSONArray("recommendedItems")?.let { jsonArray ->
                val list = mutableListOf<String>()
                for (j in 0 until jsonArray.length()) {
                    val item = jsonArray.optString(j)
                    if (item.isNotBlank()) list += item
                }
                list
            } ?: emptyList()
            val roles = obj.optJSONArray("roles")?.let { jsonArray ->
                val list = mutableListOf<String>()
                for (j in 0 until jsonArray.length()) {
                    val role = jsonArray.optString(j)
                    if (role.isNotBlank()) list += role.lowercase(Locale.ROOT)
                }
                list
            } ?: emptyList()
            val cost = obj.optInt("cost", 1)
            result[id] = ChampionInfo(
                id = id,
                name = name,
                traits = traits,
                recommendedItems = recommendedItems,
                roles = roles,
                cost = cost
            )
            if (!name.equals(id, true)) {
                result[name] = result[id]!!
            }
        }
        return result
    }

    private fun loadItems(): Map<String, ItemInfo> {
        val result = mutableMapOf<String, ItemInfo>()
        val array = repository.items()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            val name = obj.optString("name", id)
            result[id] = ItemInfo(id, name)
            if (!name.equals(id, true)) {
                result[name] = result[id]!!
            }
        }
        return result
    }

    private fun loadAugments(): Set<String> {
        val result = mutableSetOf<String>()
        val array = repository.augments()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            val name = obj.optString("name", id)
            result += id
            if (!name.equals(id, true)) {
                result += name
            }
        }
        return result
    }

    private data class ChampionInfo(
        val id: String,
        val name: String,
        val traits: List<String>,
        val recommendedItems: List<String>,
        val roles: List<String>,
        val cost: Int
    )

    private data class ItemInfo(
        val id: String,
        val name: String
    )
}
