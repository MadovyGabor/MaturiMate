package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The Material 3 date picker in a dialog, limited to the days from [earliest] to [latest].
 * Dates are converted at midnight UTC, the way the picker counts them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun AppDatePickerDialog(
    title: String,
    confirmText: String,
    dismissText: String,
    initial: LocalDate?,
    earliest: LocalDate,
    latest: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    fun LocalDate.millis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    fun Long.date() = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

    val range = remember(earliest, latest) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.date() in earliest..latest
            override fun isSelectableYear(year: Int) = year in earliest.year..latest.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.millis(),
        yearRange = earliest.year..latest.year,
        selectableDates = range,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(it.date()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(confirmText, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText, style = MaterialTheme.typography.labelLarge) }
        },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        DatePicker(
            state = state,
            title = { Text(title, style = MaterialTheme.typography.labelLarge) },
            showModeToggle = false,
        )
    }
}
