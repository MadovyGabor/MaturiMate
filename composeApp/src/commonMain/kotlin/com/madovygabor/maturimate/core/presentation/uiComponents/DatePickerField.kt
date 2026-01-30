package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlin.time.ExperimentalTime
import kotlin.time.Instant.Companion.fromEpochMilliseconds

/**
 * A custom Material 3 Date Picker field tailored for the MaturiMate project.
 * * This component acts as a read-only [OutlinedTextField] that triggers a date selection modal.
 * It is fully KMP-compatible, utilizing [LocalDate] from the kotlinx-datetime library
 * to ensure type safety and consistent date handling across platforms.
 *
 * @param label The descriptive text to be displayed as the field's label.
 * @param selectedDate The currently selected [LocalDate], or null if no date is picked yet.
 * @param onDateSelected Callback invoked when the user confirms a date selection in the modal.
 * @param modifier The [Modifier] to be applied to the outer container.
 * @param placeholder The text to display when [selectedDate] is null.
 */
@Composable
fun DatePickerField(
    label: String,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "YYYY. MM. DD."
) {
    var showModal by remember { mutableStateOf(false) }

    // Formázás a korábban megbeszélt manuális módon
    val formattedDate = remember(selectedDate) {
        selectedDate?.let { date ->
            "${date.year}. ${date.month.number.toString().padStart(2, '0')}. ${date.day.toString().padStart(2, '0')}."
        } ?: ""
    }

    Box(modifier = modifier) {
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
                        contentDescription = "Select Date",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        if (showModal) {
            MaturiDatePickerModal(
                initialDate = selectedDate,
                onDateSelected = { date ->
                    onDateSelected(date)
                    showModal = false
                },
                onDismiss = { showModal = false }
            )
        }
    }
}

/**
 * An internal Material 3 [DatePickerDialog] wrapper.
 * * Handles the conversion between [LocalDate] and epoch milliseconds required by
 * the Material 3 [DatePickerState]. It uses [TimeZone.UTC] for conversions to avoid
 * timezone-related date shifts.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun MaturiDatePickerModal(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    // Converts LocalDate to Milliseconds at the start of the day in UTC for DatePicker compatibility.
    val initialMillis = remember(initialDate) {
        initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // Converts the selected milliseconds back to LocalDate using UTC.
                val selectedLocalDate = datePickerState.selectedDateMillis?.let {
                   fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                }
                onDateSelected(selectedLocalDate)
            }) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Mégse") // Can be localized using Res.string later
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}