package com.github.exact7.xtra.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.github.exact7.xtra.model.User
import javax.inject.Inject

class MainViewModel @Inject constructor(): ViewModel() {

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?>
        get() = _user
    private val _playerMaximized = MutableLiveData<Boolean>()
    val isPlayerMaximized: Boolean
        get() = _playerMaximized.value ?: false
    var isPlayerOpened = false
        private set
    private val _isNetworkAvailable = MutableLiveData<Boolean>()

    fun setUser(user: User?) {
        _user.value = user
    }

    fun playerMaximized(): LiveData<Boolean> {
        return _playerMaximized
    }

    fun onMaximize() {
        _playerMaximized.value = true
    }

    fun onMinimize() {
        if (_playerMaximized.value != false)
            _playerMaximized.value = false
    }

    fun onPlayerStarted() {
        isPlayerOpened = true
        _playerMaximized.value = true
    }

    fun onPlayerClosed() {
        isPlayerOpened = false
        _playerMaximized.value = false
    }

    fun isNetworkAvailable(): LiveData<Boolean> = _isNetworkAvailable

    fun setNetworkAvailable(isNetworkAvailable: Boolean) {
        _isNetworkAvailable.value = isNetworkAvailable
    }


}