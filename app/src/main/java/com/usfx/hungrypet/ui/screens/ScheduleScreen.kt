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
import com.usfx.hungrypet.viewmodel.MainViewModel
import com.usfx.hungrypet.viewmodel.ScheduleItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: MainViewModel) { // PASAMOS EL VIEWMODEL

    // Observamos la lista que ahora vive en el ViewModel
    val schedules by viewModel.schedules.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var scheduleToDelete by remember { mutableStateOf<ScheduleItem?>(null) }
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

        if (schedules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    OutlinedCard(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (schedule.isActive) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { },
                                onLongClick = {
                                    scheduleToDelete = schedule
                                    showDeleteDialog = true
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    schedule.time,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (schedule.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Scale, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${schedule.amount}g por ración", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Switch(
                                checked = schedule.isActive,
                                onCheckedChange = { isActive ->
                                    viewModel.toggleScheduleActive(schedule, isActive)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            AddScheduleSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { time, amount ->
                    viewModel.addSchedule(time, amount)
                    showAddSheet = false
                }
            )
        }

        if (showDeleteDialog && scheduleToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("¿Eliminar horario?") },
                text = { Text("Se cancelará la alimentación automática de las ${scheduleToDelete!!.time}.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeSchedule(scheduleToDelete!!)
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Eliminar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}