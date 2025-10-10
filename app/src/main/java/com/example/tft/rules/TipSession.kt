package com.example.tft.rules

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import org.json.JSONObject

@Parcelize
data class Stage(
    val major: Int,
    val minor: Int
) : Comparable<Stage>, Parcelable {
    override fun compareTo(other: Stage): Int {
        val majorDiff = major - other.major
        return if (majorDiff != 0) majorDiff else minor - other.minor
    }

    override fun toString(): String = "$major-$minor"

    companion object {
        fun parse(raw: String?): Stage? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.trim().split('-', limit = 2)
            if (parts.size != 2) return null
            return parts[0].toIntOrNull()?.let { majorPart ->
                parts[1].toIntOrNull()?.let { minorPart ->
                    Stage(majorPart, minorPart)
                }
            }
        }
    }
}

@Parcelize
data class TipState(
    val screen: String,
    val stage: Stage,
    val gold: Int,
    val interest: Int,
    val level: Int,
    val health: Int,
    val boardUnits: List<String>,
    val benchUnits: List<String>,
    val shopUnits: List<String>,
    val augmentOptions: List<String>,
    val itemsInventory: List<String>,
    val carryCandidates: List<String>,
    val traitFocus: List<String>,
    val deviationFlags: Set<String>,
    val metadata: @RawValue JSONObject
) : Parcelable {
    fun summary(): String {
        val stageLabel = "Stage ${stage}".trim()
        val levelLabel = "Lv $level"
        val goldLabel = "$gold vàng (${interest} lãi)"
        val healthLabel = if (health >= 0) "$health máu" else null
        return listOf(stageLabel, levelLabel, goldLabel, healthLabel)
            .filterNotNull()
            .joinToString(" • ")
    }
}

@Parcelize
data class TipEntry(
    val text: String,
    val category: TipCategory,
    val priority: Int,
    val type: TipType
) : Parcelable {
    val categoryLabel: String get() = category.label
    val typeLabel: String get() = type.label
    val isCorrective: Boolean get() = type == TipType.CORRECTIVE
}

enum class TipCategory(val label: String) {
    LEVELING("Lên cấp"),
    ECONOMY("Kinh tế"),
    SHOP("Cửa hàng"),
    BOARD("Sắp xếp đội hình"),
    ITEMS("Lên đồ"),
    AUGMENT("Chọn lõi"),
    SCOUTING("Do thám"),
    ALERT("Cảnh báo"),
    NOTE("Ghi chú")
}

enum class TipType(val label: String) {
    PROACTIVE("Chủ động"),
    CORRECTIVE("Khắc phục")
}

data class TipSession(
    val state: TipState,
    val entries: List<TipEntry>
) {
    val proactive: List<TipEntry> = entries.filter { it.type == TipType.PROACTIVE }
    val corrective: List<TipEntry> = entries.filter { it.type == TipType.CORRECTIVE }

    fun displayLines(limit: Int = 4): List<String> {
        return entries.sortedWith(priorityComparator())
            .take(limit)
            .map { entry ->
                val prefix = if (entry.isCorrective) "⚠️" else "•"
                "$prefix [${entry.categoryLabel}] ${entry.text}"
            }
    }

    private fun priorityComparator(): Comparator<TipEntry> =
        compareByDescending<TipEntry> { it.priority }
            .thenBy { it.type.ordinal }
            .thenBy { it.category.ordinal }
}
