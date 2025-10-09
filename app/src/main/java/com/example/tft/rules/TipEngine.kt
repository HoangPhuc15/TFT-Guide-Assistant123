package com.example.tft.rules

import android.content.Context
import com.example.tft.data.TftRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class TipEngine private constructor(
    private val rules: List<Rule>,
    val patch: String
) {
    fun evaluate(state: TipState): TipSession {
        val collected = mutableListOf<TipEntry>()
        for (rule in rules) {
            if (rule.matches(state)) {
                collected += rule.resolve(state)
            }
        }
        val unique = collected.distinctBy { Pair(it.text, it.category) }
        val ordered = unique.sortedWith(
            compareByDescending<TipEntry> { it.priority }
                .thenBy { it.type.ordinal }
                .thenBy { it.category.ordinal }
        )
        return TipSession(state, ordered)
    }

    private data class Rule(
        val id: String,
        val matcher: Matcher,
        val actions: List<RuleAction>
    ) {
        fun matches(state: TipState): Boolean = matcher.test(state)

        fun resolve(state: TipState): List<TipEntry> =
            actions.filter { it.shouldEmit(state) }
                .map { it.asEntry() }
    }

    private data class RuleAction(
        val text: String,
        val category: TipCategory,
        val priority: Int,
        val type: TipType,
        val requireDeviation: Set<String>,
        val requireTrait: Set<String>,
        val requireCarry: Set<String>,
        val requireItem: Set<String>,
        val requireAugment: Set<String>
    ) {
        fun shouldEmit(state: TipState): Boolean {
            val deviationSet = state.deviationFlags.map { it.uppercase(Locale.ROOT) }.toSet()
            if (requireDeviation.isNotEmpty() && deviationSet.intersect(requireDeviation).isEmpty()) {
                return false
            }
            val traitSet = state.traitFocus.map { it.uppercase(Locale.ROOT) }.toSet()
            if (requireTrait.isNotEmpty() && traitSet.intersect(requireTrait).isEmpty()) {
                return false
            }
            val carrySet = state.carryCandidates.map { it.uppercase(Locale.ROOT) }.toSet()
            if (requireCarry.isNotEmpty() && carrySet.intersect(requireCarry).isEmpty()) {
                return false
            }
            val itemSet = state.itemsInventory.map { it.uppercase(Locale.ROOT) }.toSet()
            if (requireItem.isNotEmpty() && itemSet.intersect(requireItem).isEmpty()) {
                return false
            }
            val augmentSet = state.augmentOptions.map { it.uppercase(Locale.ROOT) }.toSet()
            if (requireAugment.isNotEmpty() && augmentSet.intersect(requireAugment).isEmpty()) {
                return false
            }
            return true
        }

        fun asEntry(): TipEntry = TipEntry(text, category, priority, type)
    }

    private data class Matcher(
        val screens: Set<String>?,
        val stageEq: Stage?,
        val stageGte: Stage?,
        val stageLte: Stage?,
        val gold: Range?,
        val level: Range?,
        val interest: Range?,
        val health: Range?,
        val boardContains: Set<String>,
        val benchContains: Set<String>,
        val shopContains: Set<String>,
        val itemsContains: Set<String>,
        val augmentAny: Set<String>,
        val carryAny: Set<String>,
        val traitsAny: Set<String>,
        val deviationsAny: Set<String>
    ) {
        fun test(state: TipState): Boolean {
            if (!screens.isNullOrEmpty()) {
                if (!screens.contains(state.screen.uppercase(Locale.ROOT))) return false
            }
            val boardSet = state.boardUnits.map { it.uppercase(Locale.ROOT) }.toSet()
            val benchSet = state.benchUnits.map { it.uppercase(Locale.ROOT) }.toSet()
            val shopSet = state.shopUnits.map { it.uppercase(Locale.ROOT) }.toSet()
            val itemSet = state.itemsInventory.map { it.uppercase(Locale.ROOT) }.toSet()
            val augmentSet = state.augmentOptions.map { it.uppercase(Locale.ROOT) }.toSet()
            val carrySet = state.carryCandidates.map { it.uppercase(Locale.ROOT) }.toSet()
            val traitSet = state.traitFocus.map { it.uppercase(Locale.ROOT) }.toSet()
            val deviationSet = state.deviationFlags.map { it.uppercase(Locale.ROOT) }.toSet()
            if (stageEq != null && state.stage != stageEq) return false
            stageGte?.let { if (state.stage < it) return false }
            stageLte?.let { if (state.stage > it) return false }
            if (gold != null && !gold.isNullOrEmpty() && !gold.matches(state.gold)) return false
            if (level != null && !level.isNullOrEmpty() && !level.matches(state.level)) return false
            if (interest != null && !interest.isNullOrEmpty() && !interest.matches(state.interest)) return false
            if (health != null && !health.isNullOrEmpty() && !health.matches(state.health)) return false
            if (boardContains.isNotEmpty() && boardContains.intersect(boardSet).size != boardContains.size) return false
            if (benchContains.isNotEmpty() && benchContains.intersect(benchSet).size != benchContains.size) return false
            if (shopContains.isNotEmpty() && shopContains.intersect(shopSet).size != shopContains.size) return false
            if (itemsContains.isNotEmpty() && itemsContains.intersect(itemSet).size != itemsContains.size) return false
            if (augmentAny.isNotEmpty() && augmentAny.intersect(augmentSet).isEmpty()) return false
            if (carryAny.isNotEmpty() && carryAny.intersect(carrySet).isEmpty()) return false
            if (traitsAny.isNotEmpty() && traitsAny.intersect(traitSet).isEmpty()) return false
            if (deviationsAny.isNotEmpty() && deviationsAny.intersect(deviationSet).isEmpty()) return false
            return true
        }
    }

    private data class Range(
        val lt: Int?,
        val lte: Int?,
        val gt: Int?,
        val gte: Int?,
        val eq: Int?
    ) {
        fun matches(value: Int): Boolean {
            lt?.let { if (!(value < it)) return false }
            lte?.let { if (!(value <= it)) return false }
            gt?.let { if (!(value > it)) return false }
            gte?.let { if (!(value >= it)) return false }
            eq?.let { if (value != it) return false }
            return true
        }

        fun isNullOrEmpty(): Boolean = lt == null && lte == null && gt == null && gte == null && eq == null
    }

    companion object {
        fun from(context: Context): TipEngine {
            val repo = TftRepository.from(context)
            val rulesJson = repo.rules()
            val patch = rulesJson.optJSONObject("meta")?.optString("patch") ?: "unknown"
            val rules = parseRules(rulesJson.optJSONArray("rules") ?: JSONArray())
            return TipEngine(rules, patch)
        }

        private fun parseRules(array: JSONArray): List<Rule> {
            val out = mutableListOf<Rule>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val matcher = parseMatcher(obj.optJSONObject("match") ?: JSONObject())
                val tipsArray = obj.optJSONArray("tips") ?: JSONArray()
                val correctivesArray = obj.optJSONArray("correctives") ?: JSONArray()
                val actions = mutableListOf<RuleAction>()
                actions += parseActions(tipsArray, TipType.PROACTIVE)
                actions += parseActions(correctivesArray, TipType.CORRECTIVE)
                out += Rule(
                    id = obj.optString("id", "rule_$i"),
                    matcher = matcher,
                    actions = actions
                )
            }
            return out
        }

        private fun parseActions(array: JSONArray, defaultType: TipType): List<RuleAction> {
            val result = mutableListOf<RuleAction>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val text = obj.optString("text")
                if (text.isBlank()) continue
                val category = parseCategory(obj.optString("category"))
                val priority = obj.optInt("priority", 50)
                val type = obj.optString("type")
                    .takeIf { it.isNotBlank() }
                    ?.let { parseType(it) }
                    ?: defaultType
                result += RuleAction(
                    text = text,
                    category = category,
                    priority = priority,
                    type = type,
                    requireDeviation = obj.optJSONArray("requiresDeviation")?.toStringSet { it.uppercase(Locale.ROOT) }
                        ?: obj.optString("requiresDeviation").toSingleSet { it.uppercase(Locale.ROOT) },
                    requireTrait = obj.optJSONArray("requiresTrait")?.toStringSet { it.uppercase(Locale.ROOT) }
                        ?: obj.optString("requiresTrait").toSingleSet { it.uppercase(Locale.ROOT) },
                    requireCarry = obj.optJSONArray("requiresCarry")?.toStringSet { it.uppercase(Locale.ROOT) }
                        ?: obj.optString("requiresCarry").toSingleSet { it.uppercase(Locale.ROOT) },
                    requireItem = obj.optJSONArray("requiresItem")?.toStringSet { it.uppercase(Locale.ROOT) }
                        ?: obj.optString("requiresItem").toSingleSet { it.uppercase(Locale.ROOT) },
                    requireAugment = obj.optJSONArray("requiresAugment")?.toStringSet { it.uppercase(Locale.ROOT) }
                        ?: obj.optString("requiresAugment").toSingleSet { it.uppercase(Locale.ROOT) }
                )
            }
            return result
        }

        private fun parseMatcher(obj: JSONObject): Matcher {
            return Matcher(
                screens = obj.optStringOrArray("screen") { value -> value.uppercase(Locale.ROOT) },
                stageEq = Stage.parse(obj.optString("stage")) ?: Stage.parse(obj.optJSONObject("stage")?.optString("eq")),
                stageGte = Stage.parse(obj.optJSONObject("stage")?.optString("gte")),
                stageLte = Stage.parse(obj.optJSONObject("stage")?.optString("lte")),
                gold = obj.optJSONObject("gold").toRange(),
                level = obj.optJSONObject("level").toRange(),
                interest = obj.optJSONObject("interest").toRange(),
                health = obj.optJSONObject("health").toRange(),
                boardContains = obj.optJSONArray("boardContains").toStringSet { it.uppercase(Locale.ROOT) },
                benchContains = obj.optJSONArray("benchContains").toStringSet { it.uppercase(Locale.ROOT) },
                shopContains = obj.optJSONArray("shopContains").toStringSet { it.uppercase(Locale.ROOT) },
                itemsContains = obj.optJSONArray("itemsContains").toStringSet { it.uppercase(Locale.ROOT) },
                augmentAny = obj.optJSONArray("augmentAny").toStringSet { it.uppercase(Locale.ROOT) },
                carryAny = obj.optJSONArray("carryAny").toStringSet { it.uppercase(Locale.ROOT) },
                traitsAny = obj.optJSONArray("traitsAny").toStringSet { it.uppercase(Locale.ROOT) },
                deviationsAny = obj.optJSONArray("deviationsAny").toStringSet { it.uppercase(Locale.ROOT) }
            )
        }

        private fun parseCategory(raw: String): TipCategory {
            val normalized = raw.ifBlank { TipCategory.NOTE.name }
            return runCatching { TipCategory.valueOf(normalized.uppercase(Locale.ROOT)) }
                .getOrDefault(TipCategory.NOTE)
        }

        private fun parseType(raw: String): TipType {
            return runCatching { TipType.valueOf(raw.uppercase(Locale.ROOT)) }
                .getOrDefault(TipType.PROACTIVE)
        }

        private fun JSONObject?.toRange(): Range? {
            if (this == null) return null
            if (length() == 0) return null
            return Range(
                lt = optIntOrNull("lt"),
                lte = optIntOrNull("lte"),
                gt = optIntOrNull("gt"),
                gte = optIntOrNull("gte"),
                eq = optIntOrNull("eq")
            )
        }

        private fun JSONObject.optIntOrNull(key: String): Int? =
            if (has(key)) optInt(key) else null

        private fun JSONObject.optStringOrArray(
            key: String,
            transform: (String) -> String = { it }
        ): Set<String>? {
            if (!has(key)) return null
            val rawValue = opt(key)
            return when (rawValue) {
                is JSONArray -> rawValue.toStringSet(transform)
                is String -> rawValue.toSingleSet(transform)
                else -> null
            }
        }

        private fun JSONArray?.toStringSet(transform: (String) -> String = { it }): Set<String> {
            if (this == null) return emptySet()
            val set = LinkedHashSet<String>()
            for (i in 0 until length()) {
                val value = optString(i)
                if (value.isNotBlank()) {
                    set += transform(value.trim())
                }
            }
            return set
        }

        private fun String?.toSingleSet(transform: (String) -> String = { it }): Set<String> {
            if (this.isNullOrBlank()) return emptySet()
            return setOf(transform(this.trim()))
        }
    }
}
