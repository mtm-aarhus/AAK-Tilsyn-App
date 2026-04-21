package com.aak.tilsynsapp

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


sealed class TilsynLoginState {
    object Loading : TilsynLoginState()
    object Input : TilsynLoginState()
    data class Waiting(val email: String) : TilsynLoginState()
    object LoggedIn : TilsynLoginState()
}

class TilsynViewModel(application: Application) : AndroidViewModel(application) {

    private val _tilsynItems = MutableStateFlow<List<TilsynItem>>(emptyList())
    val tilsynItems: StateFlow<List<TilsynItem>> = _tilsynItems

    private val _historyItems = MutableStateFlow<List<TilsynItem>>(emptyList())
    val historyItems: StateFlow<List<TilsynItem>> = _historyItems

    private val _loadingStatus = MutableStateFlow<String?>(null)
    val loadingStatus: StateFlow<String?> = _loadingStatus

    private val _loginState = MutableStateFlow<TilsynLoginState>(TilsynLoginState.Loading)
    val loginState: StateFlow<TilsynLoginState> = _loginState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private var lastRefreshTime: Long = 0L

    private val _versionMessage = MutableStateFlow<String?>(null)
    val versionMessage: StateFlow<String?> = _versionMessage

    private val _selectedMapItem = MutableStateFlow<TilsynItem?>(null)
    val selectedMapItem: StateFlow<TilsynItem?> = _selectedMapItem

    fun selectMapItem(item: TilsynItem?) {
        _selectedMapItem.value = item
    }

    private val _pendingDeepLinkItemId = MutableStateFlow<String?>(null)
    val pendingDeepLinkItemId: StateFlow<String?> = _pendingDeepLinkItemId

    fun requestOpenItemOnMap(itemId: String?) {
        if (itemId.isNullOrBlank()) return
        _pendingDeepLinkItemId.value = itemId
    }

    fun consumePendingDeepLink(): String? {
        val id = _pendingDeepLinkItemId.value
        _pendingDeepLinkItemId.value = null
        return id
    }

    fun findItemById(id: String): TilsynItem? {
        return _tilsynItems.value.firstOrNull { it.id == id }
            ?: _historyItems.value.firstOrNull { it.id == id }
    }

    fun registerFcmTokenAsync() {
        val context = getApplication<Application>()
        FirebaseMessaging.getInstance().token.addOnCompleteListener(
            OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("TilsynFcm", "Fetch FCM token failed", task.exception)
                    return@OnCompleteListener
                }
                val token = task.result ?: return@OnCompleteListener
                viewModelScope.launch(Dispatchers.IO) {
                    ApiHelper.registerFcmToken(context, token)
                }
            }
        )
    }

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
                _loginState.value = TilsynLoginState.Input
                return@launch
            }

            // -------- NORMAL LOGIN RESTORE --------
            val savedKey = SecurePrefs.getApiKey(context)
            if (!savedKey.isNullOrBlank() && !SecurePrefs.isLoginExpired(context)) {
                _loginState.value = TilsynLoginState.LoggedIn
                registerFcmTokenAsync()
            } else {
                SecurePrefs.clearAll(context)
                _loginState.value = TilsynLoginState.Input
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
                _tilsynItems.value = tasks.filter { it.hidden != true }
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

    fun toggleHidePermission(id: String, hide: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _loadingStatus.value = if (hide) "Skjuler..." else "Gendannet..."
            val success = ApiHelper.unifiedInspect(
                context = getApplication(),
                id = id,
                type = "permission",
                comment = if (hide) "Skjult fra tilsyn" else "Gendannet fra historik",
                updates = mapOf("hidden" to hide)
            )
            if (success) {
                refreshDataAsync()
            }
            _loadingStatus.value = null
            onResult(success)
        }
    }

    fun hidePermission(id: String, onResult: (Boolean) -> Unit) {
        toggleHidePermission(id, true, onResult)
    }

    fun inspectIndmeldt(id: String, comment: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _loadingStatus.value = "Gemmer tilsyn..."
            // Server looks up item_type from Cosmos, so the payload 'type' is only for logging.
            val success = ApiHelper.unifiedInspect(
                context = getApplication(),
                id = id,
                type = "indmeldt",
                comment = comment
            )
            if (success) {
                refreshDataAsync()
            }
            _loadingStatus.value = null
            onResult(success)
        }
    }

    fun createIndmeldt(
        fullAddress: String,
        streetName: String?,
        latitude: Double,
        longitude: Double,
        title: String,
        description: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _loadingStatus.value = "Opretter indmeldt tilsyn..."
            val context = getApplication<Application>()
            val email = SecurePrefs.getEmail(context) ?: ""
            val initials = email.substringBefore("@").uppercase().ifBlank { "UNKNOWN" }

            val result = ApiHelper.createIndmeldt(
                context = context,
                fullAddress = fullAddress,
                streetName = streetName,
                latitude = latitude,
                longitude = longitude,
                title = title,
                description = description,
                createdBy = initials,
                createdBySource = "app",
            )
            if (result.success) {
                refreshDataAsync()
            }
            _loadingStatus.value = null
            onResult(result.success, result.caseNumber)
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
        updatedRow.endDate?.let { updates["end_date"] = it }
        newStatus?.let { 
            updates["fakturaStatus"] = it 
            if (it == "Fakturer ikke") {
                updates["hidden"] = true
            } else if (it == "Ny") {
                updates["hidden"] = false
            }
        }

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
        _loginState.value = TilsynLoginState.Waiting(email)
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
                _loginState.value = TilsynLoginState.LoggedIn
                registerFcmTokenAsync()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun loadSavedToken(): String? = SecurePrefs.getToken(getApplication())

    fun resetLogin() {
        SecurePrefs.clearAll(getApplication())
        _loginState.value = TilsynLoginState.Input
    }
}
