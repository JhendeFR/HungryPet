package com.usfx.hungrypet.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.usfx.hungrypet.ui.components.PhoneCameraView
import com.usfx.hungrypet.viewmodel.MainViewModel

@Composable
fun AiCamScreen(viewModel: MainViewModel) {
    // Estados que provienen de Firebase (ESP32)
    val cameraFrame by viewModel.cameraFrame.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()

    // Estados para la prueba local del teléfono (ML Kit)
    var isPhoneCameraActive by remember { mutableStateOf(false) }
    var currentAiDetection by remember { mutableStateOf("Buscando mascotas...") }

    val context = LocalContext.current

    // Lanzador de permisos de Android: Pide permiso al usuario la primera vez que abre la cámara
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isPhoneCameraActive = true
            if (isStreaming) viewModel.toggleCameraStream() // Apaga el ESP32 si abres el teléfono para evitar choques
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // --- VISOR PRINCIPAL (Caja negra) ---
        // Aquí decidimos dinámicamente qué mostrar basándonos en los booleanos de estado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                // 1. Mostrar IA Local
                isPhoneCameraActive -> {
                    PhoneCameraView(
                        onDetectionUpdate = { result -> currentAiDetection = result }
                    )
                    // Overlay de texto flotante
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = currentAiDetection,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // 2. Mostrar Cámara del ESP32
                isStreaming -> {
                    if (cameraFrame != null) {
                        Image(
                            bitmap = cameraFrame!!.asImageBitmap(),
                            contentDescription = "Stream del ESP32",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
                // 3. Estado en reposo (Icono por defecto)
                else -> {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- BOTONES PARA EL ESP32 (SISTEMA REAL) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    if (isPhoneCameraActive) isPhoneCameraActive = false
                    viewModel.toggleCameraStream()
                },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isStreaming) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(if (isStreaming) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStreaming) "Detener ESP32" else "Cámara ESP32", textAlign = TextAlign.Center)
            }

            Button(
                onClick = { viewModel.handleAutoDetection("Perro") },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Rounded.SmartToy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activar IA (ESP32)", textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(32.dp))

        // --- BOTÓN 3: ENTORNO DE PRUEBAS ---
        Text("Entorno de Pruebas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (isPhoneCameraActive) {
                    isPhoneCameraActive = false
                } else {
                    // Verifica permisos de cámara antes de abrir
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        if (isStreaming) viewModel.toggleCameraStream()
                        isPhoneCameraActive = true
                    } else {
                        // Si no tiene permisos, levanta el cuadro de diálogo de Android pidiéndolo
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isPhoneCameraActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isPhoneCameraActive) Icons.Rounded.Stop else Icons.Rounded.Science, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(if (isPhoneCameraActive) "Detener Prueba Local" else "Probar IA (Cámara Teléfono)")
        }
    }
}