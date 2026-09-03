package com.ridescore.app.domain.receipt

import com.ridescore.app.domain.model.SourceApp

/**
 * A ride that actually happened, read off the order-details screen the app
 * shows once it is finished.
 *
 * Everything RideScore says before a ride is a prediction. This is the only
 * place it learns what really happened: the minutes the ride really took, the
 * kilometres really covered, and the rupees that really arrived after the
 * platform took its cut. Without it every argument about speeds and
 * deductions stays an argument.
 */
data class RideReceipt(
    val sourceApp: SourceApp,
    /** What reached the driver, after commission and taxes. */
    val totalEarning: Double,
    /** What the customer paid, when the screen breaks it out. */
    val customerFare: Double? = null,
    /**
     * A tip or an add-on the customer paid on top of the fare.
     *
     * Worth its own field because the platform's fee is charged on it too: a
     * ₹52 fare with a ₹10 extra was billed ₹5.76, which is 9.29% of ₹62, not
     * of ₹52.
     */
    val customerExtra: Double? = null,
    val commission: Double? = null,
    val taxesAndFees: Double? = null,
    /** The paid leg, as the platform measured it. */
    val tripKm: Double? = null,
    /** How long it really took, as the platform measured it. */
    val tripMinutes: Double? = null,
    val pickupKm: Double? = null,
    val rideType: String? = null,
    /** The time printed on the receipt, e.g. "8:39 am". Not parsed further. */
    val orderTime: String? = null,
    val signature: String,
) {
    /** Rupees an hour the ride really paid, over its own minutes. */
    val earningPerHour: Double?
        get() = tripMinutes?.takeIf { it > 0.0 }?.let { totalEarning / (it / 60.0) }

    val earningPerKm: Double?
        get() = tripKm?.takeIf { it > 0.0 }?.let { totalEarning / it }

    /** Everything the customer paid, fare and extra together. */
    val grossBeforeDeductions: Double?
        get() = customerFare?.let { it + (customerExtra ?: 0.0) }

    /** The platform's share, when the screen showed enough to work it out. */
    val deducted: Double?
        get() = grossBeforeDeductions?.let { it - totalEarning }
}
