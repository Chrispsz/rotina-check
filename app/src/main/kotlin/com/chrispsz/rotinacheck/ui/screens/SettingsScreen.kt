package com.chrispsz.rotinacheck.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrispsz.rotinacheck.R
import com.chrispsz.rotinacheck.update.GitHubSmartUpdate
import com.chrispsz.rotinacheck.viewmodel.RoutineViewModel
import com.chrispsz.rotinacheck.viewmodel.UiState
import com.chrispsz.rotinacheck.viewmodel.UpdateViewModel
import kotlinx.coroutines.delay

/**
 * Settings: import/export (SAF), alerts toggle, restore the bundled example
 * routine, factory reset. Permission banners surface ONLY when the OS state
 * requires action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    ui: UiState,
    vm: RoutineViewModel,
    update: UpdateViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    // ---- Atualizações (OTA) ----------------------------------------------------
    val updateState by update.state.collectAsStateWithLifecycle()
    val updateFeedback by update.feedback.collectAsStateWithLifecycle()
    var checkStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(updateFeedback) {
        when (updateFeedback) {
            GitHubSmartUpdate.Feedback.UP_TO_DATE ->
                checkStatus = context.getString(R.string.settings_updates_up_to_date)
            GitHubSmartUpdate.Feedback.CHECK_FAILED ->
                checkStatus = context.getString(R.string.settings_updates_failed)
            null -> Unit
        }
        if (updateFeedback != null) {
            delay(4_000)
            update.consumeFeedback()
            checkStatus = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = { vm.openHome() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.topbar_back_a11y),
                    )
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!ui.canExactAlarms) {
                BannerCard {
                    Text(
                        stringResource(R.string.banner_exact),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { vm.openExactAlarmSettings() }) {
                        Text(stringResource(R.string.banner_exact_action))
                    }
                }
            }

            val notificationsGranted = remember {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
            if (!notificationsGranted) {
                BannerCard(
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.banner_notifications),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_alerts),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.settings_alerts_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = ui.alertsOn, onCheckedChange = { vm.setAlerts(it) })
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_example_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.settings_example_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.restoreExample() }) {
                        Text(stringResource(R.string.settings_example_action))
                    }
                }
            }

            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_import))
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_export))
            }

            // ---- Atualizações (OTA leve via GitHub Releases) ------------------------
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_updates_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.settings_updates_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_version_installed, update.installedVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val st = updateState) {
                        is GitHubSmartUpdate.State.Checking -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.settings_updates_checking),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is GitHubSmartUpdate.State.Available -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.settings_updates_available, st.info.version),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (st.info.notes.isNotBlank()) {
                                Text(
                                    st.info.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // One tap: system download → installer. No browser.
                            Button(onClick = update::downloadUpdate, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.settings_updates_download))
                            }
                            TextButton(
                                onClick = update::ignoreCurrent,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(stringResource(R.string.update_ignore_version))
                            }
                        }
                        is GitHubSmartUpdate.State.Downloading -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.update_downloading, st.percent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { st.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is GitHubSmartUpdate.State.ReadyToInstall -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.update_ready),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = update::installUpdate, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.update_install_now))
                            }
                        }
                        else -> Unit
                    }
                    checkStatus?.let { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = update::checkNow, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_updates_check_now))
                    }
                }
            }

            TextButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.settings_clear))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        vm.clearAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun BannerCard(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
