package com.aak.tilsynsapp

import androidx.compose.runtime.*
import com.aak.tilsynsapp.ui.theme.TilsynsAppTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val viewModel: VejmanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT  // Make status bar fully transparent
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false


        setContent {
            TilsynsAppTheme {
                AppRoot(viewModel)
            }
        }
    }


    private var firstResume = true

    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
            return
        }
        viewModel.refreshDataAsync()
    }

}

@Composable
fun AppRoot(viewModel: VejmanViewModel) {
    val loginState by viewModel.loginState.collectAsState()
    var currentScreen by remember { mutableStateOf("Fakturering") }

    TilsynsAppTheme {
        when (loginState) {
            is LoginState.LoggedIn -> {
                when (currentScreen) {
                    "Fakturering" -> VejmanMainScreen(
                        viewModel = viewModel,
                        onNavigateToRegelrytteren = { currentScreen = "RegelRytteren" }
                    )
                    "RegelRytteren" -> RegelRytterenScreen(
                        onBack = { currentScreen = "Fakturering" }
                    )
                }
            }
            else -> LoginScreen(viewModel)
        }
    }
}
