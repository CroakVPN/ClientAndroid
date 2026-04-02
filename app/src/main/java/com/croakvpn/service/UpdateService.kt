package com.croakvpn.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.croakvpn.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks for updates via GitHub Releases API.
 * Mirrors UpdateService.cs logic for Android.
 */
object UpdateService {

    private const val GITHUB_OWNER = "CroakVPN"
    private const val GITHUB_REPO  = "ClientAndroid"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "CroakVPN-Android/1.0")
                    .build()
            )
        }
        .build()

    // Current version — read from BuildConfig in real project
    private const val CURRENT_VERSION = "1.0"

    /**
     * Check GitHub for a newer release. Returns UpdateInfo or null if up to date.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(API_URL).build()
            val response = http.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val tagName = json.getString("tag_name").trimStart('v', 'V')
            val remoteVersion = tagName.normalizeVersion()

            if (!isNewerVersion(remoteVersion, CURRENT_VERSION.normalizeVersion())) {
                return@withContext null
            }

            // Find APK asset
            var downloadUrl: String? = null
            var fileSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.getString("browser_download_url")
                        fileSize = asset.getLong("size")
                        break
                    }
                }
            }

            if (downloadUrl == null) return@withContext null

            UpdateInfo(
                version      = remoteVersion,
                downloadUrl  = downloadUrl,
                releaseNotes = json.optString("body", ""),
                fileSize     = fileSize,
                tagName      = tagName
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download APK via DownloadManager and trigger install.
     */
    fun downloadAndInstall(context: Context, update: UpdateInfo) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl)).apply {
            setTitle("CroakVPN ${update.version}")
            setDescription("Загрузка обновления...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "CroakVPN-${update.version}.apk"
            )
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = dm.enqueue(request)

        // Listen for completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor.moveToFirst()) {
                        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (cursor.getInt(statusCol) == DownloadManager.STATUS_SUCCESSFUL) {
                            val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val localUri = cursor.getString(uriCol)
                            installApk(ctx, localUri)
                        }
                    }
                    cursor.close()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, localUri: String) {
        try {
            val file = File(Uri.parse(localUri).path ?: return)
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun String.normalizeVersion(): String {
        val parts = split('.')
        return when (parts.size) {
            1 -> "$this.0"
            else -> this
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }
}
