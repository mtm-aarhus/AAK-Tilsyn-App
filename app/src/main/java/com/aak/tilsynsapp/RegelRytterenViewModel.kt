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

class RegelRytterenViewModel(application: Application) : AndroidViewModel(application) {
    private val _bikes = MutableStateFlow(1)
    private val _cars = MutableStateFlow(1)
    private val _vejman = MutableStateFlow(true)
    private val _henstillinger = MutableStateFlow(true)

    val bikes: StateFlow<Int> = _bikes
    val cars: StateFlow<Int> = _cars
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

    fun setBikes(value: Int) { _bikes.value = value }
    fun setCars(value: Int) { _cars.value = value }
    fun setVejman(value: Boolean) { _vejman.value = value }
    fun setHenstillinger(value: Boolean) { _henstillinger.value = value }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun submit(context: Context) {
        if (_isSubmitting.value || _successLockout.value) return

        if ((_bikes.value + _cars.value) == 0) {
            _statusMessage.value = "Du skal vælge mindst én cykel eller bil"
            return
        }
        if (!_vejman.value && !_henstillinger.value) {
            _statusMessage.value = "Vælg mindst én type: Tilladelser eller Henstillinger"
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val result = ApiHelper.sendRegelrytterenPayload(
                context = context,
                bikes = _bikes.value,
                cars = _cars.value,
                vejman = _vejman.value,
                henstillinger = _henstillinger.value
            )
            _statusMessage.value = result
            _isSubmitting.value = false
            startLockout()
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
