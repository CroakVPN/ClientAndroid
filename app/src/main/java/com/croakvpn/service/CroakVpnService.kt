package com.croakvpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.croakvpn.R
import com.croakvpn.data.PrefsManager
import com.croakvpn.model.ConnectionState
import com.croakvpn.model.ConnectionStatus
import com.croakvpn.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.util.concurrent.TimeUnit

class CroakVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT    = "com.croakvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.croakvpn.DISCONNECT"
        const val CHANNEL_ID        = "croak_vpn_channel"
        const val NOTIF_ID          = 1001
        private const val TAG       = "CroakVpnService"
    }

    // ── State ──
    private val _status = MutableStateFlow(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("0 B/s")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _uploadSpeed = MutableStateFlow("0 B/s")
    val uploadSpeed: StateFlow<String> = _uploadSpeed.asStateFlow()

    private val _logLines = MutableStateFlow(listOf<String>())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    // ── Internals ──
    private var singBoxProcess: Process? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trafficWs: WebSocket? = null
    private var trafficJob: Job? = null

    private val prefs by lazy { PrefsManager(applicationContext) }

    inner class LocalBinder : Binder() {
        fun getService() = this@CroakVpnService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("CroakVPN запускается..."))

        when (intent?.action) {
            ACTION_CONNECT    -> scope.launch { startVpn() }
            ACTION_DISCONNECT -> scope.launch { stopVpn() }
        }
        return START_STICKY
    }

    // ── Start sing-box ──

    suspend fun startVpn() {
        if (_status.value.state == ConnectionState.Connected) return
        _status.emit(ConnectionStatus.Connecting)
        updateNotification("Подключение...")

        val configPath = prefs.getSingboxConfigPath()
        if (!File(configPath).exists()) {
            _status.emit(ConnectionStatus.withError("Конфигурация не найдена"))
            return
        }

        val singBoxBin = findSingBox()
        if (singBoxBin == null) {
            _status.emit(ConnectionStatus.withError("sing-box не найден в assets"))
            return
        }

        // Setup the VPN interface via Android VpnService builder
        // sing-box will use auto_route; Android VPN fd is exposed via the TUN device
        val builder = Builder()
            .setSession("CroakVPN")
            .addAddress("172.19.0.1", 30)
            .addAddress("fdfe:dcba:9876::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .setBlocking(false)

        // Allow bypass for the sing-box process itself
        val vpnInterface = builder.establish()
        if (vpnInterface == null) {
            _status.emit(ConnectionStatus.withError("Не удалось создать VPN интерфейс"))
            return
        }
        vpnInterface.close() // sing-box manages its own TUN

        for (attempt in 1..3) {
            log("[SB] Start attempt $attempt/3")
            val result = tryStartOnce(singBoxBin, configPath)
            when (result) {
                StartResult.Success -> return
                StartResult.FatalNonRetriable -> return
                StartResult.Retriable -> {
                    if (attempt < 3) {
                        killAllSingBox()
                        delay(3000)
                    } else {
                        _status.emit(ConnectionStatus.withError("Не удалось запустить VPN. Попробуйте снова."))
                    }
                }
            }
        }
    }

    private enum class StartResult { Success, FatalNonRetriable, Retriable }

    private suspend fun tryStartOnce(singBoxBin: String, configPath: String): StartResult {
        return try {
            killAllSingBox()
            delay(500)

            val workDir = File(filesDir, "working").also { it.mkdirs() }

            val process = ProcessBuilder(singBoxBin, "run", "-c", configPath, "-D", workDir.absolutePath)
                .redirectErrorStream(false)
                .start()

            singBoxProcess = process
            var fatalDetected = false

            // Read stdout/stderr
            scope.launch {
                process.inputStream.bufferedReader().forEachLine { line ->
                    log("[SB] $line")
                    if (line.contains("FATAL", ignoreCase = true)) fatalDetected = true
                }
            }
            scope.launch {
                process.errorStream.bufferedReader().forEachLine { line ->
                    log("[SB-ERR] $line")
                    if (line.contains("FATAL", ignoreCase = true)) fatalDetected = true
                }
            }

            // Watch for unexpected exit
            scope.launch {
                val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
                log("[SB] Process exited with code $exitCode")
                if (_status.value.state == ConnectionState.Connected) {
                    _status.emit(ConnectionStatus.withError("VPN остановился (код $exitCode)"))
                    stopTraffic()
                }
            }

            // Poll Clash API (port 9090) until ready
            val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).build()
            for (i in 1..15) {
                delay(1000)
                if (!process.isAlive || fatalDetected) {
                    log("[SB] Process died or FATAL detected")
                    return StartResult.Retriable
                }
                try {
                    val resp = http.newCall(Request.Builder().url("http://127.0.0.1:9090/version").build()).execute()
                    if (resp.code < 500) {
                        log("[SB] Clash API ready after ${i}s (HTTP ${resp.code})")
                        resp.close()
                        _status.emit(ConnectionStatus.Connected)
                        updateNotification("Защищено ✓")
                        startTrafficLoop()
                        return StartResult.Success
                    }
                    resp.close()
                } catch (e: Exception) {
                    // Not ready yet
                }
            }

            // Timeout but still alive — assume started
            if (process.isAlive) {
                log("[SB] Timeout, process alive — assuming started")
                _status.emit(ConnectionStatus.Connected)
                updateNotification("Защищено ✓")
                startTrafficLoop()
                return StartResult.Success
            }

            StartResult.Retriable
        } catch (e: Exception) {
            log("[SB] Exception: ${e.message}")
            _status.emit(ConnectionStatus.withError("Ошибка запуска: ${e.message}"))
            StartResult.FatalNonRetriable
        }
    }

    // ── Stop sing-box ──

    suspend fun stopVpn() {
        _status.emit(ConnectionStatus.Disconnecting)
        updateNotification("Отключение...")
        stopTraffic()

        singBoxProcess?.let { p ->
            try {
                p.destroy()
                withContext(Dispatchers.IO) { p.waitFor() }
            } catch (e: Exception) { /* ignore */ }
            singBoxProcess = null
        }

        killAllSingBox()
        delay(1000)
        _status.emit(ConnectionStatus.Disconnected)
        _downloadSpeed.emit("0 B/s")
        _uploadSpeed.emit("0 B/s")
        updateNotification("Не подключено")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Traffic WebSocket ──

    private fun startTrafficLoop() {
        stopTraffic()
        val http = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val req = Request.Builder().url("ws://127.0.0.1:9090/traffic").build()
        trafficWs = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = org.json.JSONObject(text)
                    val down = obj.getLong("down")
                    val up   = obj.getLong("up")
                    scope.launch {
                        _downloadSpeed.emit(formatSpeed(down))
                        _uploadSpeed.emit(formatSpeed(up))
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                log("[Traffic] WS error: ${t.message}")
            }
        })
    }

    private fun stopTraffic() {
        trafficWs?.close(1000, null)
        trafficWs = null
        trafficJob?.cancel()
        trafficJob = null
    }

    // ── Helpers ──

    private fun findSingBox(): String? {
        // sing-box binary should be extracted to filesDir/libs/sing-box on first run
        val candidates = listOf(
            File(filesDir, "libs/sing-box"),
            File(filesDir, "sing-box"),
            File(applicationInfo.nativeLibraryDir, "libsingbox.so"),
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

    private fun killAllSingBox() {
        try {
            Runtime.getRuntime().exec("killall sing-box").waitFor()
        } catch (e: Exception) { /* ignore */ }
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        val current = _logLines.value.toMutableList()
        current.add(msg)
        if (current.size > 500) current.removeAt(0) // Keep last 500 lines
        scope.launch { _logLines.emit(current) }
    }

    private fun formatSpeed(bytes: Long): String {
        if (bytes <= 0) return "0 B/s"
        if (bytes < 1024) return "$bytes B/s"
        if (bytes < 1024 * 1024) return "${"%.1f".format(bytes / 1024.0)} KB/s"
        return "${"%.1f".format(bytes / (1024.0 * 1024))} MB/s"
    }

    // ── Notification ──

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CroakVPN Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VPN connection status"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CroakVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CroakVPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn_shield)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Отключить", disconnectIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        stopTraffic()
        singBoxProcess?.destroy()
        super.onDestroy()
    }
}
