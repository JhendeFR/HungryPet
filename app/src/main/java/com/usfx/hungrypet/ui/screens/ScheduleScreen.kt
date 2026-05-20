package com.usfx.hungrypet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usfx.hungrypet.ui.components.AddScheduleSheet
import java.util.UUID

// Estructura de datos mejorada con una ID única para evitar conflictos al eliminar
data class ScheduleItem(
    val id: UUID = UUID.randomUUID(),
    val time: String,
    val amount: Int,
    var isActive: Boolean
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    // Lista reactiva observable cargada con los datos iniciales
    val schedules = remember {
        mutableStateListOf(
            ScheduleItem(time = "08:00 AM", amount = 50, isActive = true),
            ScheduleItem(time = "02:30 PM", amount = 40, isActive = true)
        )
    }

    // Estados para controlar la eliminación (Long-press)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var scheduleToDelete by remember { mutableStateOf<ScheduleItem?>(null) }

    // Estado para controlar la adición de un nuevo horario (Sheet)
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Añadir")
            }
        }
    ) { innerPadding ->

        // Vista en caso de que el usuario elimine todas las programaciones
        if (schedules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No hay horarios programados.\nPresiona + para añadir uno.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // El parámetro 'key' asegura una animación y seguimiento correctos al eliminar
                items(schedules, key = { it.id }) { schedule ->
                    var checked by remember { mutableStateOf(schedule.isActive) }

                    OutlinedCard(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (checked) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { /* Clic normal opcional */ },
                                onLongClick = {
                                    scheduleToDelete = schedule
                                    showDeleteDialog = true
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    schedule.time,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Scale,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${schedule.amount}g por ración", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    checked = it
                                    schedule.isActive = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // --- HOJA INFERIOR DINÁMICA ---
        if (showAddSheet) {
            AddScheduleSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { time, amount ->
                    schedules.add(ScheduleItem(time = time, amount = amount, isActive = true))
                    showAddSheet = false
                }
            )
        }

        // --- DIÁLOGO DE CONFIRMACIÓN DE ELIMINACIÓN ---
        if (showDeleteDialog && scheduleToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("¿Eliminar horario?") },
                text = { Text("Se cancelará la alimentación automática de las ${scheduleToDelete!!.time}.") },
                confirmButton = {
                    Button(
                        onClick = {
                            schedules.remove(scheduleToDelete)
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
