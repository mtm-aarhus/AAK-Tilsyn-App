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
import okhttp3.OkHttpClient
import java.util.logging.Level
import java.util.logging.Logger

class MainActivity : ComponentActivity() {
    private val viewModel: VejmanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api = SecurePrefs.getApiKey(this)
        val email = SecurePrefs.getEmail(this)

        if (!api.isNullOrBlank() && email.isNullOrBlank()) {
            // Old session from previous app version → wipe it
            SecurePrefs.clearAll(this)
        }

        val isDarkTheme = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme



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
