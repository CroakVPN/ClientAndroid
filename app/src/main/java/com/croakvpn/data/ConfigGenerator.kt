package com.croakvpn.data

import com.croakvpn.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates sing-box JSON config from parsed VLESS server list.
 * Android version: uses TUN inbound (Android VPN tun fd is passed via -D flag).
 * Direct port of ConfigGenerator.cs with Android-specific adjustments.
 */
object ConfigGenerator {

    fun generate(configs: List<ServerConfig>): String {
        val outbounds = JSONArray()
        val proxyTags = JSONArray()
        val serverAddresses = mutableSetOf<String>()

        for ((i, config) in configs.withIndex()) {
            val tag = if (i == 0 && configs.size == 1) "proxy" else "proxy-$i"
            if (config.address.isNotEmpty()) serverAddresses.add(config.address)

            val outbound = JSONObject().apply {
                put("type", config.protocol)
                put("tag", tag)
                put("server", config.address)
                put("server_port", config.port)
                put("uuid", config.uuid)
            }

            if (!config.flow.isNullOrEmpty()) outbound.put("flow", config.flow)

            if (config.security == "reality" || config.security == "tls") {
                val tls = JSONObject().apply { put("enabled", true) }

                if (!config.sni.isNullOrEmpty()) tls.put("server_name", config.sni)

                if (!config.fingerprint.isNullOrEmpty()) {
                    tls.put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", config.fingerprint)
                    })
                }

                if (config.security == "reality") {
                    tls.put("reality", JSONObject().apply {
                        put("enabled", true)
                        if (!config.publicKey.isNullOrEmpty()) put("public_key", config.publicKey)
                        if (!config.shortId.isNullOrEmpty()) put("short_id", config.shortId)
                    })
                }

                outbound.put("tls", tls)
            }

            if (!config.network.isNullOrEmpty() && config.network != "tcp") {
                outbound.put("transport", JSONObject().apply { put("type", config.network) })
            }

            outbounds.put(outbound)
            proxyTags.put(tag)
        }

        // urltest group for multi-server auto selection
        if (configs.size > 1) {
            outbounds.put(JSONObject().apply {
                put("type", "urltest")
                put("tag", "proxy")
                put("outbounds", proxyTags)
                put("url", "https://www.gstatic.com/generate_204")
                put("interval", "1m")
                put("tolerance", 50)
            })
        } else if (configs.size == 1) {
            // Rename single proxy to "proxy"
            (outbounds.get(0) as? JSONObject)?.put("tag", "proxy")
        }

        outbounds.put(JSONObject().apply { put("type", "direct"); put("tag", "direct") })

        // Route rules
        val routeRules = JSONArray().apply {
            put(JSONObject().apply { put("action", "sniff") })
            put(JSONObject().apply { put("protocol", "dns"); put("action", "hijack-dns") })
        }

        // Critical: route VPN server traffic direct to avoid loop
        if (serverAddresses.isNotEmpty()) {
            val serverIps = JSONArray().apply { serverAddresses.forEach { put(it) } }
            routeRules.put(JSONObject().apply {
                put("ip_cidr", serverIps)
                put("outbound", "direct")
            })
        }

        // Private IPs direct
        routeRules.put(JSONObject().apply {
            put("ip_is_private", true)
            put("outbound", "direct")
        })

        val root = JSONObject().apply {
            put("log", JSONObject().apply {
                put("level", "warn")
                put("timestamp", true)
            })
            put("experimental", JSONObject().apply {
                put("clash_api", JSONObject().apply {
                    put("external_controller", "127.0.0.1:9090")
                    put("secret", "")
                })
            })
            put("dns", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("tag", "remote")
                        put("type", "tls")
                        put("server", "8.8.8.8")
                        put("detour", "proxy")
                    })
                    put(JSONObject().apply {
                        put("tag", "local")
                        put("type", "udp")
                        put("server", "1.1.1.1")
                    })
                })
                put("rules", JSONArray().apply {
                    put(JSONObject().apply {
                        put("ip_is_private", true)
                        put("server", "local")
                    })
                })
                put("final", "remote")
                put("strategy", "ipv4_only")
            })
            // Android: sing-box uses "tun" inbound with auto_route
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("address", JSONArray().apply {
                        put("172.19.0.1/30")
                        put("fdfe:dcba:9876::1/126")
                    })
                    put("auto_route", true)
                    put("strict_route", false)
                    put("stack", "system")
                    // Android platform hint
                    put("platform", JSONObject().apply {
                        put("http_proxy", JSONObject().apply {
                            put("enabled", false)
                        })
                    })
                })
            })
            put("outbounds", outbounds)
            put("route", JSONObject().apply {
                put("default_domain_resolver", "local")
                put("rules", routeRules)
                put("auto_detect_interface", true)
                put("final", "proxy")
            })
        }

        return root.toString(2)
    }
}
