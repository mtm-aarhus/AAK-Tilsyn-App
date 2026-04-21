package com.aak.tilsynsapp

import androidx.compose.runtime.*
import com.aak.tilsynsapp.ui.theme.TilsynsAppTheme
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.logging.Level
import java.util.logging.Logger

class MainActivity : ComponentActivity() {
    private val viewModel: TilsynViewModel by viewModels()

    private lateinit var appUpdater: AppUpdater
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.w("AppUpdater", "Update flow did not complete (resultCode=${result.resultCode})")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — user can still use the app without notifications */ }

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

        appUpdater = AppUpdater(this)
        appUpdater.checkForUpdate(updateLauncher)

        TilsynMessagingService.ensureChannel(this)
        maybeRequestNotificationPermission()

        handleDeepLinkIntent(intent)

        setContent {
            TilsynsAppTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val itemId = intent?.getStringExtra(TilsynMessagingService.EXTRA_OPEN_ITEM_ID) ?: return
        viewModel.requestOpenItemOnMap(itemId)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    private var firstResume = true

    override fun onResume() {
        super.onResume()
        if (::appUpdater.isInitialized) {
            appUpdater.resumeIfInProgress(updateLauncher)
        }
        if (firstResume) {
            firstResume = false
            return
        }
        viewModel.refreshDataAsync()
    }

    fun triggerUpdate() {
        if (::appUpdater.isInitialized) {
            appUpdater.checkForUpdate(updateLauncher)
        }
    }

}

@Composable
fun AppRoot(viewModel: TilsynViewModel) {
    val loginState by viewModel.loginState.collectAsState()
    val pendingDeepLinkItemId by viewModel.pendingDeepLinkItemId.collectAsState()
    var currentScreen by remember { mutableStateOf("Tilsyn") }

    LaunchedEffect(loginState, pendingDeepLinkItemId) {
        val pending = pendingDeepLinkItemId
        if (pending != null && loginState is TilsynLoginState.LoggedIn) {
            currentScreen = "Map"
            var item = viewModel.findItemById(pending)
            if (item == null) {
                viewModel.refreshDataAsync()
                // Wait briefly for items to load before resolving by id.
                repeat(20) {
                    delay(250)
                    item = viewModel.findItemById(pending)
                    if (item != null) return@repeat
                }
            }
            item?.let { viewModel.selectMapItem(it) }
            viewModel.consumePendingDeepLink()
        }
    }

    TilsynsAppTheme {
        when (loginState) {
            is TilsynLoginState.LoggedIn -> {
                when (currentScreen) {
                    "Tilsyn" -> TilsynScreen(
                        viewModel = viewModel,
                        onNavigateToRegelrytteren = { currentScreen = "RegelRytteren" },
                        onNavigateToHistory = { currentScreen = "History" },
                        onNavigateToMap = { currentScreen = "Map" },
                        onNavigateToCreateIndmeldt = { currentScreen = "CreateIndmeldt" }
                    )
                    "RegelRytteren" -> RegelRytterenScreen(
                        onNavigateToTilsyn = { currentScreen = "Tilsyn" },
                        onNavigateToHistory = { currentScreen = "History" },
                        onNavigateToMap = { currentScreen = "Map" }
                    )
                    "History" -> HistoryScreen(
                        viewModel = viewModel,
                        onNavigateToTilsyn = { currentScreen = "Tilsyn" },
                        onNavigateToRegelrytteren = { currentScreen = "RegelRytteren" },
                        onNavigateToHistory = { currentScreen = "History" },
                        onNavigateToMap = { currentScreen = "Map" }
                    )
                    "Map" -> MapScreen(
                        viewModel = viewModel,
                        onNavigateToTilsyn = { currentScreen = "Tilsyn" },
                        onNavigateToRegelrytteren = { currentScreen = "RegelRytteren" },
                        onNavigateToHistory = { currentScreen = "History" },
                        onNavigateToMap = { currentScreen = "Map" }
                    )
                    "CreateIndmeldt" -> CreateIndmeldtScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = "Tilsyn" },
                        onCreated = { currentScreen = "Tilsyn" }
                    )
                }
            }
            else -> LoginScreen(viewModel)
        }
    }
}
