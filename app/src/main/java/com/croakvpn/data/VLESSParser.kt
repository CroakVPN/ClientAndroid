package com.croakvpn.data

import com.croakvpn.model.ServerConfig
import java.net.URLDecoder

/**
 * Parses vless://uuid@host:port?params#fragment URIs
 * Direct port of VLESSParser.cs
 */
object VLESSParser {

    fun parse(uri: String): ServerConfig? {
        if (!uri.startsWith("vless://", ignoreCase = true)) return null

        return try {
            val stripped = uri.removePrefix("vless://")

            // Split fragment
            val fragmentIdx = stripped.indexOf('#')
            val mainPart = if (fragmentIdx >= 0) stripped.substring(0, fragmentIdx) else stripped
            val fragment = if (fragmentIdx >= 0)
                URLDecoder.decode(stripped.substring(fragmentIdx + 1), "UTF-8")
            else null

            // Split query
            val queryIdx = mainPart.indexOf('?')
            val authHost = if (queryIdx >= 0) mainPart.substring(0, queryIdx) else mainPart
            val queryString = if (queryIdx >= 0) mainPart.substring(queryIdx + 1) else ""

            // Parse uuid@host:port
            val atIdx = authHost.indexOf('@')
            if (atIdx < 0) return null

            val uuid = authHost.substring(0, atIdx)
            val hostPort = authHost.substring(atIdx + 1)

            val (address, port) = parseHostPort(hostPort)
            val parameters = parseQueryParams(queryString)

            ServerConfig(
                protocol    = "vless",
                uuid        = uuid,
                address     = address,
                port        = port,
                flow        = parameters["flow"],
                security    = parameters["security"],
                sni         = parameters["sni"],
                fingerprint = parameters["fp"],
                publicKey   = parameters["pbk"],
                shortId     = parameters["sid"],
                serverName  = fragment,
                network     = parameters.getOrDefault("type", "tcp")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHostPort(hostPort: String): Pair<String, Int> {
        return if (hostPort.startsWith('[')) {
            // IPv6: [::1]:port
            val closeBracket = hostPort.indexOf(']')
            val address = hostPort.substring(1, closeBracket)
            val rest = hostPort.substring(closeBracket + 1)
            val port = if (rest.startsWith(':')) rest.substring(1).toIntOrNull() ?: 443 else 443
            Pair(address, port)
        } else {
            val parts = hostPort.split(':', limit = 2)
            val addr = parts[0]
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 443 else 443
            Pair(addr, port)
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val kv = pair.split('=', limit = 2)
            if (kv.size == 2) {
                URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8")
            } else null
        }.toMap()
    }
}
