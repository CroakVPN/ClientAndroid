package com.croakvpn.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.croakvpn.model.ConnectionState

// ═══════════════════════════════════════════════════════
// Root
// ═══════════════════════════════════════════════════════

@Composable
fun CroakVpnApp(viewModel: MainViewModel, activity: Activity) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                activity = activity,
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                activity = activity,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, activity: Activity, onOpenSettings: () -> Unit) {
    val status       by viewModel.status.collectAsState()
    val downloadSpeed by viewModel.downloadSpeed.collectAsState()
    val uploadSpeed  by viewModel.uploadSpeed.collectAsState()
    val duration     by viewModel.duration.collectAsState()
    val showImport   by viewModel.showImportDialog.collectAsState()
    val toastMsg     by viewModel.toastMessage.collectAsState()
    val toastIsError by viewModel.toastIsError.collectAsState()
    val serverName   by viewModel.serverName.collectAsState()

    val bgGradient = when (status.state) {
        ConnectionState.Connected -> Brush.verticalGradient(
            listOf(Color(0xFF0A1628), Color(0xFF0D2137), Color(0xFF0A3320))
        )
        ConnectionState.Connecting, ConnectionState.Disconnecting -> Brush.verticalGradient(
            listOf(Color(0xFF1A1200), Color(0xFF0A0A0A))
        )
        ConnectionState.Error -> Brush.verticalGradient(
            listOf(Color(0xFF1A0808), Color(0xFF0A0A0A))
        )
        else -> Brush.verticalGradient(
            listOf(Color(0xFF0A0A0A), Color(0xFF141414), Color(0xFF0A0A0A))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Frog logo
                    Text("🐸", fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CroakVPN",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Server name
            if (serverName.isNotEmpty()) {
                Text(
                    text = serverName,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(24.dp))
            }

            // Big connect button
            ConnectButton(
                state = status.state,
                onClick = { viewModel.toggleVpn(activity) }
            )

            Spacer(Modifier.height(24.dp))

            // Status text
            StatusBadge(status = status)

            Spacer(Modifier.height(40.dp))

            // Stats card (only when connected)
            AnimatedVisibility(
                visible = status.state == ConnectionState.Connected,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                StatsCard(
                    duration = duration,
                    downloadSpeed = downloadSpeed,
                    uploadSpeed = uploadSpeed
                )
            }

            Spacer(Modifier.weight(1f))

            // Add subscription button (when no subscription)
            if (status.state == ConnectionState.Disconnected) {
                TextButton(
                    onClick = { viewModel.openImportDialog() },
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Добавить подписку",
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp
                    )
                }
            } else {
                Spacer(Modifier.height(32.dp))
            }
        }

        // Import dialog overlay
        if (showImport) {
            ImportDialog(
                viewModel = viewModel,
                activity = activity,
                onDismiss = { viewModel.closeImportDialog() }
            )
        }

        // Toast
        toastMsg?.let { msg ->
            Toast(
                message = msg,
                isError = toastIsError,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Connect Button
// ═══════════════════════════════════════════════════════

@Composable
fun ConnectButton(state: ConnectionState, onClick: () -> Unit) {
    val isTransitioning = state == ConnectionState.Connecting || state == ConnectionState.Disconnecting

    val buttonColor = when (state) {
        ConnectionState.Connected    -> Color(0xFF4CAF50)
        ConnectionState.Error        -> Color(0xFFEF4444)
        ConnectionState.Connecting, ConnectionState.Disconnecting -> Color(0xFFFFC107)
        else                         -> Color(0xFF374151)
    }

    val ringColor = buttonColor.copy(alpha = 0.25f)

    val pulsing by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isTransitioning) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(pulsing)
                .clip(CircleShape)
                .background(ringColor)
        )
        // Inner button
        Button(
            onClick = onClick,
            enabled = !isTransitioning,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                disabledContainerColor = buttonColor.copy(alpha = 0.7f)
            ),
            modifier = Modifier.size(160.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isTransitioning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = when (state) {
                            ConnectionState.Connected -> Icons.Default.Lock
                            ConnectionState.Error     -> Icons.Default.Close
                            else                      -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Status Badge
// ═══════════════════════════════════════════════════════

@Composable
fun StatusBadge(status: com.croakvpn.model.ConnectionStatus) {
    val color = when (status.state) {
        ConnectionState.Connected    -> Color(0xFF4CAF50)
        ConnectionState.Connecting, ConnectionState.Disconnecting -> Color(0xFFFFC107)
        ConnectionState.Error        -> Color(0xFFEF4444)
        else                         -> Color(0xFF6B7280)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = status.displayText,
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Stats Card
// ═══════════════════════════════════════════════════════

@Composable
fun StatsCard(duration: String, downloadSpeed: String, uploadSpeed: String) {
    Surface(
        color = Color(0xFF1A2332),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "Время", value = duration, icon = "⏱")
            Divider(
                color = Color(0xFF2D3748),
                modifier = Modifier.fillMaxHeight().width(1.dp)
            )
            StatItem(label = "↓ Скорость", value = downloadSpeed, icon = "⬇")
            Divider(
                color = Color(0xFF2D3748),
                modifier = Modifier.fillMaxHeight().width(1.dp)
            )
            StatItem(label = "↑ Скорость", value = uploadSpeed, icon = "⬆")
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            color = Color(0xFF6B7280),
            fontSize = 11.sp
        )
    }
}

// ═══════════════════════════════════════════════════════
// Import Dialog
// ═══════════════════════════════════════════════════════

@Composable
fun ImportDialog(viewModel: MainViewModel, activity: Activity, onDismiss: () -> Unit) {
    val importUrl by viewModel.importUrl.collectAsState()
    val status    by viewModel.status.collectAsState()
    val isLoading = status.state == ConnectionState.Connecting

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1A1F2E),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Добавить подписку",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Вставьте ссылку на подписку (VLESS)",
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = importUrl,
                    onValueChange = { viewModel.setImportUrl(it) },
                    placeholder = { Text("https://...", color = Color(0xFF4B5563)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF4CAF50)
                    ),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9CA3AF)),
                        border = BorderStroke(1.dp, Color(0xFF374151))
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = { viewModel.importSubscription(activity) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = importUrl.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Добавить", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Settings Screen
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, activity: Activity, onBack: () -> Unit) {
    val hasSubscription by viewModel.hasSubscription.collectAsState()
    val serverName      by viewModel.serverName.collectAsState()
    val autoConnect     by viewModel.autoConnect.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Subscription section
            SettingsSection(title = "Подписка") {
                if (hasSubscription) {
                    SettingsItem(
                        icon = { Icon(Icons.Default.Link, null, tint = Color(0xFF4CAF50)) },
                        title = "Активная подписка",
                        subtitle = serverName.take(60)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.refreshSubscription(activity) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50)),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Обновить")
                        }
                        OutlinedButton(
                            onClick = { viewModel.deleteSubscription(activity) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Удалить")
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.openImportDialog(); onBack() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить подписку")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Behavior section
            SettingsSection(title = "Поведение") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Автоподключение", color = Color.White, fontSize = 15.sp)
                        Text(
                            "Подключаться при запуске устройства",
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { viewModel.toggleAutoConnect() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50)
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // About section
            SettingsSection(title = "О приложении") {
                SettingsItem(
                    icon = { Text("🐸", fontSize = 20.sp) },
                    title = "CroakVPN",
                    subtitle = "Версия 1.0 • VLESS / Reality"
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            color = Color(0xFF4CAF50),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            color = Color(0xFF111827),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SettingsItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color(0xFF6B7280), fontSize = 12.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Toast
// ═══════════════════════════════════════════════════════

@Composable
fun Toast(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    val color = if (isError) Color(0xFFEF4444) else Color(0xFF4CAF50)
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = message,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
