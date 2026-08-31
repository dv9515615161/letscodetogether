package com.ridescore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridescore.app.data.log.LogStats
import com.ridescore.app.domain.settings.AppMode
import com.ridescore.app.domain.settings.EarningsPlan
import com.ridescore.app.domain.settings.OverlayMode
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.util.Format

/**
 * Every rule the decision engine uses is editable here, with the brief's
 * defaults pre-filled.
 */
@Composable
fun SettingsScreen(
    settings: RideScoreSettings,
    onChange: ((RideScoreSettings) -> RideScoreSettings) -> Unit,
    onReset: () -> Unit,
    onPreviewVoice: () -> Unit,
    onOcrToggle: (Boolean) -> Unit,
    logStats: LogStats,
    onShareLog: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionCard(
            title = "Vehicle and fuel",
            subtitle = "Fuel cost per km = petrol price ÷ mileage = " +
                "${Format.rupees2(settings.fuelCostPerKm)}/km",
        ) {
            Text(settings.vehicleName, style = MaterialTheme.typography.bodyLarge)
            Text("Mileage (km per litre)", style = MaterialTheme.typography.bodySmall)
            ChipRow(
                options = RideScoreSettings.MILEAGE_PRESETS,
                selected = RideScoreSettings.MILEAGE_PRESETS.firstOrNull { it == settings.mileageKmPerLitre },
                label = { Format.decimal(it, 1) },
                onSelect = { preset -> onChange { it.copy(mileageKmPerLitre = preset) } },
            )
            NumberField("Custom mileage", settings.mileageKmPerLitre, "km/L", 1) { v ->
                onChange { it.copy(mileageKmPerLitre = v) }
            }
            NumberField("Petrol price", settings.petrolPricePerLitre, "₹/L", 2) { v ->
                onChange { it.copy(petrolPricePerLitre = v) }
            }
        }

        SectionCard(
            title = "Acceptance rules",
            subtitle = "Accept needs the hourly rate AND the per-km rate to pass. " +
                "Anything under the maybe threshold is a reject.",
        ) {
            NumberField("Accept at or above", settings.acceptNetPerHour, "₹ net/hour", 0) { v ->
                onChange { it.copy(acceptNetPerHour = v) }
            }
            NumberField("Maybe at or above", settings.maybeNetPerHour, "₹ net/hour", 0) { v ->
                onChange { it.copy(maybeNetPerHour = v) }
            }
            NumberField("Minimum net per km", settings.minNetPerKm, "₹/km", 2) { v ->
                onChange { it.copy(minNetPerKm = v) }
            }
            SwitchRow(
                label = "Require both metrics",
                checked = settings.requireBothMetrics,
                description = "Off means the hourly rate alone decides",
            ) { on -> onChange { it.copy(requireBothMetrics = on) } }
        }

        SectionCard(
            title = "Riding speeds",
            subtitle = "When an offer does not print a time, RideScore works it out " +
                "from these speeds and rounds up to the next whole minute. Anything " +
                "estimated is marked with a ~ on the card.",
        ) {
            NumberField("Pickup speed", settings.pickupSpeedKmph, "km/h", 1) { v ->
                onChange { it.copy(pickupSpeedKmph = v) }
            }
            NumberField("Trip speed", settings.tripSpeedKmph, "km/h", 1) { v ->
                onChange { it.copy(tripSpeedKmph = v) }
            }
            SwitchRow("Include pickup distance", settings.includePickupDistance) { on ->
                onChange { it.copy(includePickupDistance = on) }
            }
            SwitchRow("Include pickup time", settings.includePickupTime) { on ->
                onChange { it.copy(includePickupTime = on) }
            }
        }

        SectionCard(
            title = "The ride back",
            subtitle = "A long drop can pay well and still ruin the hour, because the " +
                "kilometres back are unpaid fuel and unpaid time. Turn this on and long " +
                "trips are scored on the round trip instead of the paid leg.",
        ) {
            SwitchRow(
                label = "Assume you ride back empty",
                checked = settings.emptyReturnEnabled,
                description = "Nothing is assumed about demand out there - only that if no " +
                    "order comes, you ride back.",
            ) { on -> onChange { it.copy(emptyReturnEnabled = on) } }

            NumberField(
                "Only for trips longer than",
                settings.emptyReturnFromKm,
                "km",
                0,
                enabled = settings.emptyReturnEnabled,
            ) { v -> onChange { it.copy(emptyReturnFromKm = v) } }

            NumberField(
                "How much of it you ride back",
                settings.emptyReturnFraction * 100.0,
                "%",
                0,
                enabled = settings.emptyReturnEnabled,
            ) { v -> onChange { it.copy(emptyReturnFraction = v / 100.0) } }
        }

        SectionCard(
            title = "Your plan",
            subtitle = "The fare on an offer is what the customer pays. On the commission " +
                "plan part of it never reaches you.",
        ) {
            ChipRow(
                options = EarningsPlan.entries.toList(),
                selected = settings.earningsPlan,
                label = { it.label },
                onSelect = { plan -> onChange { it.copy(earningsPlan = plan) } },
            )

            // Taxes and fees come off on either plan - that is the trap the
            // earnings plan sets, since "0% commission" reads as "the fare is
            // mine" when about a tenth of it is not.
            NumberField(
                "Taxes and other fees",
                settings.taxesAndFeesPercent,
                "% of the fare",
                2,
            ) { v -> onChange { it.copy(taxesAndFeesPercent = v) } }

            NumberField("Fixed fee per order", settings.perOrderFee, "₹", 2) { v ->
                onChange { it.copy(perOrderFee = v) }
            }

            if (settings.earningsPlan == EarningsPlan.COMMISSION) {
                NumberField("Commission", settings.commissionPercent, "% of the fare", 1) { v ->
                    onChange { it.copy(commissionPercent = v) }
                }
                NumberField(
                    "Fare that carries no commission",
                    settings.commissionExemptAmount,
                    "₹",
                    2,
                ) { v -> onChange { it.copy(commissionExemptAmount = v) } }
                NumberField("GST on that commission", settings.gstOnCommissionPercent, "%", 1) { v ->
                    onChange { it.copy(gstOnCommissionPercent = v) }
                }
                Text(
                    "Leave GST at 0 if your taxes line above already came off a payout " +
                        "screen - Rapido bills the GST inside that line, so entering it " +
                        "here as well counts it twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabelledValue(
                    "Commission on a ₹70 fare",
                    Format.rupees2(settings.commissionOn(70.0)),
                )
            } else {
                NumberField("Plan cost", settings.dailyPlanFee, "₹ per day", 0) { v ->
                    onChange { it.copy(dailyPlanFee = v) }
                }
                Text(
                    "Not taken off individual offers - a day's fee is spent whichever " +
                        "order you take next, so it belongs to the question of whether " +
                        "to go out, not to this offer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SwitchRow(
                label = "Parcel orders pay no tax",
                checked = settings.parcelOrdersExempt,
                description = "On Rapido's payout screens a parcel order's fare and its " +
                    "earning are the same number. Taxes and the flat fee are skipped; " +
                    "commission, if your plan charges one, is still taken.",
            ) { on -> onChange { it.copy(parcelOrdersExempt = on) } }

            // Checkable against any completed order's payment breakdown.
            LabelledValue(
                "On a ₹70 fare",
                "${Format.rupees2(settings.deductionOn(70.0))} kept, " +
                    "${Format.rupees2(70.0 - settings.deductionOn(70.0))} yours",
            )
        }

        SectionCard(
            title = "Maintenance",
            subtitle = "Off by default. With it off, net earning means fare minus fuel and " +
                "the platform's cut, and nothing else is claimed.",
        ) {
            SwitchRow("Maintenance cost", settings.maintenanceEnabled) { on ->
                onChange { it.copy(maintenanceEnabled = on) }
            }
            NumberField(
                "Maintenance",
                settings.maintenancePerKm,
                "₹/km",
                2,
                enabled = settings.maintenanceEnabled,
            ) { v -> onChange { it.copy(maintenancePerKm = v) } }
        }

        SectionCard(title = "Overlay") {
            SwitchRow("Show the floating card", settings.overlayEnabled) { on ->
                onChange { it.copy(overlayEnabled = on) }
            }
            Text("Mode", style = MaterialTheme.typography.bodySmall)
            ChipRow(
                options = OverlayMode.entries.toList(),
                selected = settings.overlayMode,
                label = { it.label },
                onSelect = { mode -> onChange { it.copy(overlayMode = mode) } },
            )
            SwitchRow(
                label = "Show fare, distance and time",
                checked = settings.overlayShowDetailsInQuickMode,
            ) { on -> onChange { it.copy(overlayShowDetailsInQuickMode = on) } }

            Text("Card size", style = MaterialTheme.typography.bodySmall)
            ChipRow(
                options = listOf(0.9f, 1.0f, 1.25f, 1.5f, 1.8f),
                selected = listOf(0.9f, 1.0f, 1.25f, 1.5f, 1.8f)
                    .minByOrNull { kotlin.math.abs(it - settings.overlayTextScale) },
                label = { scale ->
                    when (scale) {
                        0.9f -> "Small"
                        1.0f -> "Normal"
                        1.25f -> "Large"
                        1.5f -> "Larger"
                        else -> "Biggest"
                    }
                },
                onSelect = { scale -> onChange { it.copy(overlayTextScale = scale) } },
            )
            Text(
                "The net rupees per hour is the biggest thing on the card - it is what " +
                    "the decision is made on, and what you have time to read at a glance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberField("Hide the card after", settings.overlayAutoHideMillis / 1000.0, "seconds", 0) { v ->
                onChange { it.copy(overlayAutoHideMillis = (v * 1000).toLong()) }
            }
            SwitchRow(
                label = "Notify on the lock screen",
                checked = settings.lockScreenNoticeEnabled,
                description = "Android hides floating cards behind the lock screen, for every " +
                    "app. A silent notification gets the verdict through instead.",
            ) { on -> onChange { it.copy(lockScreenNoticeEnabled = on) } }
            Text(
                "Tap the card to expand it, drag it anywhere. It never covers the " +
                    "Accept button, and it never taps anything for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Voice") {
            SwitchRow(
                label = "Speak the decision",
                checked = settings.voiceEnabled,
                description = "\"Good order, 180 net per hour.\"",
            ) { on -> onChange { it.copy(voiceEnabled = on) } }
            NumberField(
                "Minimum gap between announcements",
                settings.voiceMinIntervalMillis / 1000.0,
                "seconds",
                0,
                enabled = settings.voiceEnabled,
            ) { v -> onChange { it.copy(voiceMinIntervalMillis = (v * 1000).toLong()) } }
            OutlinedButton(onClick = onPreviewVoice, enabled = settings.voiceEnabled) {
                Text("Test the voice")
            }
        }

        SectionCard(title = "Apps to watch") {
            ChipRow(
                options = AppMode.entries.toList(),
                selected = settings.appMode,
                label = { it.label },
                onSelect = { mode -> onChange { it.copy(appMode = mode) } },
            )
            Text(
                "RideScore only reads the screen of the app you pick here. Anything " +
                    "else in the foreground is ignored without being read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "OCR fallback",
            subtitle = "Only used when the accessibility text is missing something. " +
                "Needs Android's screen capture permission, and shows Android's own " +
                "recording indicator while it is on.",
        ) {
            SwitchRow("Read the screen with OCR when text is unavailable", settings.ocrFallbackEnabled) { on ->
                onOcrToggle(on)
            }
        }

        SectionCard(
            title = "Ride log",
            subtitle = "Writes every offer it sees to a spreadsheet file on this phone - " +
                "fare, distance, time, rates, decision, and the time of day. Nothing is " +
                "uploaded; the file goes nowhere until you share it yourself.",
        ) {
            SwitchRow(
                label = "Keep a log of offers",
                checked = settings.offerLogEnabled,
                description = "The only thing RideScore writes to disk.",
            ) { on -> onChange { it.copy(offerLogEnabled = on) } }

            if (logStats.isEmpty) {
                Text(
                    if (settings.offerLogEnabled) "No offers logged yet." else "Logging is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LabelledValue("Offers logged", logStats.rows.toString())
                LabelledValue("File size", "${logStats.bytes / 1024} KB")
                logStats.firstEntry?.let { LabelledValue("First", it) }
                logStats.lastEntry?.let { LabelledValue("Latest", it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShareLog, enabled = !logStats.isEmpty) {
                    Text("Export")
                }
                OutlinedButton(onClick = onClearLog, enabled = !logStats.isEmpty) {
                    Text("Delete log")
                }
            }
        }

        SectionCard(
            title = "Confidence",
            subtitle = "A read below the accept threshold is never shown as ACCEPT. " +
                "A read below the usable floor is shown as CHECK.",
        ) {
            NumberField("Accept needs confidence above", settings.lowConfidenceThreshold * 100.0, "%", 0) { v ->
                onChange { it.copy(lowConfidenceThreshold = (v / 100.0).toFloat()) }
            }
            NumberField("Usable floor", settings.minUsableConfidence * 100.0, "%", 0) { v ->
                onChange { it.copy(minUsableConfidence = (v / 100.0).toFloat()) }
            }
        }

        SectionCard(
            title = "Destinations",
            subtitle = "RideScore shows the destination it read. It does not claim an " +
                "area is in high demand, because it has no demand data.",
        ) {
            Text(
                if (settings.preferredDestinations.isEmpty()) "No preferred areas set"
                else settings.preferredDestinations.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(Modifier.padding(16.dp)) {
            OutlinedButton(onClick = onReset) { Text("Reset everything to defaults") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
