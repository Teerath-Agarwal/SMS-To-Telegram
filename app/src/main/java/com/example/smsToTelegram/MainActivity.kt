package com.example.smsToTelegram

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.smsToTelegram.ui.theme.SMSRelayTheme
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isIgnoringBattery) {
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
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Improve Reliability", style = MaterialTheme.typography.labelLarge)
                            Text("Set to 'Unrestricted' to prevent delays during deep sleep.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
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
                            putExtra("EXTRA_BODY", "Test] This is a Telegram Test Message")
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
                    Text("Test Telegram")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !forwardingNumber.isNullOrBlank(),
                    onClick = {
                        val intent = Intent(context, SmsForwardingService::class.java).apply {
                            putExtra("EXTRA_TO", forwardingNumber)
                            putExtra("EXTRA_BODY", "Test] This is an SMS Fallback Test")
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
                    Text("Test SMS")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit) {
    CenterAlignedTopAppBar(title = title)
}
