package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HostRule
import com.example.data.HostRuleRepository
import com.example.network.LocalHostsProxyServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HostsBrowserViewModel(
    private val repository: HostRuleRepository,
    application: Application
) : AndroidViewModel(application) {

    val hostRules: StateFlow<List<HostRule>> = repository.allRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _proxyPort = MutableStateFlow(0)
    val proxyPort = _proxyPort.asStateFlow()

    private val _isProxyActive = MutableStateFlow(false)
    val isProxyActive = _isProxyActive.asStateFlow()

    private val _browserUrl = MutableStateFlow("https://districts.upeoffice.gov.in")
    val browserUrl = _browserUrl.asStateFlow()

    private val _ignoreSslErrors = MutableStateFlow(true) // Intranet government portals often use internal CAs, so default to true for user convenience
    val ignoreSslErrors = _ignoreSslErrors.asStateFlow()

    private val _vpnActiveHint = MutableStateFlow(true) // Displays AnyConnect integration tips
    val vpnActiveHint = _vpnActiveHint.asStateFlow()

    private var proxyServer: LocalHostsProxyServer? = null

    init {
        // Build the proxy server instance, providing rules from database
        proxyServer = LocalHostsProxyServer {
            repository.getActiveRules()
        }
        startProxy()
    }

    fun startProxy() {
        proxyServer?.let { server ->
            if (!server.isRunning) {
                server.start()
                viewModelScope.launch {
                    // Slight delay to ensure socket binding is complete
                    kotlinx.coroutines.delay(200)
                    _proxyPort.value = server.activePort
                    _isProxyActive.value = server.isRunning
                }
            }
        }
    }

    fun stopProxy() {
        proxyServer?.let { server ->
            if (server.isRunning) {
                server.stop()
                _proxyPort.value = 0
                _isProxyActive.value = false
            }
        }
    }

    fun toggleProxy() {
        if (_isProxyActive.value) {
            stopProxy()
        } else {
            startProxy()
        }
    }

    fun refreshProxyRules() {
        proxyServer?.refreshCache()
    }

    fun addHostRule(hostname: String, ipAddress: String, description: String = "") {
        viewModelScope.launch {
            val rule = HostRule(
                hostname = hostname.trim(),
                ipAddress = ipAddress.trim(),
                isEnabled = true,
                description = description.trim()
            )
            repository.insert(rule)
            refreshProxyRules()
        }
    }

    fun toggleRuleEnabled(rule: HostRule) {
        viewModelScope.launch {
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            repository.update(updated)
            refreshProxyRules()
        }
    }

    fun deleteRule(rule: HostRule) {
        viewModelScope.launch {
            repository.delete(rule)
            refreshProxyRules()
        }
    }

    fun updateBrowserUrl(url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isNotEmpty()) {
            if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                // If the input doesn't contain a dot or has spaces, treat it as a Google Search query
                val isSearch = !formattedUrl.contains(".") || formattedUrl.contains(" ")
                if (isSearch) {
                    val encoded = try {
                        java.net.URLEncoder.encode(formattedUrl, "UTF-8")
                    } catch (e: Exception) {
                        formattedUrl
                    }
                    formattedUrl = "https://www.google.com/search?q=$encoded"
                } else {
                    formattedUrl = "https://$formattedUrl"
                }
            }
            _browserUrl.value = formattedUrl
        }
    }

    fun setIgnoreSslErrors(ignore: Boolean) {
        _ignoreSslErrors.value = ignore
    }

    override fun onCleared() {
        super.onCleared()
        proxyServer?.stop()
    }
}

class HostsBrowserViewModelFactory(
    private val repository: HostRuleRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HostsBrowserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HostsBrowserViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
