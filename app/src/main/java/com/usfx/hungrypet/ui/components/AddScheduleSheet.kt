package com.usfx.hungrypet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleSheet(
    onDismiss: () -> Unit,
    onConfirm: (time: String, amount: Int) -> Unit
) {
    // Estado para la hoja inferior
    val sheetState = rememberModalBottomSheetState()

    // Estado para los inputs
    var amountInput by remember { mutableStateOf("") }
    var selectedHour by remember { mutableIntStateOf(8) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Formatear hora para mostrarla en el botón
    val timeLabel = String.format(Locale.getDefault(), "%02d:%02d %s",
        if (selectedHour % 12 == 0) 12 else selectedHour % 12,
        selectedMinute,
        if (selectedHour < 12) "AM" else "PM"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Nueva Programación",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECCIÓN DE HORA (Visualmente atractiva)
            Text("¿A qué hora debe comer?", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                onClick = { showTimePicker = true },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(timeLabel, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECCIÓN DE RACIÓN
            OutlinedTextField(
                value = amountInput,
                onValueChange = { if (it.length <= 4) amountInput = it },
                label = { Text("Cantidad de alimento") },
                suffix = { Text("gramos") },
                leadingIcon = { Icon(Icons.Rounded.Scale, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            // BOTONES DE ACCIÓN
            Button(
                onClick = {
                    val amount = amountInput.toIntOrNull()
                    if (amount != null && amount > 0) {
                        onConfirm(timeLabel, amount)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                enabled = amountInput.isNotBlank()
            ) {
                Text("Guardar Programación", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    // DIÁLOGO DEL RELOJ (Dial Time Picker)
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )

        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(24.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Selecciona la hora",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                    )

                    TimePicker(state = timePickerState)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
                        TextButton(onClick = {
                            selectedHour = timePickerState.hour
                            selectedMinute = timePickerState.minute
                            showTimePicker = false
                        }) { Text("Confirmar") }
                    }
                }
            }
        }
    }
}