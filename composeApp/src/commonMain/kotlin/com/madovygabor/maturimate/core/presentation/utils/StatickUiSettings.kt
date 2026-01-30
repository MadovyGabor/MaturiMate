package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Domain model representing an academic subject.
 * * @param id Unique identifier for the subject (e.g., "MATH").
 * @param name Localized name of the subject.
 * @param icon The [ImageVector] associated with the subject.
 * @param color Representative color used in the UI.
 * @param level Optional academic level or difficulty (e.g., "B2" for languages).
 */
data class Subject(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val level: String? = null
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
        "ENGLISH" to Subject(
            id = "ENGLISH",
            name = "Angol nyelv",
            icon = Icons.Outlined.Language,
            color = Color(0xFF4CA16A),
            level = "B2"
        ),
        "SLOVAK" to Subject(
            id = "SLOVAK",
            name = "Szlovák nyelv és irodalom",
            icon = Icons.Outlined.DriveFileRenameOutline,
            color = Color(0xFFEA5D56)
        ),
        "HUNGARIAN" to Subject(
            id = "HUNGARIAN",
            name = "Magyar nyelv és irodalom",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            color = Color(0xFFFFB74D)
        ),
        "MATH" to Subject(
            id = "MATH",
            name = "Matematika",
            icon = Icons.Outlined.Calculate,
            color = Color(0xFF4B88E0)
        )
    )

    /**
     * Retrieves a subject by its unique ID.
     * Returns an "Unknown" subject fallback to prevent application crashes on invalid IDs.
     */
    fun getById(id: String): Subject {
        return configs[id] ?: Subject(
            id = "UNKNOWN",
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