package com.usfx.hungrypet.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usfx.hungrypet.data.FeedingLog
import com.usfx.hungrypet.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsState()
    var selectedPeriod by remember { mutableIntStateOf(0) } // 0 = Día, 1 = Semana, 2 = Mes
    val periods = listOf("Día", "Semana", "Mes")

    // --- PIPELINE DE PROCESAMIENTO DE DATOS EN TIEMPO REAL ---
    val barValues = remember(logs, selectedPeriod) {
        val sdfFull = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val calendarNow = Calendar.getInstance()
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(calendarNow.time)

        when (selectedPeriod) {
            0 -> { // HOY: Agrupado por bloques horarios
                val todayLogs = logs.filter { it.fechaHora.startsWith(todayStr) }
                var manana = 0f
                var tarde = 0f
                var noche = 0f

                todayLogs.forEach { log ->
                    try {
                        val date = sdfFull.parse(log.fechaHora)
                        val logCal = Calendar.getInstance().apply { time = date ?: Date() }
                        val hour = logCal.get(Calendar.HOUR_OF_DAY)
                        when (hour) {
                            in 6..11 -> manana += log.cantidad
                            in 12..18 -> tarde += log.cantidad
                            else -> noche += log.cantidad // Madrugada y noche profunda
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                listOf(manana, tarde, noche)
            }
            1 -> { // SEMANA: Agrupado en bloques de 7 días
                var estaSemana = 0f
                var semanaPasada = 0f
                var dosSemanasPasadas = 0f

                logs.forEach { log ->
                    try {
                        val logDate = sdfFull.parse(log.fechaHora) ?: return@forEach
                        val diffMillis = calendarNow.timeInMillis - logDate.time
                        val diffDays = diffMillis / (1000 * 60 * 60 * 24)
                        when (diffDays) {
                            in 0..7 -> estaSemana += log.cantidad
                            in 8..14 -> semanaPasada += log.cantidad
                            in 15..21 -> dosSemanasPasadas += log.cantidad
                        }
                    } catch (e: Exception) { }
                }
                listOf(dosSemanasPasadas, semanaPasada, estaSemana)
            }
            else -> { // MES: Agrupado por meses del año
                var esteMes = 0f
                var mesPasado = 0f
                var dosMesesPasados = 0f
                val currentMonth = calendarNow.get(Calendar.MONTH)
                val currentYear = calendarNow.get(Calendar.YEAR)

                logs.forEach { log ->
                    try {
                        val logDate = sdfFull.parse(log.fechaHora) ?: return@forEach
                        val logCal = Calendar.getInstance().apply { time = logDate }
                        val monthDiff = (currentYear - logCal.get(Calendar.YEAR)) * 12 +
                                (currentMonth - logCal.get(Calendar.MONTH))
                        when (monthDiff) {
                            0 -> esteMes += log.cantidad
                            1 -> mesPasado += log.cantidad
                            2 -> dosMesesPasados += log.cantidad
                        }
                    } catch (e: Exception) { }
                }
                listOf(dosMesesPasados, mesPasado, esteMes)
            }
        }
    }

    // El consumo total de la tarjeta superior ahora es la suma real de las barras visualizadas
    val filteredTotal = remember(barValues) { barValues.sum().toInt() }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedPeriod) {
            periods.forEachIndexed { index, title ->
                Tab(
                    selected = selectedPeriod == index,
                    onClick = { selectedPeriod = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Consumo Total del Periodo:", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${filteredTotal}g", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val barColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant

                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            val barWidth = 45.dp.toPx()
                            val sectionWidth = size.width / 3

                            // Determinamos un máximo dinámico para que las barras escalen bien y no se salgan del Canvas
                            val maxAmount = (barValues.maxOrNull() ?: 150f).coerceAtLeast(150f)

                            barValues.forEachIndexed { i, value ->
                                val xOffset = (sectionWidth * i) + (sectionWidth / 2) - (barWidth / 2)
                                val normalizedHeight = (value / maxAmount) * size.height
                                val yOffset = size.height - normalizedHeight

                                // Fondo de carril
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(xOffset, 0f),
                                    size = Size(barWidth, size.height),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                                // Barra con cantidad real de Room
                                if (normalizedHeight > 0) {
                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset(xOffset, yOffset),
                                        size = Size(barWidth, normalizedHeight),
                                        cornerRadius = CornerRadius(12f, 12f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Etiquetas del Eje X
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val labels = when(selectedPeriod) {
                                0 -> listOf("Mañana", "Tarde", "Noche")
                                1 -> listOf("Sem -2", "Sem -1", "Esta Sem")
                                else -> listOf("Mes -2", "Mes -1", "Este Mes")
                            }

                            labels.forEach { label ->
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Desglose del Historial", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text("Dispensación ${log.tipo}", fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Column {
                                Text(log.fechaHora)
                                if(log.tipo == "Automático") {
                                    Text("Identificado: ${log.resultadoDeteccion}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Medium)
                                }
                            }
                        },
                        trailingContent = {
                            Text("${log.cantidad}g", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        },
                        leadingContent = {
                            Icon(
                                when (log.tipo) {
                                    "Manual" -> Icons.Rounded.TouchApp
                                    "Programado" -> Icons.Rounded.Alarm
                                    else -> Icons.Rounded.SmartToy
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }
}