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
    object Input : LoginState()
    data class Waiting(val email: String) : LoginState()
    object LoggedIn : LoginState()
}

class VejmanViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = VejmanDatabase.getDatabase(getApplication()).vejmanDao()

    private val _rows = MutableStateFlow<List<VejmanKassenRow>>(emptyList())
    val rows: StateFlow<List<VejmanKassenRow>> = _rows

    private val _loadingStatus = MutableStateFlow<String?>(null)
    val loadingStatus: StateFlow<String?> = _loadingStatus

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Input)
    val loginState: StateFlow<LoginState> = _loginState

    private val _allRows = MutableStateFlow<Map<String, List<VejmanKassenRow>>>(emptyMap())
    //val allRows: StateFlow<Map<String, List<VejmanKassenRow>>> = _allRows

    private val _activeFilter = MutableStateFlow("Ny")
    //val activeFilter: StateFlow<String> = _activeFilter

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

                // Save the update message for LoginScreen
                _versionMessage.value = message ?: "Din app skal opdateres, gå ind i Play store og søg efter nyeste opdateringer."

                // Force logout
                SecurePrefs.clearAll(context)
                _loginState.value = LoginState.Input

                return@launch  // IMPORTANT
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



    fun preloadAndMaybeRefresh(force: Boolean = false) {
        val shouldRefresh = force || System.currentTimeMillis() - lastRefreshTime > 5 * 60 * 1000
        viewModelScope.launch {
            _loadingStatus.value = "Henter fakturaer..."
            if (shouldRefresh) {
                fetchAllRowsAndCache(getApplication())
            } else {
                val cached = dao.getAll()
                val grouped = cached.groupBy { it.fakturaStatus ?: "Ukendt" }

                _allRows.value = grouped
                setActiveFilter("Ny")

            }
            _loadingStatus.value = null
        }
    }

    suspend fun fetchAllRowsAndCache(context: Context) {
        _isRefreshing.value = true
        _loadingStatus.value = "Henter fakturaer..."
        Log.d("Refresh", "Fetching all statuses")

        val tilFakturering = ApiHelper.getRowsByStatus(context, "Til fakturering") ?: emptyList()
        val fakturerIkke = ApiHelper.getRowsByStatus(context, "Fakturer ikke") ?: emptyList()
        val faktureret = ApiHelper.getRowsByStatus(context, "Faktureret") ?: emptyList()
        val ny = ApiHelper.getRowsByStatus(context, "Ny") ?: emptyList()

        dao.clearAll()
        dao.insertAll(ny + tilFakturering + fakturerIkke + faktureret)

        val all = mapOf(
            "Ny" to ny,
            "Til fakturering" to tilFakturering,
            "Fakturer ikke" to fakturerIkke,
            "Faktureret" to faktureret
        )

        _allRows.value = all
        _rows.value = all[_activeFilter.value] ?: emptyList()
        lastRefreshTime = System.currentTimeMillis()
        _loadingStatus.value = null
        _isRefreshing.value = false
    }

    fun refreshDataAsync() {
        viewModelScope.launch {
            fetchAllRowsAndCache(getApplication())
        }
    }

    fun setActiveFilter(status: String) {
        _activeFilter.value = status
        _rows.value = _allRows.value[status] ?: emptyList()
    }

    fun sendLoginEmail(email: String) {
        Log.d("LoginFlow", "Sending login email to: $email")
        _loginState.value = LoginState.Waiting(email)
        viewModelScope.launch(Dispatchers.IO) {
            val token = ApiHelper.sendLoginRequest(email)
            if (token != null) {
                Log.d("LoginFlow", "Token received and saved: $token")
                saveToken(token)
            } else {
                Log.e("LoginFlow", "Failed to get token for $email")
            }
        }
    }

    fun pollAuthToken(token: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = ApiHelper.pollAuth(token)

            if (result != null) {
                val (apiKey, email) = result

                Log.d("LoginFlow", "API key + email received, completing login")

                val context = getApplication<Application>()

                // Save API key
                SecurePrefs.saveApiKey(context, apiKey)

                // Save email if server returned it
                if (!email.isNullOrBlank()) {
                    SecurePrefs.saveEmail(context, email)
                }

                // Update login timestamp (for 90-day expiration)
                SecurePrefs.saveLoginTimestamp(context)

                // Update UI state
                _loginState.value = LoginState.LoggedIn

                onResult(true)
            } else {
                Log.d("LoginFlow", "Token not yet authorized")
                onResult(false)
            }
        }
    }


    private fun saveToken(token: String) {
        SecurePrefs.saveToken(getApplication(), token)
    }

    fun loadSavedToken(): String? {
        return SecurePrefs.getToken(getApplication())
    }

    fun resetLogin() {
        SecurePrefs.clearAll(getApplication())
        _loginState.value = LoginState.Input
    }


    suspend fun updateRow(context: Context, updatedRow: VejmanKassenRow, newStatus: String?): Boolean {
        val updates = mutableMapOf<String, Any?>()

        val originalRow = _allRows.value
            .flatMap { it.value }
            .find { it.id == updatedRow.id }

        if (originalRow != null) {
            if (updatedRow.kvadratmeter != originalRow.kvadratmeter) {
                updatedRow.kvadratmeter?.let { updates["kvadratmeter"] = it }
            }

            if (updatedRow.tilladelsestype != originalRow.tilladelsestype) {
                updatedRow.tilladelsestype?.let { updates["tilladelsestype"] = it }
            }

            if (updatedRow.slutdato != originalRow.slutdato) {
                updatedRow.slutdato?.let { updates["slutdato"] = it }
            }
        } else {
            // fallback if row not found in cache (should not happen)
            updatedRow.kvadratmeter?.let { updates["kvadratmeter"] = it }
            updatedRow.tilladelsestype?.let { updates["tilladelsestype"] = it }
            updatedRow.slutdato?.let { updates["slutdato"] = it }
        }

        newStatus?.let { updates["fakturaStatus"] = it }


        if (updates.isEmpty()) {
            Log.w("UpdateRow", "No changed fields to update for ID=${updatedRow.id}")
            return false
        }

        val success = ApiHelper.updateRow(
            context = context,
            id = updatedRow.id,
            updates = updates,
            oldStatus = originalRow?.fakturaStatus ?: updatedRow.fakturaStatus,
            newStatus = newStatus ?: updatedRow.fakturaStatus
        )

        if (!success) {
            Log.e("UpdateRow", "API update failed for ID ${updatedRow.id}")
            return false
        }

        // Local cache update
        val newRow = updatedRow.copy(fakturaStatus = newStatus ?: updatedRow.fakturaStatus)
        dao.updateRow(newRow)

        val all = _allRows.value.toMutableMap()
        val status = newRow.fakturaStatus ?: "Ny"
        val updatedList = all[status]?.map {
            if (it.id == newRow.id) newRow else it
        } ?: listOf(newRow)
        all[status] = updatedList
        _allRows.value = all
        setActiveFilter(_activeFilter.value)

        return true
    }

}
