package com.madovygabor.maturimate


import androidx.compose.runtime.Composable
import com.madovygabor.maturimate.core.theme.AppTheme
import com.madovygabor.maturimate.core.ui.MainFrame
import com.madovygabor.maturimate.core.ui.NavigationRoute
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun App() {

    AppTheme {

        MainFrame(
            content = {
                NavigationRoute()
            }
        )
//        NavigationRoute()
    }
}
@Composable
@Preview(showBackground = true)
private fun AppPreview() {
    App()
}


