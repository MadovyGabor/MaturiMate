package com.madovygabor.maturimate.features.appOnboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun InitialSetupScreen(
    onInitialSetupComplete: () -> Unit
){

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center

    ){

        Column {
            Text(
                text = "InitialSetupScreen",
                color = MaterialTheme.colorScheme.onBackground
            )
            Button(
                onClick = {
                    onInitialSetupComplete()
                }
            ) {
                Text(text = "Get Started")
            }
        }

    }
}