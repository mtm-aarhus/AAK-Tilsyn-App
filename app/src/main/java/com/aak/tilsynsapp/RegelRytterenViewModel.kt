package com.aak.tilsynsapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class InspectorConfig(
    val initial: String,
    val name: String,
    val vehicle: String, // "Bil" or "Cykel"
    val isIncluded: Boolean = false
)

class RegelRytterenViewModel(application: Application) : AndroidViewModel(application) {
    private val _inspectors = MutableStateFlow(listOf(
        InspectorConfig("HAROB", "Harald", "Cykel"),
        InspectorConfig("DJI", "Jimmy", "Bil"),
        InspectorConfig("MOJUS", "Just", "Cykel")
    ))
    val inspectors: StateFlow<List<InspectorConfig>> = _inspectors

    private val _vejman = MutableStateFlow(true)
    private val _henstillinger = MutableStateFlow(true)

    val vejman: StateFlow<Boolean> = _vejman
    val henstillinger: StateFlow<Boolean> = _henstillinger

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _successLockout = MutableStateFlow(false)
    val successLockout: StateFlow<Boolean> = _successLockout

    private val _countdown = MutableStateFlow(300)
    val countdown: StateFlow<Int> = _countdown

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private var timerJob: Job? = null

    fun toggleInspector(initial: String) {
        _inspectors.value = _inspectors.value.map {
            if (it.initial == initial) it.copy(isIncluded = !it.isIncluded) else it
        }
    }

    fun setVehicle(initial: String, vehicle: String) {
        _inspectors.value = _inspectors.value.map {
            if (it.initial == initial) it.copy(vehicle = vehicle) else it
        }
    }

    fun setVejman(value: Boolean) { _vejman.value = value }
    fun setHenstillinger(value: Boolean) { _henstillinger.value = value }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun submit(context: Context) {
        if (_isSubmitting.value || _successLockout.value) return

        val included = _inspectors.value.filter { it.isIncluded }
        if (included.isEmpty()) {
            _statusMessage.value = "Vælg mindst én tilsynsførende"
            return
        }
        
        if (!_vejman.value && !_henstillinger.value) {
            _statusMessage.value = "Vælg mindst én type: Tilladelser eller Henstillinger"
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            
            // Map the included inspectors to a list of maps for the JSON payload
            val inspectorData = included.map { 
                mapOf("initial" to it.initial, "vehicle" to it.vehicle) 
            }

            val result = ApiHelper.sendRegelrytterenPayload(
                context = context,
                inspectors = inspectorData,
                vejman = _vejman.value,
                henstillinger = _henstillinger.value
            )
            _statusMessage.value = result
            _isSubmitting.value = false
            if (result == "Success") {
                startLockout()
            }
        }
    }

    private fun startLockout() {
        _successLockout.value = true
        _countdown.value = 300
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_countdown.value > 0) {
                delay(1000)
                _countdown.value -= 1
            }
            _successLockout.value = false
        }
    }
}
