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
            title = "Pickup leg",
            subtitle = "When the app does not show a pickup time, RideScore estimates it " +
                "from this speed and rounds up to the next whole minute.",
        ) {
            NumberField("Pickup speed", settings.pickupSpeedKmph, "km/h", 1) { v ->
                onChange { it.copy(pickupSpeedKmph = v) }
            }
            SwitchRow("Include pickup distance", settings.includePickupDistance) { on ->
                onChange { it.copy(includePickupDistance = on) }
            }
            SwitchRow("Include pickup time", settings.includePickupTime) { on ->
                onChange { it.copy(includePickupTime = on) }
            }
        }

        SectionCard(
            title = "Optional costs",
            subtitle = "Off by default. With both off, net earning means fare minus fuel, " +
                "and nothing else is claimed.",
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

            SwitchRow("Platform fee / commission", settings.platformFeeEnabled) { on ->
                onChange { it.copy(platformFeeEnabled = on) }
            }
            NumberField(
                "Platform fee",
                settings.platformFeePercent,
                "% of fare",
                1,
                enabled = settings.platformFeeEnabled,
            ) { v -> onChange { it.copy(platformFeePercent = v) } }
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
                label = "Show distance and time in quick mode",
                checked = settings.overlayShowDetailsInQuickMode,
            ) { on -> onChange { it.copy(overlayShowDetailsInQuickMode = on) } }
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
