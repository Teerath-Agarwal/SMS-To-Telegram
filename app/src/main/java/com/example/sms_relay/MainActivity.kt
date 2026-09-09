package com.example.sms_relay

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sms_relay.ui.theme.SMSRelayTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SMSRelayTheme {
                RelayScreen()
            }
        }
    }
}

@Composable
fun RelayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { RelaySettings(context) }

    val forwardingNumber by settings.forwardingNumber.collectAsState(initial = "")

    var newNumber by remember { mutableStateOf("") }
    
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var isIgnoringBattery by remember { 
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("SMS Relay Settings") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isIgnoringBattery) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery optimization is ON", style = MaterialTheme.typography.labelLarge)
                            Text("Relay might fail in background.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }) {
                            Text("FIX")
                        }
                    }
                }
            }

            Text("Forwarding Number (SMS Fallback)", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newNumber,
                    onValueChange = { newNumber = it },
                    label = { Text("Primary Phone Number") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = forwardingNumber.isNullOrBlank()
                )
                Button(
                    onClick = {
                        if (newNumber.isNotBlank()) {
                            scope.launch {
                                settings.updateForwardingNumber(newNumber)
                                newNumber = ""
                            }
                        }
                    },
                    enabled = forwardingNumber.isNullOrBlank()
                ) {
                    Text("Set")
                }
            }

            if (!forwardingNumber.isNullOrBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fallback to:", style = MaterialTheme.typography.labelSmall)
                            Text(forwardingNumber!!, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = {
                            scope.launch { settings.updateForwardingNumber("") }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Test Relays", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val intent = Intent(context, SmsForwardingService::class.java).apply {
                            putExtra("EXTRA_TO", forwardingNumber)
                            putExtra("EXTRA_BODY", "[Test] This is a Telegram Test Message")
                            putExtra("EXTRA_FORCE_SMS", false)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Telegram")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !forwardingNumber.isNullOrBlank(),
                    onClick = {
                        val intent = Intent(context, SmsForwardingService::class.java).apply {
                            putExtra("EXTRA_TO", forwardingNumber)
                            putExtra("EXTRA_BODY", "[Test] This is an SMS Fallback Test")
                            putExtra("EXTRA_FORCE_SMS", true)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test SMS")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Note: Configure Bot Token and Chat ID in RelayConfig.kt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit) {
    CenterAlignedTopAppBar(title = title)
}
