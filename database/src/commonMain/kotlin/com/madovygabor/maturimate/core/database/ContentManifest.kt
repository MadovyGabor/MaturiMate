package com.madovygabor.maturimate.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `content-manifest.json`: describes one release of the content database.
 * Written by the exporter (tools/content-pipeline), read by the app before it installs a content file.
 */
@Serializable
data class ContentManifest(
    @SerialName("content_version") val contentVersion: Long,
    @SerialName("schema_version") val schemaVersion: Long,
    @SerialName("file_name") val fileName: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    /** `FULL` (everything of the subjects in the file) or `STARTER` (the first lesson of each subject). */
    val kind: String = KIND_FULL,
    /** The subjects the file holds. Empty in a manifest written before packs existed (every subject in the file). */
    @SerialName("subject_ids") val subjectIds: List<Long> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this) + "\n"

    companion object {
        const val FILE_NAME = "content-manifest.json"
        const val KIND_FULL = "FULL"
        const val KIND_STARTER = "STARTER"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(text: String): ContentManifest = json.decodeFromString(serializer(), text)
    }
}
