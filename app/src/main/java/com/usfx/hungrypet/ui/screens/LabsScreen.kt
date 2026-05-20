package com.usfx.hungrypet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.usfx.hungrypet.ui.components.PhoneCameraView
import com.usfx.hungrypet.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsScreen(viewModel: MainViewModel) {
    var isLocalIaTesting by remember { mutableStateOf(false) }
    var localDetectionText by remember { mutableStateOf("Esperando cuadro...") }

    // Campos de Aprovisionamiento BLE
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var operationMode by remember { mutableIntStateOf(0) } // 0 = Local (Websockets), 1 = Red (Firebase)

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Laboratorio de Pruebas (Labs)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // PANEL 1: CONFIGURACIÓN BLUETOOTH (BLE PROVISIONING)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronización BLE del Dispensador", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = { Text("Nombre de Red Wi-Fi") },
                    leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = wifiPassword,
                    onValueChange = { wifiPassword = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Modo de Operación del Hardware:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = operationMode == 0,
                        onClick = { operationMode = 0 },
                        label = { Text("Wifi Local (P2P)") }
                    )
                    FilterChip(
                        selected = operationMode == 1,
                        onClick = { operationMode = 1 },
                        label = { Text("Red Nube (Firebase)") }
                    )
                }

                Button(
                    onClick = { /* Invocar BluetoothGatt para transmitir tramas de bytes */ },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Rounded.BluetoothConnected, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar Credenciales por BLE")
                }
            }
        }

        // PANEL 2: COMPONENTE DE DEPURACIÓN DE IA LOCAL
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Science, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Diagnóstico IA Interna (Móvil)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLocalIaTesting) {
                        PhoneCameraView(onDetectionUpdate = { localDetectionText = it })
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                .padding(4.dp)
                        ) {
                            Text(localDetectionText, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text("Módulo de Cámara Apagado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { isLocalIaTesting = !isLocalIaTesting },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isLocalIaTesting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(if (isLocalIaTesting) Icons.Rounded.Stop else Icons.Rounded.VideoLabel, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isLocalIaTesting) "Apagar Cámara de Diagnóstico" else "Encender Cámara de Diagnóstico")
                }
            }
        }
    }
}