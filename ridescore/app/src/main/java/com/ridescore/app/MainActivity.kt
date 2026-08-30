package com.ridescore.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridescore.app.accessibility.RideScoreAccessibilityService
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.engine.RideScoreEngine
import com.ridescore.app.ocr.ScreenCaptureService
import com.ridescore.app.tts.VoiceAnnouncer
import com.ridescore.app.ui.DisclosureScreen
import com.ridescore.app.ui.HomeScreen
import com.ridescore.app.ui.PermissionState
import com.ridescore.app.ui.SampleOffers
import com.ridescore.app.ui.SettingsScreen
import com.ridescore.app.ui.SettingsViewModel
import com.ridescore.app.ui.theme.RideScoreTheme
import com.ridescore.app.util.Diagnostics
import kotlinx.coroutines.launch

/**
 * The app's own UI: permissions, settings, and a sample offer to try.
 *
 * Nothing here reads another app. The screen reading lives entirely in
 * [RideScoreAccessibilityService], which only runs once the driver has turned
 * it on in Android's own settings.
 */
class MainActivity : ComponentActivity() {

    private val engine = RideScoreEngine()
    private var permissions = mutableStateOf(PermissionState(false, false, false))
    private var voice: VoiceAnnouncer? = null

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            ScreenCaptureService.start(this, result.resultCode, data)
            pendingOcrEnable?.invoke(true)
        } else {
            pendingOcrEnable?.invoke(false)
        }
        pendingOcrEnable = null
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissions() }

    private var pendingOcrEnable: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissions()

        setContent {
            RideScoreTheme {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
                val settings by viewModel.settings.collectAsState()
                val diagnostics by Diagnostics.state.collectAsState()
                var tab by remember { mutableIntStateOf(0) }
                var sample by remember { mutableStateOf<ScreenAnalysis?>(null) }
                val logStats by viewModel.logStats.collectAsState()
                val scope = rememberCoroutineScope()

                LaunchedEffect(tab, settings.offerLogEnabled) { viewModel.refreshLogStats() }

                // Play requires the accessibility disclosure to be accepted
                // before the permission is offered, and the app should not be
                // usable around it.
                if (!settings.disclosureAccepted) {
                    DisclosureScreen(
                        onAccept = { viewModel.update { it.copy(disclosureAccepted = true) } },
                        onOpenPrivacyPolicy = { openPrivacyPolicy() },
                    )
                    return@RideScoreTheme
                }

                RideScoreScaffold(
                    tab = tab,
                    onTabChange = { tab = it },
                ) { modifier ->
                    when (tab) {
                        0 -> HomeScreen(
                            settings = settings,
                            onChange = { transform -> viewModel.update(transform) },
                            permissions = permissions.value,
                            diagnostics = diagnostics,
                            sample = sample,
                            onOpenAccessibilitySettings = { openAccessibilitySettings() },
                            onRequestOverlay = { requestOverlayPermission() },
                            onRequestNotifications = { requestNotificationPermission() },
                            onRunSample = {
                                val analysis = engine.analyse(SampleOffers.RAPIDO_SINGLE, settings)
                                sample = analysis
                                RideScoreAccessibilityService.instance?.preview(analysis)
                            },
                            modifier = modifier,
                        )
                        else -> SettingsScreen(
                            logStats = logStats,
                            onShareLog = {
                                scope.launch {
                                    val intent = viewModel.shareLogIntent()
                                    if (intent == null) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Nothing logged yet",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        startActivity(Intent.createChooser(intent, "Send ride log"))
                                    }
                                }
                            },
                            onClearLog = { viewModel.clearLog() },
                            settings = settings,
                            onChange = { transform -> viewModel.update(transform) },
                            onReset = { viewModel.resetToDefaults() },
                            onPreviewVoice = { previewVoice() },
                            onOcrToggle = { enabled -> toggleOcr(enabled) { on -> viewModel.update { it.copy(ocrFallbackEnabled = on) } } },
                            modifier = modifier,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onDestroy() {
        voice?.shutdown()
        voice = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ permissions

    private fun refreshPermissions() {
        permissions.value = PermissionState(
            accessibilityEnabled = isAccessibilityServiceEnabled(this),
            overlayGranted = Settings.canDrawOverlays(this),
            notificationsGranted = hasNotificationPermission(),
        )
        Diagnostics.update { it.copy(serviceConnected = RideScoreAccessibilityService.isRunning) }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ------------------------------------------------------------------- OCR

    private fun toggleOcr(enabled: Boolean, commit: (Boolean) -> Unit) {
        if (!enabled) {
            ScreenCaptureService.stop(this)
            commit(false)
            return
        }
        // Android requires an explicit, per-session consent for screen capture.
        // There is no way to skip this, and RideScore does not try to.
        pendingOcrEnable = commit
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun openPrivacyPolicy() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        }.onFailure {
            Toast.makeText(this, PRIVACY_POLICY_URL, Toast.LENGTH_LONG).show()
        }
    }

    private fun previewVoice() {
        val announcer = voice ?: VoiceAnnouncer(applicationContext).also { voice = it }
        announcer.preview()
    }

    companion object {
        /** Hosted from the repository's docs/ directory via GitHub Pages. */
        const val PRIVACY_POLICY_URL = "https://dv9515615161.github.io/letscodetogether/privacy.html"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${RideScoreAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RideScoreScaffold(
    tab: Int,
    onTabChange: (Int) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("RideScore") })
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { onTabChange(0) }, text = { Text("Home") })
                Tab(selected = tab == 1, onClick = { onTabChange(1) }, text = { Text("Settings") })
            }
            content(Modifier)
        }
    }
}
