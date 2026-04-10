package com.aak.tilsynsapp

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


sealed class LoginState {
    object Loading : LoginState()
    object Input : LoginState()
    data class Waiting(val email: String) : LoginState()
    object LoggedIn : LoginState()
}

class VejmanViewModel(application: Application) : AndroidViewModel(application) {

    private val _tilsynItems = MutableStateFlow<List<TilsynItem>>(emptyList())
    val tilsynItems: StateFlow<List<TilsynItem>> = _tilsynItems

    private val _historyItems = MutableStateFlow<List<TilsynItem>>(emptyList())
    val historyItems: StateFlow<List<TilsynItem>> = _historyItems

    private val _loadingStatus = MutableStateFlow<String?>(null)
    val loadingStatus: StateFlow<String?> = _loadingStatus

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Loading)
    val loginState: StateFlow<LoginState> = _loginState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private var lastRefreshTime: Long = 0L

    private val _versionMessage = MutableStateFlow<String?>(null)
    val versionMessage: StateFlow<String?> = _versionMessage

    init {
        viewModelScope.launch {
            val context = getApplication<Application>()

            // -------- VERSION CHECK --------
            val versionInfo = ApiHelper.fetchVersionInfo()
            val minVersion = (versionInfo?.get("min_version") as? Double)?.toInt() ?: 1
            val message = versionInfo?.get("message") as? String

            if (BuildConfig.VERSION_CODE < minVersion) {
                Log.e("Version", "App version too old: $message")
                _versionMessage.value = message ?: "Din app skal opdateres, gå ind i Play store og søg efter nyeste opdateringer."
                SecurePrefs.clearAll(context)
                _loginState.value = LoginState.Input
                return@launch
            }

            // -------- NORMAL LOGIN RESTORE --------
            val savedKey = SecurePrefs.getApiKey(context)
            if (!savedKey.isNullOrBlank() && !SecurePrefs.isLoginExpired(context)) {
                _loginState.value = LoginState.LoggedIn
            } else {
                SecurePrefs.clearAll(context)
                _loginState.value = LoginState.Input
            }
        }
    }

    fun refreshDataAsync() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _loadingStatus.value = "Henter opgaver..."
            
            val context = getApplication<Application>()
            val tasks = ApiHelper.getUnifiedTasks(context)
            val history = ApiHelper.getUnifiedHistory(context)

            if (tasks != null) {
                _tilsynItems.value = tasks
            }
            if (history != null) {
                _historyItems.value = history
            }

            lastRefreshTime = System.currentTimeMillis()
            _loadingStatus.value = null
            _isRefreshing.value = false
        }
    }

    fun inspectPermission(
        id: String,
        comment: String,
        selection: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _loadingStatus.value = "Gemmer tilsyn..."
            val success = ApiHelper.unifiedInspect(
                context = getApplication(),
                id = id,
                type = "permission",
                comment = comment,
                selection = selection
            )
            if (success) {
                refreshDataAsync()
            }
            _loadingStatus.value = null
            onResult(success)
        }
    }

    fun hidePermission(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _loadingStatus.value = "Skjuler..."
            val success = ApiHelper.unifiedInspect(
                context = getApplication(),
                id = id,
                type = "permission",
                comment = "Skjult fra tilsyn",
                updates = mapOf("hidden" to true)
            )
            if (success) {
                refreshDataAsync()
            }
            _loadingStatus.value = null
            onResult(success)
        }
    }

    fun uploadImage(id: String, imageBytes: ByteArray, fileName: String? = null, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = ApiHelper.uploadImage(getApplication(), id, imageBytes, fileName)
            onResult(success)
        }
    }

    suspend fun updateRow(context: Context, updatedRow: TilsynItem, newStatus: String?, comment: String? = null): Boolean {
        _loadingStatus.value = "Opdaterer..."
        
        val updates = mutableMapOf<String, Any?>()
        updatedRow.kvadratmeter?.let { updates["kvadratmeter"] = it }
        updatedRow.slutdatoHenstilling?.let { updates["slutdato"] = it }
        newStatus?.let { updates["fakturaStatus"] = it }

        val success = ApiHelper.unifiedInspect(
            context = context,
            id = updatedRow.id,
            type = "henstilling",
            comment = comment,
            oldStatus = updatedRow.fakturaStatus,
            updates = updates
        )

        if (success) {
            refreshDataAsync()
        }
        
        _loadingStatus.value = null
        return success
    }

    fun sendLoginEmail(email: String) {
        _loginState.value = LoginState.Waiting(email)
        viewModelScope.launch(Dispatchers.IO) {
            val token = ApiHelper.sendLoginRequest(email)
            if (token != null) {
                SecurePrefs.saveToken(getApplication(), token)
            }
        }
    }

    fun pollAuthToken(token: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = ApiHelper.pollAuth(token)
            if (result != null) {
                val (apiKey, email) = result
                val context = getApplication<Application>()
                SecurePrefs.saveApiKey(context, apiKey)
                if (!email.isNullOrBlank()) SecurePrefs.saveEmail(context, email)
                SecurePrefs.saveLoginTimestamp(context)
                _loginState.value = LoginState.LoggedIn
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun loadSavedToken(): String? = SecurePrefs.getToken(getApplication())

    fun resetLogin() {
        SecurePrefs.clearAll(getApplication())
        _loginState.value = LoginState.Input
    }
}
