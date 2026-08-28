package com.ridescore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.ui.theme.AcceptGreen
import com.ridescore.app.ui.theme.CheckGrey
import com.ridescore.app.ui.theme.MaybeAmber
import com.ridescore.app.ui.theme.RejectRed
import com.ridescore.app.util.Diagnostics
import com.ridescore.app.util.Format

data class PermissionState(
    val accessibilityEnabled: Boolean,
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
)

@Composable
fun HomeScreen(
    settings: RideScoreSettings,
    permissions: PermissionState,
    diagnostics: Diagnostics.State,
    sample: ScreenAnalysis?,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRunSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionCard(
            title = "Setup",
            subtitle = "RideScore reads the offer screen and tells you what it is worth. " +
                "It never taps, accepts or declines anything.",
        ) {
            PermissionRow(
                label = "Accessibility service",
                granted = permissions.accessibilityEnabled,
                actionLabel = "Open settings",
                onClick = onOpenAccessibilitySettings,
                description = "Lets RideScore read the offer text in Rapido and Uber.",
            )
            PermissionRow(
                label = "Draw over other apps",
                granted = permissions.overlayGranted,
                actionLabel = "Grant",
                onClick = onRequestOverlay,
                description = "Needed for the floating decision card.",
            )
            PermissionRow(
                label = "Notifications",
                granted = permissions.notificationsGranted,
                actionLabel = "Grant",
                onClick = onRequestNotifications,
                description = "Only used by the OCR fallback's required foreground service.",
            )
        }

        SectionCard(
            title = "Your rules",
            subtitle = settings.vehicleName,
        ) {
            LabelledValue("Mileage", "${Format.decimal(settings.mileageKmPerLitre, 1)} km/L")
            LabelledValue("Petrol", "${Format.rupees(settings.petrolPricePerLitre)}/L")
            LabelledValue("Fuel cost", "${Format.rupees2(settings.fuelCostPerKm)}/km")
            LabelledValue("Accept", "${Format.rupeesRounded(settings.acceptNetPerHour)} net/hr")
            LabelledValue("Maybe", "${Format.rupeesRounded(settings.maybeNetPerHour)} net/hr")
            LabelledValue("Min per km", "${Format.rupees2(settings.minNetPerKm)} net/km")
            LabelledValue("Watching", settings.appMode.label)
        }

        SectionCard(
            title = "Try it",
            subtitle = "Runs the sample Rapido offer (₹45 + ₹15, 1.8 km pickup, 5.9 km trip, " +
                "12 min) through the same engine that runs on a real screen.",
        ) {
            Button(onClick = onRunSample) { Text("Analyse the sample offer") }
            if (sample != null) {
                Spacer(Modifier.height(8.dp))
                sample.ranked.forEachIndexed { index, analysis ->
                    Text(
                        "${analysis.decision.emoji} ${if (sample.hasMultipleOffers) "#${index + 1} " else ""}" +
                            "${Format.rupeesRounded(analysis.grossEarning)} · " +
                            "${Format.decimal(analysis.totalDistanceKm)} km · " +
                            Format.minutes(analysis.totalTimeMinutes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = decisionColor(analysis.decision),
                    )
                    Text(
                        "gross ${Format.rupeesRounded(analysis.grossPerHour)}/hr · " +
                            "fuel ${Format.rupees2(analysis.fuelCost)} · " +
                            "net ${Format.rupees2(analysis.netEarning)} · " +
                            "${Format.rupeesRounded(analysis.netPerHour)} net/hr · " +
                            "${Format.rupees2(analysis.netPerKm)} net/km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (sample.noGoodOrder) {
                    Text(
                        "🔴 NO GOOD ORDER",
                        style = MaterialTheme.typography.titleMedium,
                        color = RejectRed,
                    )
                }
            }
        }

        SectionCard(title = "Status") {
            LabelledValue("Service", if (diagnostics.serviceConnected) "connected" else "not running")
            LabelledValue(
                "Foreground app",
                diagnostics.lastForegroundPackage?.let {
                    if (diagnostics.lastForegroundSupported) "$it ✓" else "$it (ignored)"
                } ?: "none yet",
            )
            LabelledValue("Offers on last screen", diagnostics.lastOfferCount.toString())
            LabelledValue("Last decision", diagnostics.lastDecision ?: "-")
            LabelledValue(
                "Read confidence",
                if (diagnostics.lastConfidence > 0f) Format.percent(diagnostics.lastConfidence) else "-",
            )
            if (diagnostics.overlayPermissionMissing) {
                Text(
                    "The overlay could not be shown. Grant \"draw over other apps\" above.",
                    color = RejectRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard(
            title = "Last screen read",
            subtitle = "What RideScore saw on the driver app, and how it split it into " +
                "offer cards. Useful when the numbers look wrong. Kept in memory only - " +
                "it is never saved or sent anywhere.",
        ) {
            var expanded by remember { mutableStateOf(false) }
            val clipboard = LocalClipboardManager.current

            LabelledValue("Offer cards detected", diagnostics.lastBlocks.size.toString())
            LabelledValue("Lines read", diagnostics.lastLines.size.toString())

            if (diagnostics.lastLines.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide" else "Show")
                    }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(diagnosticsReport(diagnostics))) },
                    ) { Text("Copy") }
                }
            }

            if (expanded) {
                Text(
                    diagnosticsReport(diagnostics),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        SectionCard(title = "Safety and privacy") {
            Text(
                "• Advisory only. RideScore never accepts, declines, taps, swipes or " +
                    "scrolls anything in Rapido or Uber.\n" +
                    "• Look at the card before you accept, when you are stopped. Never " +
                    "while riding.\n" +
                    "• Everything is calculated on this phone. No screenshots, no " +
                    "location, no ride data and no driver data leave the device.\n" +
                    "• No accounts, no cloud, no analytics.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The last screen, as text a driver can copy and send to whoever maintains the parsers. */
private fun diagnosticsReport(state: Diagnostics.State): String = buildString {
    append("app: ").append(state.lastForegroundPackage ?: "none").append('\n')
    append("offer cards detected: ").append(state.lastBlocks.size).append('\n')

    state.lastOfferSummaries.forEachIndexed { i, summary ->
        append("parsed #").append(i + 1).append(": ").append(summary).append('\n')
    }

    if (state.lastBlocks.isEmpty()) {
        append('\n').append("--- screen text ---").append('\n')
        state.lastLines.forEach { append(it).append('\n') }
    } else {
        state.lastBlocks.forEachIndexed { i, block ->
            append('\n').append("--- card ").append(i + 1).append(" ---").append('\n')
            block.forEach { append(it).append('\n') }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                "${if (granted) "✓" else "•"} $label",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (granted) AcceptGreen else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onClick) { Text(actionLabel) }
        }
    }
}

private fun decisionColor(decision: Decision): Color = when (decision) {
    Decision.ACCEPT -> AcceptGreen
    Decision.MAYBE -> MaybeAmber
    Decision.REJECT -> RejectRed
    Decision.CHECK -> CheckGrey
}
