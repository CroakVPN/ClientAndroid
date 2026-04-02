package com.croakvpn

import android.app.Application
import android.util.Log

class CroakApp : Application() {

    companion object {
        private const val TAG = "CroakVPN"

        fun log(message: String) {
            Log.d(TAG, message)
        }
    }

    override fun onCreate() {
        super.onCreate()
        log("=== CroakVPN started ===")
        extractSingBox()
    }

    /**
     * Extract sing-box binary from assets to filesDir on first run.
     * The binary must be placed in app/src/main/assets/libs/sing-box-arm64
     * (and optionally sing-box-arm, sing-box-x86_64 for other ABIs).
     *
     * Download the Android arm64 build from:
     * https://github.com/SagerNet/sing-box/releases
     */
    private fun extractSingBox() {
        val targetDir = java.io.File(filesDir, "libs").also { it.mkdirs() }
        val targetFile = java.io.File(targetDir, "sing-box")

        // Pick correct ABI binary
        val abiAsset = when {
            android.os.Build.SUPPORTED_ABIS.any { it.contains("arm64") } -> "libs/sing-box-arm64"
            android.os.Build.SUPPORTED_ABIS.any { it.contains("x86_64") } -> "libs/sing-box-x86_64"
            else -> "libs/sing-box-arm"
        }

        try {
            // Only extract if not already present or assets changed
            val assetMeta = java.io.File(targetDir, ".asset_name")
            if (targetFile.exists() && assetMeta.exists() && assetMeta.readText() == abiAsset) {
                log("sing-box already extracted: ${targetFile.absolutePath}")
                return
            }

            assets.open(abiAsset).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setExecutable(true, false)
            assetMeta.writeText(abiAsset)
            log("sing-box extracted to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            // Asset not present — user must place sing-box manually or download
            log("sing-box extraction failed (asset not found): ${e.message}")
            log("Place sing-box binary at: ${targetFile.absolutePath}")
        }
    }
}
