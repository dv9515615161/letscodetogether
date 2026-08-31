package com.ridescore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridescore.app.domain.settings.EarningsPlan
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.util.Format

/**
 * The two numbers everything else depends on.
 *
 * Shipping with one bike's defaults is fine for the person who wrote the app
 * and wrong for everyone else: a Honda Activa does over 50 km/L against a
 * Pulsar's 37.5, and petrol prices differ by city and by month. Get these wrong
 * and every verdict is wrong without ever looking wrong, so the app asks once
 * before it is used rather than hoping the driver finds Settings.
 */
@Composable
fun SetupScreen(
    settings: RideScoreSettings,
    onChange: ((RideScoreSettings) -> RideScoreSettings) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Your bike and your petrol",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Every figure RideScore shows is built on these two numbers. They take " +
                "a moment now and you can change them any time in Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How far does your bike go on a litre?", fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = RideScoreSettings.MILEAGE_PRESETS,
                selected = RideScoreSettings.MILEAGE_PRESETS
                    .firstOrNull { it == settings.mileageKmPerLitre },
                label = { "${Format.decimal(it, 1)} km/L" },
                onSelect = { preset -> onChange { it.copy(mileageKmPerLitre = preset) } },
            )
            NumberField("Mileage", settings.mileageKmPerLitre, "km/L", 1) { v ->
                onChange { it.copy(mileageKmPerLitre = v) }
            }
            Text(
                "Not sure? Use what the bike actually does in city traffic, not the " +
                    "showroom figure - it is usually lower.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What are you paying for petrol?", fontWeight = FontWeight.SemiBold)
            NumberField("Petrol price", settings.petrolPricePerLitre, "₹ per litre", 2) { v ->
                onChange { it.copy(petrolPricePerLitre = v) }
            }
        }

        Text(
            "That works out at ${Format.rupees2(settings.fuelCostPerKm)} of petrol " +
                "for every kilometre you ride.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Which Rapido plan are you on?", fontWeight = FontWeight.SemiBold)
            Text(
                "The fare on an offer is what the customer pays. On the commission plan " +
                    "part of it never reaches you, so RideScore has to know which plan " +
                    "you are on or its figures will be too high. Your rate card in Rapido " +
                    "shows it under \"Rapido's Commission\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                options = EarningsPlan.entries.toList(),
                selected = settings.earningsPlan,
                label = { it.label },
                onSelect = { plan -> onChange { it.copy(earningsPlan = plan) } },
            )

            if (settings.earningsPlan == EarningsPlan.COMMISSION) {
                NumberField("Commission", settings.commissionPercent, "% of the fare", 1) { v ->
                    onChange { it.copy(commissionPercent = v) }
                }
                NumberField("GST on that commission", settings.gstOnCommissionPercent, "%", 1) { v ->
                    onChange { it.copy(gstOnCommissionPercent = v) }
                }
                Text(
                    "Rapido keeps ${Format.decimal(settings.effectiveCommissionPercent, 2)}% " +
                        "of each fare on those numbers.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                NumberField("What the plan costs you", settings.dailyPlanFee, "₹ per day", 0) { v ->
                    onChange { it.copy(dailyPlanFee = v) }
                }
                Text(
                    "This is not taken off individual offers. Once you have paid for the " +
                        "day it is spent whichever order you take next, so it does not " +
                        "change whether this one is worth it - it only changes whether " +
                        "the day was.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        Spacer(Modifier.height(24.dp))
    }
}
