package com.madovygabor.maturimate.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
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
import com.example.compose.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun MainFrame(
    currentScreen: @Composable () -> Unit // Ez a "lyuk", ahová a tartalom kerül
) {
    BoxWithConstraints {

        val isDesktop = maxWidth > 600.dp

        if (isDesktop) {
            // Desktop navigation side bar
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.width(100.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    header = { /* Application logo */ }
                ) {

                    //For item centering
                    Spacer(Modifier.weight(1f))

                    NavigationRailItem(
                        selected = true,
                        onClick = { /* Navigation */ },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Főoldal") }
                    )

                    NavigationRailItem(
                        selected = false,
                        onClick = { /* Navigation */ },
                        icon = { Icon(Icons.Default.History, null) },
                        label = { Text("Előzmények") }
                    )

                    NavigationRailItem(
                        selected = false,
                        onClick = { /* Navigation */ },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text("Profil") }
                    )

                    Spacer(Modifier.weight(1f))
                }

                // The main content area
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    currentScreen()
                }
            }
        } else {

            // Mobile bottom navigation bar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        NavigationBarItem(
                            selected = true,
                            onClick = { /* Navigáció */ },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("Főoldal") }
                        )

                        NavigationBarItem(
                            selected = false,
                            onClick = { /* Navigáció */ },
                            icon = { Icon(Icons.Default.History, null) },
                            label = { Text("Előzmények") }
                        )

                        NavigationBarItem(
                            selected = false,
                            onClick = { /* Navigáció */ },
                            icon = { Icon(Icons.Default.Person, null) },
                            label = { Text("Profil") }
                        )
                        // Többi ikon...
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    currentScreen()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 1920, heightDp = 1080)
@Composable
fun AppPreview() {
    AppTheme(darkTheme = false) {
        MainFrame {
            // Például a HomeScreen()
        }
    }
}