package com.croakvpn.data

import android.util.Base64
import com.croakvpn.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches Marzban subscription URL and parses VLESS configs.
 * Marzban returns base64-encoded list of vless:// URIs.
 */
class SubscriptionRepo {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "CroakVPN/1.0")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun fetchAndParse(urlString: String): List<ServerConfig> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(urlString).build()
        val body = http.newCall(request).execute().use { resp ->
            resp.body?.string() ?: throw Exception("Empty response")
        }

        // Marzban returns base64-encoded content
        val decoded: String = try {
            val bytes = Base64.decode(body.trim(), Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            body // already plain text
        }

        decoded
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { VLESSParser.parse(it) }
    }
}
