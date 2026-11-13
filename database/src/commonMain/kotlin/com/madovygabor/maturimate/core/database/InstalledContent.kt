package com.madovygabor.maturimate.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One file the installed `content.db` was built from: the bundled file (`bundled`) or a downloaded pack (`pack-<subject>`). */
@Serializable
data class BuiltPart(
    val name: String,
    @SerialName("content_version") val contentVersion: Long,
    val sha256: String,
    val kind: String,
    /** Empty for a full file written before packs existed: it holds every subject it contains. */
    @SerialName("subject_ids") val subjectIds: List<Long> = emptyList(),
)

/**
 * `content-installed.json`: which parts the installed `content.db` was built from. Written last by the swap, so a half
 * finished swap is built again at the next start. A downloaded pack on disk that is not listed here is waiting for a swap.
 */
@Serializable
data class InstalledContent(val parts: List<BuiltPart>) {
    fun toJson(): String = json.encodeToString(serializer(), this) + "\n"

    /** The kind and version of what the installed file holds for one subject; null when it holds nothing of it. */
    fun subject(subjectId: Long): SubjectContent? {
        val candidates = parts.mapNotNull { part ->
            val holds = part.subjectIds.isEmpty() || subjectId in part.subjectIds
            if (holds) SubjectContent(part.kind, part.contentVersion) else null
        }
        // a full pack beats a starter whatever the versions; among equals the higher version
        return candidates.maxWithOrNull(compareBy<SubjectContent> { it.kind == ContentManifest.KIND_FULL }.thenBy { it.contentVersion })
    }

    companion object {
        const val FILE_NAME = "content-installed.json"
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): InstalledContent = json.decodeFromString(serializer(), text)
    }
}

data class SubjectContent(val kind: String, val contentVersion: Long)
