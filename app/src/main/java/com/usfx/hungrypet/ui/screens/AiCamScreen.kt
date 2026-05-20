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
    val cameraFrame by viewModel.cameraFrame.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()

    // NUEVOS ESTADOS ENLAZADOS AL VIEWMODEL PARA EL ESP32
    val isEsp32AiActive by viewModel.isEsp32AiActive.collectAsState()
    val esp32DetectionResult by viewModel.esp32DetectionResult.collectAsState()

    // Estados para la prueba local del teléfono
    var isPhoneCameraActive by remember { mutableStateOf(false) }
    var currentAiDetection by remember { mutableStateOf("Buscando mascotas...") }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isPhoneCameraActive = true
            if (isStreaming) viewModel.toggleCameraStream()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // --- VISOR DE LA CÁMARA (Alterna entre ESP32 y Teléfono) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                isPhoneCameraActive -> {
                    PhoneCameraView(onDetectionUpdate = { result -> currentAiDetection = result })

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = currentAiDetection, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
                isStreaming -> {
                    if (cameraFrame != null) {
                        Image(
                            bitmap = cameraFrame!!.asImageBitmap(),
                            contentDescription = "Stream del ESP32",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // TEXTO FLOTANTE CON EL RESULTADO DE LA IA DEL ESP32
                        if (isEsp32AiActive) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = esp32DetectionResult,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- BOTONES PARA EL ESP32 (SISTEMA REAL) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón 1: Ver Cámara ESP32
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

            // Botón 2: Activar/Desactivar IA en el ESP32 (DINÁMICO)
            Button(
                onClick = { viewModel.toggleEsp32Ai() },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = isStreaming, // Solo se puede activar si la cámara está encendida
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEsp32AiActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (isEsp32AiActive) Icons.Rounded.SmartToy else Icons.Rounded.Circle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEsp32AiActive) "IA Activa" else "Activar IA (ESP32)", textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(32.dp))

        // --- BOTÓN 3: ENTORNO DE PRUEBAS (CÁMARA DEL TELÉFONO) ---
        Text("Entorno de Pruebas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (isPhoneCameraActive) {
                    isPhoneCameraActive = false
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        if (isStreaming) viewModel.toggleCameraStream()
                        isPhoneCameraActive = true
                    } else {
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