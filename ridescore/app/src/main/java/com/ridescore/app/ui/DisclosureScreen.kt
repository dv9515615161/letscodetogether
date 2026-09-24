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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * What RideScore reads, and what it will never do with it.
 *
 * Google Play requires an app to disclose accessibility use, in the app, before
 * asking for the permission - and to have the user accept it. This screen is
 * that disclosure. It is also simply the honest thing to show someone about to
 * grant a permission that can read screens.
 *
 * Written to be read by a driver, not a lawyer: short sentences, the two things
 * that matter first (what is read, where it goes), and every promise on this
 * screen enforced somewhere in the code rather than only asserted here.
 */
@Composable
fun DisclosureScreen(
    onAccept: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Before you turn RideScore on",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Section(
            "What it reads",
            "RideScore uses Android's accessibility service to read the text of ride " +
                "offers in Rapido Captain and Uber Driver: the fare, the distances, the " +
                "times, and the pickup and drop addresses.\n\n" +
                "It reads those two apps and nothing else. The app in front is checked " +
                "before any screen content is requested, so another app's screen is " +
                "never read at all.",
        )

        Section(
            "Where it goes",
            "Nowhere. Every calculation runs on this phone. RideScore does not have " +
                "internet permission, so it cannot send anything anywhere even if it " +
                "tried. There are no accounts, no servers and no analytics.\n\n" +
                "If you switch the ride log on, offers are saved to a file on this phone " +
                "so you can look at patterns later. That file stays here unless you " +
                "share it yourself, and you can delete it whenever you like.",
        )

        Section(
            "What it will never do",
            "RideScore never accepts, declines, taps, swipes or scrolls anything in " +
                "Rapido or Uber. It cannot: the accessibility service is declared " +
                "without the ability to perform actions in other apps.\n\n" +
                "It shows you what an offer is worth. Every decision stays yours.",
        )

        Section(
            "Read it when it is safe to",
            "The card is meant to be read before you accept, while you are stopped. " +
                "Turn the voice option on and you do not need to look at the phone at " +
                "all. Never read it while you are riding.",
        )

        TextButton(onClick = onOpenPrivacyPolicy) { Text("Read the full privacy policy") }

        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("I understand - continue")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
