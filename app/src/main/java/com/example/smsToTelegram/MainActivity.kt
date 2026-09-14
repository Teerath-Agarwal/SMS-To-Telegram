package com.example.smsToTelegram

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smsToTelegram.ui.theme.SMSRelayTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)
        setContent {
            SMSRelayTheme {
                RelayScreen()
            }
        }
    }
}

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "relay_channel",
            "SMS Relay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of SMS relaying"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

@Composable
fun RelayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { RelaySettings(context) }

    val fwdNumber by settings.forwardingNumber.collectAsState(initial = "...")
    val tgToken by settings.telegramToken.collectAsState(initial = "")
    val userName by settings.userName.collectAsState(initial = "")
    val regexList by settings.senderRegexList.collectAsState(initial = emptySet())

    var newRegex by remember { mutableStateOf("") }
    var editedUserName by remember { mutableStateOf("") }

    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var isIgnoringBattery by remember { 
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    var arePermissionsGranted by remember { 
        mutableStateOf(checkCorePermissions(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                arePermissionsGranted = checkCorePermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        settings.ensureSeeded()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("SMS Relay Dashboard") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ReliabilityBanner(
                isVisible = !arePermissionsGranted,
                title = "Allow Permissions",
                description = "Please enable 'Allow restricted settings' for this app. Then enable 'SMS' under Permissions.",
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )

            ReliabilityBanner(
                isVisible = !isIgnoringBattery,
                title = "Improve Reliability",
                description = "Set to 'Unrestricted' for instant delivery during idle.",
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Service Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    StatusRow("Telegram Relay", if (tgToken.isNotBlank()) "Configured" else "Missing")
                    StatusRow("SMS Fallback", if (fwdNumber.isNotBlank()) "Configured" else "Missing")
                    StatusRow("User Name", userName.ifBlank { "Not Set" })
                }
            }

            HorizontalDivider()

            Text("Identity Settings", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editedUserName,
                    onValueChange = { editedUserName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = {
                    scope.launch {
                        settings.updateUserName(editedUserName)
                        editedUserName = ""
                    }
                }) {
                    Text("Set")
                }
            }

            HorizontalDivider()

            Text("Sender Filters (Regex)", style = MaterialTheme.typography.titleMedium)
            
            if (regexList.isEmpty()) {
                Text(
                    "No filters active. All incoming SMS will be relayed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newRegex,
                    onValueChange = { newRegex = it },
                    label = { Text("Add Regex (e.g. .*BANK.*)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = {
                    if (newRegex.isNotBlank()) {
                        scope.launch {
                            settings.addRegex(newRegex)
                            newRegex = ""
                        }
                    }
                }) {
                    Text("Add")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                regexList.forEach { regex ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(regex, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = {
                                scope.launch { settings.removeRegex(regex) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Manual Tests", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val intent = Intent(context, SmsForwardingService::class.java).apply {
                            putExtra("EXTRA_TO", fwdNumber)
                            putExtra("EXTRA_BODY", "Test] Telegram Manual Test")
                            putExtra("EXTRA_FORCE_SMS", false)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Telegram")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = fwdNumber.isNotBlank(),
                    onClick = {
                        val intent = Intent(context, SmsForwardingService::class.java).apply {
                            putExtra("EXTRA_TO", fwdNumber)
                            putExtra("EXTRA_BODY", "Test] SMS Fallback Test")
                            putExtra("EXTRA_FORCE_SMS", true)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("SMS")
                }
            }
        }
    }
}

@Composable
fun ReliabilityBanner(
    isVisible: Boolean,
    title: String,
    description: String,
    action: String
) {
    val context = LocalContext.current
    if (isVisible) {
        OutlinedCard(
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    val intent = Intent(action).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("FIX")
                }
            }
        }
    }
}

private fun checkCorePermissions(context: Context): Boolean {
    val smsReceive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val smsSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    // Notifications are important but not "core" to the relay logic itself.
    // However, if we want the banner to disappear only when everything is perfect:
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    return smsReceive && smsSend && notifications
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit) {
    CenterAlignedTopAppBar(title = title)
}
