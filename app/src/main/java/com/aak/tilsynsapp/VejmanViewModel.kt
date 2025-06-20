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

    private val context = application.applicationContext
    private val dao = VejmanDatabase.getDatabase(context).vejmanDao()

    private val _rows = MutableStateFlow<List<VejmanKassenRow>>(emptyList())
    val rows: StateFlow<List<VejmanKassenRow>> = _rows

    private val _loadingStatus = MutableStateFlow<String?>(null)
    val loadingStatus: StateFlow<String?> = _loadingStatus

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Input)
    val loginState: StateFlow<LoginState> = _loginState

    private val _allRows = MutableStateFlow<Map<String, List<VejmanKassenRow>>>(emptyMap())
    val allRows: StateFlow<Map<String, List<VejmanKassenRow>>> = _allRows

    private val _activeFilter = MutableStateFlow("Ny")
    val activeFilter: StateFlow<String> = _activeFilter

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private var lastRefreshTime: Long = 0L

    init {
        val savedKey = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .getString("api_key", null)

        if (!savedKey.isNullOrBlank() && savedKey != "placeholder") {
            Secrets.API_KEY = savedKey
            _loginState.value = LoginState.LoggedIn
            Log.d("LoginFlow", "Restored saved API key and set LoggedIn state")
        } else {
            Log.d("LoginFlow", "No valid saved API key found, showing login")
        }
    }

    fun preloadAndMaybeRefresh(force: Boolean = false) {
        val shouldRefresh = force || System.currentTimeMillis() - lastRefreshTime > 5 * 60 * 1000
        viewModelScope.launch {
            _loadingStatus.value = "Henter fakturaer..."
            if (shouldRefresh) {
                fetchAllRowsAndCache()
            } else {
                val cached = dao.getAll()
                val grouped = cached.groupBy { it.fakturaStatus ?: "Ukendt" }
                _allRows.value = grouped
                setActiveFilter("Ny")
            }
            _loadingStatus.value = null
        }
    }

    suspend fun fetchAllRowsAndCache() {
        _isRefreshing.value = true
        _loadingStatus.value = "Henter fakturaer..."
        Log.d("Refresh", "Fetching all statuses")


        val tilFakturering = ApiHelper.getRowsByStatus("Til fakturering") ?: emptyList()
        val fakturerIkke = ApiHelper.getRowsByStatus("Fakturer ikke") ?: emptyList()
        val faktureret = ApiHelper.getRowsByStatus("Faktureret") ?: emptyList()
        val ny = ApiHelper.getRowsByStatus("Ny") ?: emptyList()

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
            fetchAllRowsAndCache()
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
            Log.d("LoginFlow", "Polling token: $token")
            val apiKey = ApiHelper.pollAuth(token)
            if (apiKey != null) {
                Log.d("LoginFlow", "API key received, login complete")
                saveApiKey(apiKey)
                _loginState.value = LoginState.LoggedIn
                onResult(true)
            } else {
                Log.d("LoginFlow", "Token not yet authorized")
                onResult(false)
            }
        }
    }

    private fun saveToken(token: String) {
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit().putString("token", token).apply()
    }

    fun loadSavedToken(): String? {
        return context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .getString("token", null)
    }

    private fun saveApiKey(apiKey: String) {
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit().putString("api_key", apiKey).apply()
        Secrets.API_KEY = apiKey
    }

    fun resetLogin() {
        _loginState.value = LoginState.Input
    }

    suspend fun updateRow(updatedRow: VejmanKassenRow, newStatus: String?): Boolean {
        val updates = mutableMapOf<String, Any?>()
        updatedRow.kvadratmeter?.let { updates["kvadratmeter"] = it }
        updatedRow.tilladelsestype?.let { updates["tilladelsestype"] = it }
        updatedRow.slutdato?.let { updates["slutdato"] = it }
        newStatus?.let { updates["fakturaStatus"] = it }

        val success = ApiHelper.updateRow(updatedRow.id, updates)
        if (success) {
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
        }
        return success
    }
}
