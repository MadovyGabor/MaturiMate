package com.madovygabor.maturimate.core.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Date Picker Field Composable
@Composable
fun DatePickerField(
    label: String,
    selectedDateMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "ÉÉÉÉ. HH. NN."
) {
    // State to control the visibility of the date picker modal
    var showModal by remember { mutableStateOf(false) }

    // Format the selected date for display
    val formattedDate = remember(selectedDateMillis) {
        selectedDateMillis?.let { convertMillisToDate(it) } ?: ""
    }

    // Main UI Box
    Box(modifier = modifier) {
        // Outlined Text Field displaying the selected date
        OutlinedTextField(
            value = formattedDate,
            onValueChange = { },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { showModal = true }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Dátum kiválasztása",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        // Date Picker Modal
        if (showModal) {
            MaturiDatePickerModal(
                initialSelectedDateMillis = selectedDateMillis,
                onDateSelected = {
                    onDateSelected(it)
                    showModal = false
                },
                onDismiss = { showModal = false }
            )
        }
    }
}

// Date Picker Modal Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaturiDatePickerModal(
    initialSelectedDateMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    // Remember the state of the date picker
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis
    )

    // Date Picker Dialog
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onDateSelected(datePickerState.selectedDateMillis) }) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Mégse")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

// Converts milliseconds since epoch to a formatted date string "yyyy. MM. dd."
fun convertMillisToDate(millis: Long): String {

    val formatter = SimpleDateFormat("yyyy. MM. dd.", Locale.getDefault())
    return formatter.format(Date(millis))
}