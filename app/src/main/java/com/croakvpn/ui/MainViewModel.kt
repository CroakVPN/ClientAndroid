package com.croakvpn.ui

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.croakvpn.data.ConfigGenerator
import com.croakvpn.data.PrefsManager
import com.croakvpn.data.SubscriptionRepo
import com.croakvpn.model.ConnectionState
import com.croakvpn.model.ConnectionStatus
import com.croakvpn.service.CroakVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsManager(app)
    private val subRepo = SubscriptionRepo()

    // ── Bound service ──
    private var vpnService: CroakVpnService? = null
    private var serviceConnection: ServiceConnection? = null

    // ── UI State ──
    private val _status = MutableStateFlow(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("0 B/s")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _uploadSpeed = MutableStateFlow("0 B/s")
    val uploadSpeed: StateFlow<String> = _uploadSpeed.asStateFlow()

    private val _duration = MutableStateFlow("00:00:00")
    val duration: StateFlow<String> = _duration.asStateFlow()

    private val _hasSubscription = MutableStateFlow(false)
    val hasSubscription: StateFlow<Boolean> = _hasSubscription.asStateFlow()

    private val _serverName = MutableStateFlow("")
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    private val _importUrl = MutableStateFlow("")
    val importUrl: StateFlow<String> = _importUrl.asStateFlow()

    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog: StateFlow<Boolean> = _showImportDialog.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _toastIsError = MutableStateFlow(false)
    val toastIsError: StateFlow<Boolean> = _toastIsError.asStateFlow()

    private val _autoConnect = MutableStateFlow(false)
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

    // VPN permission request callback
    var onRequestVpnPermission: ((Intent) -> Unit)? = null

    private var connectTime: Long? = null
    private var durationJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.subscriptionUrlFlow.collect { url ->
                _hasSubscription.value = url != null
                if (url != null) _serverName.value = url
            }
        }
        viewModelScope.launch {
            prefs.autoConnectFlow.collect { _autoConnect.value = it }
        }
    }

    // ── Service binding ──

    fun bindToService(context: Context) {
        val intent = Intent(context, CroakVpnService::class.java)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                vpnService = (binder as? CroakVpnService.LocalBinder)?.getService()
                vpnService?.let { svc ->
                    viewModelScope.launch {
                        svc.status.collect { st ->
                            _status.value = st
                            when (st.state) {
                                ConnectionState.Connected -> {
                                    if (connectTime == null) {
                                        connectTime = System.currentTimeMillis()
                                        startDurationTimer()
                                    }
                                }
                                ConnectionState.Disconnected, ConnectionState.Error -> {
                                    connectTime = null
                                    durationJob?.cancel()
                                    _duration.value = "00:00:00"
                                    _downloadSpeed.value = "0 B/s"
                                    _uploadSpeed.value = "0 B/s"
                                }
                                else -> {}
                            }
                        }
                    }
                    viewModelScope.launch {
                        svc.downloadSpeed.collect { _downloadSpeed.value = it }
                    }
                    viewModelScope.launch {
                        svc.uploadSpeed.collect { _uploadSpeed.value = it }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                vpnService = null
            }
        }
        serviceConnection = conn
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun unbindFromService(context: Context) {
        serviceConnection?.let { context.unbindService(it) }
        serviceConnection = null
    }

    // ── VPN toggle ──

    fun toggleVpn(activity: Activity) {
        when (_status.value.state) {
            ConnectionState.Connected -> disconnectVpn(activity)
            ConnectionState.Disconnected, ConnectionState.Error -> connectVpn(activity)
            else -> { /* ignore mid-transition */ }
        }
    }

    private fun connectVpn(activity: Activity) {
        if (!_hasSubscription.value) {
            _showImportDialog.value = true
            return
        }

        // Check VPN permission
        val permIntent = VpnService.prepare(activity)
        if (permIntent != null) {
            onRequestVpnPermission?.invoke(permIntent)
            return
        }

        doConnect(activity)
    }

    fun onVpnPermissionGranted(context: Context) {
        doConnect(context)
    }

    private fun doConnect(context: Context) {
        _status.value = ConnectionStatus.Connecting
        val intent = Intent(context, CroakVpnService::class.java).apply {
            action = CroakVpnService.ACTION_CONNECT
        }
        context.startForegroundService(intent)
    }

    private fun disconnectVpn(context: Context) {
        _status.value = ConnectionStatus.Disconnecting
        val intent = Intent(context, CroakVpnService::class.java).apply {
            action = CroakVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    // ── Import subscription ──

    fun setImportUrl(url: String) { _importUrl.value = url }
    fun openImportDialog() { _showImportDialog.value = true }
    fun closeImportDialog() { _showImportDialog.value = false; _importUrl.value = "" }

    fun importSubscription(context: Context) {
        val url = _importUrl.value.trim()
        if (url.isEmpty()) return

        viewModelScope.launch {
            _status.value = ConnectionStatus.Connecting
            try {
                val configs = subRepo.fetchAndParse(url)
                if (configs.isEmpty()) {
                    _status.value = ConnectionStatus.withError("Конфигурация не найдена")
                    return@launch
                }

                val singboxConfig = ConfigGenerator.generate(configs)
                prefs.saveSubscription(url, singboxConfig)

                _serverName.value = configs[0].serverName ?: configs[0].address
                _hasSubscription.value = true
                _importUrl.value = ""
                _showImportDialog.value = false
                showToast("Подписка добавлена, подключаем...")

                // Auto-connect after import
                doConnect(context)
            } catch (e: Exception) {
                _status.value = ConnectionStatus.withError("Ошибка импорта: ${e.message}")
            }
        }
    }

    // ── Refresh subscription ──

    fun refreshSubscription(context: Context) {
        viewModelScope.launch {
            val url = prefs.getSubscriptionUrl() ?: return@launch
            val wasConnected = _status.value.state == ConnectionState.Connected

            if (wasConnected) {
                disconnectVpn(context)
                delay(2000)
            }

            _importUrl.value = url
            importSubscription(context)
        }
    }

    // ── Delete subscription ──

    fun deleteSubscription(context: Context) {
        viewModelScope.launch {
            if (_status.value.state == ConnectionState.Connected) {
                disconnectVpn(context)
                delay(2000)
            }
            prefs.clearSubscription()
            _hasSubscription.value = false
            _serverName.value = ""
            _status.value = ConnectionStatus.Disconnected
            showToast("Подписка удалена")
        }
    }

    // ── Auto-connect toggle ──

    fun toggleAutoConnect() {
        viewModelScope.launch {
            prefs.setAutoConnect(!_autoConnect.value)
        }
    }

    // ── Deep link ──

    fun handleDeepLink(uri: String) {
        // croakvpn://<subscription-url>
        val subUrl = uri.removePrefix("croakvpn://")
        if (subUrl.isNotEmpty()) {
            _importUrl.value = subUrl
            _showImportDialog.value = true
        }
    }

    // ── Duration timer ──

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (isActive) {
                val start = connectTime ?: break
                val elapsed = System.currentTimeMillis() - start
                val h = elapsed / 3_600_000
                val m = (elapsed % 3_600_000) / 60_000
                val s = (elapsed % 60_000) / 1_000
                _duration.value = "%02d:%02d:%02d".format(h, m, s)
                delay(1000)
            }
        }
    }

    // ── Toast ──

    private fun showToast(msg: String, isError: Boolean = false) {
        viewModelScope.launch {
            _toastIsError.value = isError
            _toastMessage.value = msg
            delay(3000)
            _toastMessage.value = null
        }
    }

    override fun onCleared() {
        durationJob?.cancel()
        super.onCleared()
    }
}
