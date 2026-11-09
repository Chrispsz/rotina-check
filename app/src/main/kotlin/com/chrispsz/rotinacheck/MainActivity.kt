package com.chrispsz.rotinacheck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrispsz.rotinacheck.ui.screens.EditorScreen
import com.chrispsz.rotinacheck.ui.screens.HomeScreen
import com.chrispsz.rotinacheck.ui.screens.SettingsScreen
import com.chrispsz.rotinacheck.ui.theme.RotinaCheckTheme
import com.chrispsz.rotinacheck.viewmodel.RoutineViewModel
import com.chrispsz.rotinacheck.viewmodel.Screen
import com.chrispsz.rotinacheck.viewmodel.UpdateViewModel

/**
 * Single-activity Compose host. POST_NOTIFICATIONS is requested at first
 * launch (minSdk 34 → always runtime); a denial degrades gracefully — the
 * UI banner in Settings stays visible and alerts simply don't show.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* UI degrades gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            RotinaCheckTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(vm: RoutineViewModel = viewModel(), update: UpdateViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- OTA trigger ----------------------------------------------------------
    // One attempt per foreground entry; the smart gate inside the updater
    // makes this effectively free (usually a single DataStore read, network
    // only once a day). Both calls below are idempotent for the same day.
    LaunchedEffect(update) { update.onAppOpened() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, update) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) update.onAppOpened()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // SAF: import reads any JSON-ish mime (file managers disagree on the
    // exact type); export suggests a default backup file name.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFromUri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportToUri)
    }

    BackHandler(enabled = ui.screen != Screen.HOME) { vm.back() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = {
            // The OUTER scaffold owns only the snackbar; content insets are
            // handled by each screen's own scaffold/top bar. Applying the
            // outer padding too would double the status-bar gap.
            SnackbarHost(
                snackbarHostState,
                Modifier.navigationBarsPadding(),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // contentWindowInsets = zero → padding is empty by design; each screen
        // applies its own status-bar/navigation-bar insets (no double gap).
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when (ui.screen) {
                Screen.HOME -> HomeScreen(
                    ui = ui,
                    vm = vm,
                    update = update,
                    onImport = {
                        importLauncher.launch(
                            arrayOf("application/json", "application/octet-stream", "text/*"),
                        )
                    },
                )
                Screen.SETTINGS -> SettingsScreen(
                    ui = ui,
                    vm = vm,
                    update = update,
                    onImport = {
                        importLauncher.launch(
                            arrayOf("application/json", "application/octet-stream", "text/*"),
                        )
                    },
                    onExport = { exportLauncher.launch("rotinacheck-backup.json") },
                )
                Screen.EDITOR -> EditorScreen(ui = ui, vm = vm)
            }
        }
    }
}
