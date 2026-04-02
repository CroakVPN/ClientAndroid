package com.croakvpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.croakvpn.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PrefsManager(context)
        CoroutineScope(Dispatchers.IO).launch {
            val autoConnect = prefs.autoConnectFlow.first()
            val hasConfig   = prefs.hasSingboxConfig()

            if (autoConnect && hasConfig) {
                val svcIntent = Intent(context, CroakVpnService::class.java).apply {
                    action = CroakVpnService.ACTION_CONNECT
                }
                context.startForegroundService(svcIntent)
            }
        }
    }
}
