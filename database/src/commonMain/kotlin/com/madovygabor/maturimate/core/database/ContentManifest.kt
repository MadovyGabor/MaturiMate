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
) {
    fun toJson(): String = json.encodeToString(serializer(), this) + "\n"

    companion object {
        const val FILE_NAME = "content-manifest.json"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun fromJson(text: String): ContentManifest = json.decodeFromString(serializer(), text)
    }
}
