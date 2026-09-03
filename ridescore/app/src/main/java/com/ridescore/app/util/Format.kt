package com.ridescore.app.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Short, glanceable formatting. The driver reads these in about a second. */
object Format {

    fun rupees(value: Double): String =
        if (abs(value - value.roundToInt()) < 0.005) "₹${value.roundToInt()}"
        else String.format(Locale.US, "₹%.2f", value)

    fun rupeesRounded(value: Double): String = "₹${value.roundToInt()}"

    fun perHour(value: Double): String = "₹${value.roundToInt()}/hr"

    /** Two decimals, for per-km rates: "₹4.59". */
    fun rupees2(value: Double): String = String.format(Locale.US, "₹%.2f", value)

    fun perKm(value: Double): String = String.format(Locale.US, "₹%.2f/km", value)

    fun km(value: Double): String = String.format(Locale.US, "%.1f km", value)

    fun minutes(value: Double): String = "${value.roundToInt()} min"

    fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

    fun decimal(value: Double, places: Int = 1): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.${places}f", value)
}
