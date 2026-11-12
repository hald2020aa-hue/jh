package eu.kanade.tachiyomi.data.database.models

import java.util.Locale

/** Recognize common Manhwa labels, including the frequent source typo "Manwha". */
internal fun isManhwaTagText(tag: String): Boolean {
    val normalized = tag.trim().lowercase(Locale.ROOT)
    return normalized in setOf("long strip", "manhwa", "manwha", "манхва", "kr") ||
        normalized.startsWith("korean")
}

/** Chinese-language source labels are normalized before checking their script tags. */
internal fun isManhuaTagText(tag: String): Boolean {
    val normalized = tag.trim().lowercase(Locale.ROOT)
    return normalized in setOf("manhua", "маньхуа", "cn", "hk", "zh-hans", "zh-hant") ||
        normalized.startsWith("chinese")
}
