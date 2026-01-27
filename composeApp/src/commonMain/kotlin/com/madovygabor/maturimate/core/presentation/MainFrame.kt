package com.madovygabor.maturimate.core.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.madovygabor.maturimate.core.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.navigation.TOP_LEVEL_NAVBAR_DESTINATIONS
import org.jetbrains.compose.ui.tooling.preview.Preview


// MainFrame composable that provides responsive bottom or side navigation based on screen size.
@Composable
fun MainFrame(
    selectedKey: NavKey,// The currently selected navigation key
    onSelectedKey: (NavKey) -> Unit,// Callback when a navigation item is selected
    content: @Composable () -> Unit
) {


    BoxWithConstraints {
        // Determine if the device is considered a desktop based on maxWidth
        val isDesktop = maxWidth > LayoutConstants.DesktopThreshold


        if (isDesktop) {
            // Desktop navigation side bar
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.width(100.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    header = { /* Application logo */ }
                ) {

                    //For item centering
                    Spacer(Modifier.weight(1f))

                    // Dynamically create navigation items from the TOP_LEVEL_NAV_ITEMS map
                    TOP_LEVEL_NAVBAR_DESTINATIONS.forEach { (topLevelDestination, data) ->

                        // Determine if the current item is selected
                        val isSelected = selectedKey == topLevelDestination
                        val iconToShow = if (isSelected) data.selectedIcon else data.icon

                        NavigationRailItem(
                            selected = selectedKey == topLevelDestination,
                            onClick = { onSelectedKey(topLevelDestination) },
                            icon = { Icon(iconToShow, data.title) },
                            label = { Text(data.title) }
                        )

                    }
                    //For item centering
                    Spacer(Modifier.weight(1f))
                }

                // The main content area
                Box(Modifier.fillMaxSize()) {
                    content()
                }
            }
        } else {

            // Mobile bottom navigation bar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        // Dynamically create navigation items from the TOP_LEVEL_NAVBAR_DESTINATIONS map
                        TOP_LEVEL_NAVBAR_DESTINATIONS.forEach { (topLevelDestination, data) ->
                            // Determine if the current item is selected
                            val isSelected = selectedKey == topLevelDestination
                            val iconToShow = if (isSelected) data.selectedIcon else data.icon

                            NavigationBarItem(
                                selected = selectedKey == topLevelDestination,
                                onClick = { onSelectedKey(topLevelDestination) },
                                icon = { Icon(iconToShow, data.title,) },
                                label = { Text(data.title) },
                            )

                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 1920, heightDp = 1080)
@Composable
fun AppPreview() {
    AppTheme(darkTheme = true) {
        MainFrame (
            selectedKey = Route.Main.Subject,
            onSelectedKey = {},
            content = {
                Text("Hello, World!", modifier = Modifier.padding(16.dp))
            }
        )
    }
}