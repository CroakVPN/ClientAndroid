package com.croakvpn.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.croakvpn.ui.theme.CroakVpnTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // VPN permission launcher
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted(this)
        }
    }

    // Notification permission launcher (Android 13+)
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        viewModel.onRequestVpnPermission = { intent ->
            vpnPermissionLauncher.launch(intent)
        }

        // Handle deep link
        intent?.data?.toString()?.let { uri ->
            if (uri.startsWith("croakvpn://")) {
                viewModel.handleDeepLink(uri)
            }
        }

        setContent {
            CroakVpnTheme {
                CroakVpnApp(viewModel = viewModel, activity = this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.bindToService(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindFromService(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { uri ->
            if (uri.startsWith("croakvpn://")) {
                viewModel.handleDeepLink(uri)
            }
        }
    }
}
