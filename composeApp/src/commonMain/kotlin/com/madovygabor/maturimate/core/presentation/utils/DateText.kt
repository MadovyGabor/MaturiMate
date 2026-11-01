package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.date_long
import maturimate.composeapp.generated.resources.months
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** A date written out in the app language: "2027. március 15." or "15. marca 2027". */
@Composable
fun longDate(date: LocalDate): String {
    val months = stringArrayResource(Res.array.months)
    return stringResource(Res.string.date_long, date.year, months[date.month.ordinal], date.day)
}
