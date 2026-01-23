package com.madovygabor.maturimate


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.theme.AppTheme
import com.madovygabor.maturimate.core.ui.navigation.NavigationRoute
import com.madovygabor.maturimate.core.ui.systemPadding
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun App() {
    AppTheme {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
        ){
            NavigationRoute(
                modifier = Modifier.systemPadding()
            )
        }

    }
}
@Composable
@Preview(showBackground = true)
private fun AppPreview() {
    App()
}


