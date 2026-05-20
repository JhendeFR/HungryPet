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
import androidx.compose.ui.unit.dp
import com.usfx.hungrypet.data.FeedingLog
import com.usfx.hungrypet.viewmodel.MainViewModel

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsState()
    var selectedPeriod by remember { mutableIntStateOf(0) } // 0 = Día, 1 = Semana, 2 = Mes
    val periods = listOf("Día", "Semana", "Mes")

    // Filtrado de raciones y cálculo del total agregado
    val filteredTotal = remember(logs, selectedPeriod) {
        when (selectedPeriod) {
            0 -> logs.take(3).sumOf { it.cantidad }
            1 -> logs.take(7).sumOf { it.cantidad }
            else -> logs.sumOf { it.cantidad }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Selector de periodos
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
            // Panel de Gráfico de Barras Integrado
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
                        Spacer(modifier = Modifier.height(20.dp))

                        // Renderizado del Canvas para barras dinámicas
                        val barColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            val barWidth = 45.dp.toPx()
                            val spacing = (size.width - (barWidth * 3)) / 4
                            val maxAmount = 150f

                            val heights = when(selectedPeriod) {
                                0 -> listOf(50f, 30f, 40f)
                                1 -> listOf(110f, 95f, 130f)
                                else -> listOf(140f, 120f, 150f)
                            }

                            heights.forEachIndexed { i, h ->
                                val xOffset = spacing + i * (barWidth + spacing)
                                val normalizedHeight = (h / maxAmount) * size.height
                                val yOffset = size.height - normalizedHeight

                                // Carril de fondo de la barra
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(xOffset, 0f),
                                    size = Size(barWidth, size.height),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                                // Barra de datos sólida
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(xOffset, yOffset),
                                    size = Size(barWidth, normalizedHeight),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Desglose del Historial", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            // Listado de Tarjetas optimizado para Alta Definición en Modo Oscuro
            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        // surfaceContainerHigh asegura separación visual perfecta sobre fondo oscuro
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