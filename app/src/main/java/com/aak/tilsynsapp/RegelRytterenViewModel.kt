package com.aak.tilsynsapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RegelRytterenViewModel(application: Application) : AndroidViewModel(application) {
    private val _bikes = MutableStateFlow(1)
    private val _cars = MutableStateFlow(1)
    private val _vejman = MutableStateFlow(true)
    private val _henstillinger = MutableStateFlow(true)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val bikes: StateFlow<Int> = _bikes
    val cars: StateFlow<Int> = _cars
    val vejman: StateFlow<Boolean> = _vejman
    val henstillinger: StateFlow<Boolean> = _henstillinger
    val statusMessage: StateFlow<String?> = _statusMessage

    fun setBikes(value: Int) { _bikes.value = value }
    fun setCars(value: Int) { _cars.value = value }
    fun setVejman(value: Boolean) { _vejman.value = value }
    fun setHenstillinger(value: Boolean) { _henstillinger.value = value }

    fun updateStatusMessage(message: String) {
        _statusMessage.value = message
    }

}