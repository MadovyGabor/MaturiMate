import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Extension function to format LocalDate into a human-readable Hungarian format.
 * Example: 2026-05-12 -> 2026. május 12.
 */
fun LocalDate.toFormattedDateString(): String {
    val monthName = when (this.month) {
        Month.JANUARY -> "január"
        Month.FEBRUARY -> "február"
        Month.MARCH -> "március"
        Month.APRIL -> "április"
        Month.MAY -> "május"
        Month.JUNE -> "június"
        Month.JULY -> "július"
        Month.AUGUST -> "augusztus"
        Month.SEPTEMBER -> "szeptember"
        Month.OCTOBER -> "október"
        Month.NOVEMBER -> "november"
        Month.DECEMBER -> "december"
        else -> this.month.name.lowercase()
    }

    return "${this.year}. $monthName ${this.dayOfMonth}."
}

//TODO: UiText verzió, ha szükség lesz rá

// /**
// * Extension function to return a localized date format using UiText.
// * Handles the formatting logic: "Year. Month Day."
// */
//fun LocalDate.toLocalizedUiText(): UiText {
//    val monthRes = when (this.month) {
//        Month.JANUARY -> Res.string.month_january
//        Month.FEBRUARY -> Res.string.month_february
//        Month.MARCH -> Res.string.month_march
//        Month.APRIL -> Res.string.month_april
//        Month.MAY -> Res.string.month_may
//        Month.JUNE -> Res.string.month_june
//        Month.JULY -> Res.string.month_july
//        Month.AUGUST -> Res.string.month_august
//        Month.SEPTEMBER -> Res.string.month_september
//        Month.OCTOBER -> Res.string.month_october
//        Month.NOVEMBER -> Res.string.month_november
//        Month.DECEMBER -> Res.string.month_december
//        else -> Res.string.month_january // Fallback
//    }
//
//    // Összerakunk egy dinamikus szöveget: "2026. [hónap] 12."
//    // Megjegyzés: A pontos formátum (pontok helye) nyelvenként eltérhet,
//    // de egyszerűség kedvéért most a magyar sémát követjük.
//    return UiText.DynamicString("${this.year}. ") +
//            UiText.StringRes(monthRes) +
//            UiText.DynamicString(" ${this.dayOfMonth}.")
//}
//
//// Segédfüggvény a UiText-ek összefűzéséhez (ha még nincs meg)
//@Composable
//operator fun UiText.plus(other: UiText): UiText {
//    return UiText.DynamicString(this.asString() + other.asString())
//}