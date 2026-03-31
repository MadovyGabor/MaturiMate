package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferences

/**
 * Domain model representing an academic subject.
 * @param uiId Unique identifier for the subject, used for UI mapping and data retrieval.
 * @param name Localized name of the subject.
 * @param icon The [ImageVector] associated with the subject.
 * @param color Representative color used in the UI.
 * @param level Optional academic level or difficulty (e.g., "B2" for languages).
 * @param dbIdMapping Optional mapping of database-specific IDs to this subject's ID, allowing for flexible integration with various data sources.
 */
data class Subject(
    val uiId: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val level: String? = null,
    val dbIdMapping: Map<String, String> = emptyMap()
)

/**
 * UI-related constants for responsive design.
 * @property DesktopThreshold The minimum width in dp to consider the layout as "desktop".
 */
object LayoutConstants {
    val DesktopThreshold = 600.dp
}

/**
 * Static provider for predefined subject data.
 */
object SubjectStaticData {

    // Internal configuration map for subjects
    private val configs = mapOf(
        "MATH" to Subject(
            uiId = "MATH",
            name = "Matematika",
            icon = Icons.Outlined.Calculate,
            color = Color(0xFF4B88E0),
            dbIdMapping = mapOf(
                //For subjects with different database IDs based on the school type, we can provide a mapping here.
                "HU" to "HU_MAT",
                "SK" to "SK_MAT"
            )
        ),
        "ENGLISH" to Subject(
            uiId = "ENGLISH",
            name = "Angol nyelv",
            icon = Icons.Outlined.Language,
            color = Color(0xFF4CA16A),
            level = "B2",
            //Mapping database IDs for different levels of English exams not based on school type.
            dbIdMapping = mapOf(
                "B1" to "AJ_B1",
                "B2" to "AJ_B2"
            )
        ),
        "SLOVAK" to Subject(
            uiId = "SLOVAK",
            name = "Szlovák nyelv és irodalom",
            icon = Icons.Outlined.DriveFileRenameOutline,
            color = Color(0xFFEA5D56),
            dbIdMapping = mapOf(
                //For subjects with different database IDs based on the school type, we can provide a mapping here.
                "HU" to "HU_SJSL",
                "SK" to "SK_SJL"
            )
        ),
        "HUNGARIAN" to Subject(
            uiId = "HUNGARIAN",
            name = "Magyar nyelv és irodalom",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            color = Color(0xFFFFB74D),
            dbIdMapping = mapOf(
                //For subjects with different database IDs based on the school type, we can provide a mapping here.
                "HU" to "HU_MJL",
                //Assuming there's no Slovak equivalent for Hungarian language and literature, we can mark it as undefined or simply omit it from the mapping.
            )
        )
    )

    /**
     * This method abstracts away the logic of determining the correct database ID for
     * subjects that have different IDs based on the school type or language level.
     */
    fun getDbId(uiId: String, prefs: UserPreferences): String? {
        val subject = configs[uiId] ?: return null

        // Determine the key to look up in the dbIdMapping based on the subject and user preferences.
        val key = if (uiId == "ENGLISH") {
            prefs.maturitaLevel.name
        } else {
            prefs.maturitaLanguage.name
        }

        return subject.dbIdMapping[key]
    }

    /**
     * Retrieves a subject by its unique ID.
     * Returns an "Unknown" subject fallback to prevent application crashes on invalid IDs.
     */
    fun getById(id: String): Subject {
        return configs[id] ?: Subject(
            uiId = "UNKNOWN",
            name = "Unknown Subject",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            color = Color.Gray
        )
    }

    /**
     * Returns all available subjects as a list for UI display.
     */
    fun getAll(): List<Subject> = configs.values.toList()
}