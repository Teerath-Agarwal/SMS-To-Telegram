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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
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
    val regexList by settings.senderRegexList.collectAsState(initial = emptySet())

    var newNumber by remember { mutableStateOf("") }
    var newRegex by remember { mutableStateOf("") }

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
                Manifest.permission.SEND_SMS
            )
        )
    }

    LaunchedEffect(forwardingNumber) {
        if (forwardingNumber != null) {
            newNumber = forwardingNumber!!
        }
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

            Text("Forwarding Settings", style = MaterialTheme.typography.titleMedium)
            
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
                            Text("Relaying to:", style = MaterialTheme.typography.labelSmall)
                            Text(forwardingNumber!!, style = MaterialTheme.typography.bodyLarge)
                        }
                        Row {
                            IconButton(onClick = {
                                SmsRelayWorker.forwardSms(context, forwardingNumber!!, "[SMS Relay] Test Message")
                            }) {
                                Icon(Icons.Default.Send, contentDescription = "Test Send")
                            }
                            IconButton(onClick = {
                                scope.launch { settings.updateForwardingNumber("") }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Filter Regex List", style = MaterialTheme.typography.titleMedium)
            
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

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(regexList.toList()) { regex ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(regex, modifier = Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { settings.removeRegex(regex) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
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
