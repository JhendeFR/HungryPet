package com.usfx.hungrypet.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usfx.hungrypet.viewmodel.MainViewModel

@Composable
fun AiCamScreen(viewModel: MainViewModel) {
    val cameraFrame by viewModel.cameraFrame.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isEsp32AiActive by viewModel.isEsp32AiActive.collectAsState()
    val esp32DetectionResult by viewModel.esp32DetectionResult.collectAsState()
    val isFlashOn by viewModel.isEsp32FlashOn.collectAsState()
    val aiAmount by viewModel.aiDispenseAmount.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Visor de Cámara de Red Remota
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isStreaming) {
                if (cameraFrame != null) {
                    Image(
                        bitmap = cameraFrame!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = esp32DetectionResult, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                } else {
                    CircularProgressIndicator()
                }
            } else {
                Icon(Icons.Rounded.Router, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }

        // --- PANEL DE HARDWARE AVANZADO ---
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FlashlightOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Iluminación de Apoyo (Flash)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = isFlashOn, onCheckedChange = { viewModel.toggleEsp32Flash() })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SettingsInputAntenna, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ración Automática IA: $aiAmount g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Slider(
                    value = aiAmount.toFloat(),
                    onValueChange = { viewModel.setAiDispenseAmount(it.toInt()) },
                    valueRange = 20f..100f,
                    steps = 7
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- DOS BOTONES DE CONTROL DE SISTEMA ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { viewModel.toggleCameraStream() },
                modifier = Modifier.weight(1f).height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isStreaming) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(if (isStreaming) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("ESP32 Live")
            }

            Button(
                onClick = { viewModel.toggleEsp32Ai() },
                modifier = Modifier.weight(1f).height(64.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = isStreaming,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEsp32AiActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.SmartToy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEsp32AiActive) "IA Activa" else "Activar IA")
            }
        }
    }
}