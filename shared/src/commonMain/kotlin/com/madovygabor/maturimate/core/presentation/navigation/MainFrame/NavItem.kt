package com.madovygabor.maturimate.core.presentation.navigation.MainFrame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.madovygabor.maturimate.core.presentation.navigation.Route
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.nav_home
import maturimate.shared.generated.resources.nav_practice
import maturimate.shared.generated.resources.nav_profile
import maturimate.shared.generated.resources.nav_subjects
import org.jetbrains.compose.resources.StringResource

/** A tab of the main screen: its icon (outlined, and filled when selected) and its label in the app language. */
data class BottomNavItem(
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val title: StringResource,
)

/** The four tabs (docs/navigation/prototype.html, decided on 2026-11-04). */
val TOP_LEVEL_NAVBAR_DESTINATIONS = mapOf(
    Route.Main.Home to BottomNavItem(Icons.Outlined.Home, Icons.Filled.Home, Res.string.nav_home),
    Route.Main.Subject to BottomNavItem(Icons.Outlined.MenuBook, Icons.Filled.MenuBook, Res.string.nav_subjects),
    Route.Main.Practice to BottomNavItem(Icons.Outlined.Lightbulb, Icons.Filled.Lightbulb, Res.string.nav_practice),
    Route.Main.Profile to BottomNavItem(Icons.Outlined.Person, Icons.Filled.Person, Res.string.nav_profile),
)
