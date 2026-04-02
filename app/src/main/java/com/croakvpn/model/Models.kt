package com.croakvpn.model

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════
// Connection Status
// ═══════════════════════════════════════════

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Error
}

data class ConnectionStatus(
    val state: ConnectionState,
    val errorMessage: String? = null
) {
    val displayText: String get() = when (state) {
        ConnectionState.Connected    -> "Защищено"
        ConnectionState.Connecting   -> "Подключение..."
        ConnectionState.Disconnecting -> "Отключение..."
        ConnectionState.Disconnected -> "Не подключено"
        ConnectionState.Error        -> "Ошибка: $errorMessage"
    }

    companion object {
        val Disconnected  = ConnectionStatus(ConnectionState.Disconnected)
        val Connecting    = ConnectionStatus(ConnectionState.Connecting)
        val Connected     = ConnectionStatus(ConnectionState.Connected)
        val Disconnecting = ConnectionStatus(ConnectionState.Disconnecting)
        fun withError(msg: String) = ConnectionStatus(ConnectionState.Error, msg)
    }
}

// ═══════════════════════════════════════════
// Traffic Stats
// ═══════════════════════════════════════════

data class TrafficStats(
    val downloadSpeed: String = "0 B/s",
    val uploadSpeed: String   = "0 B/s",
    val totalDownload: Long   = 0L,
    val totalUpload: Long     = 0L
)

// ═══════════════════════════════════════════
// Server Config (parsed from vless:// URI)
// ═══════════════════════════════════════════

data class ServerConfig(
    val protocol: String     = "vless",
    val uuid: String         = "",
    val address: String      = "",
    val port: Int            = 443,
    val flow: String?        = null,
    val security: String?    = null,
    val sni: String?         = null,
    val fingerprint: String? = null,
    val publicKey: String?   = null,
    val shortId: String?     = null,
    val serverName: String?  = null,
    val network: String?     = "tcp"
)

// ═══════════════════════════════════════════
// Update Info
// ═══════════════════════════════════════════

data class UpdateInfo(
    val version: String      = "0.0",
    val downloadUrl: String  = "",
    val releaseNotes: String = "",
    val fileSize: Long       = 0L,
    val tagName: String      = ""
)

// ═══════════════════════════════════════════
// VPN Preferences (persisted via DataStore)
// ═══════════════════════════════════════════

@Serializable
data class VpnPrefs(
    val subscriptionUrl: String = "",
    val autoConnect: Boolean    = false
)
