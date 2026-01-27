package com.madovygabor.maturimate.core.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

// Data class representing a navigation item with an icon and title.
data class BottomNavItem (
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val title: String,
)

// Map of top-level navigation items used in the application.
val TOP_LEVEL_NAVBAR_DESTINATIONS = mapOf(
    Route.Main.Subject to BottomNavItem(
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
        title = "Főoldal",
    ),
    Route.Main.History to BottomNavItem(
        icon = Icons.Outlined.History,
        selectedIcon = Icons.Filled.History,
        title = "Előzmények",
    ),
    Route.Main.Profile to BottomNavItem(
        icon = Icons.Outlined.Person,
        selectedIcon = Icons.Filled.Person,
        title = "Profil",
    )
)





