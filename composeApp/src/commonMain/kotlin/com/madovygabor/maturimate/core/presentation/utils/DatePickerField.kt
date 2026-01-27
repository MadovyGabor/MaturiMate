package com.madovygabor.maturimate.core.presentation.utils

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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/**
 * A custom Date Picker field that uses Material 3 [DatePicker].
 * Fully compatible with Kotlin Multiplatform using kotlinx-datetime.
 * * @param label The label to display on the text field.
 * @param label The label to display on the text field.in milliseconds.
 * @param onDateSelected Callback when a new date is picked.
 * @param modifier Styling for the container.
 * @param placeholder Text shown when no date is selected.
 */
@Composable
fun DatePickerField(
    label: String,
    selectedDateMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "YYYY. MM. DD."
) {
    // State to manage modal visibility
    var showModal by remember { mutableStateOf(false) }

    // Convert millis to formatted string whenever the selection changes
    val formattedDate = remember(selectedDateMillis) {
        selectedDateMillis?.let { convertMillisToDate(it) } ?: ""
    }


    Box(modifier = modifier) {
        OutlinedTextField(
            value = formattedDate,
            onValueChange = { }, // Read-only field
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { showModal = true }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select Date",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

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

/**
 * Material 3 Date Picker Dialog wrapper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaturiDatePickerModal(
    initialSelectedDateMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis
    )


    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onDateSelected(datePickerState.selectedDateMillis) }) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Platform-independent date formatting using kotlinx-datetime.
 * Converts Epoch Milliseconds to "YYYY. MM. DD." format.
 */
@OptIn(ExperimentalTime::class)
fun convertMillisToDate(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    // Manual formatting for KMP compatibility (since SimpleDateFormat is JVM-only)
    val year = dateTime.year
    val month = dateTime.month.number.toString().padStart(2, '0')
    val day = dateTime.day.toString().padStart(2, '0')

    return "$year. $month. $day."
}