package com.madovygabor.maturimate


import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.example.compose.AppTheme
import com.madovygabor.maturimate.core.ui.MainFrame
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun App() {
    AppTheme {
//        // 1. Elindítjuk a navigációt a HomeScreen-nel
//        Navigator(HomeScreen()) { navigator ->
//            // 2. Beletesszük a globális keretbe
//            MainFrame {
//                // 3. Ez fogja kirajzolni azt a képernyőt, ahol éppen tartunk
//                CurrentScreen()
//            }
//        }
    }
}
@Composable
fun HomeScreen() {

}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    App()
}


