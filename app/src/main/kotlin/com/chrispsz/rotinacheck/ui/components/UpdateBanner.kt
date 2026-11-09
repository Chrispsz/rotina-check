package com.chrispsz.rotinacheck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chrispsz.rotinacheck.R
import com.chrispsz.rotinacheck.update.GitHubSmartUpdate
import com.chrispsz.rotinacheck.update.UpdateInfo

/**
 * The ENTIRE update UX for the Home screen: one quiet card under the top bar.
 *
 * Discretion contract:
 *  - appears only when a newer release was found (offline-resolved, no spinners);
 *  - ✕ hides it for the session — it quietly returns on the next open;
 *  - tap the card → expands to the full notes + "Ignorar esta versão";
 *  - "Atualizar" = ONE tap: system download with progress, then "Instalar
 *    agora" opens the platform installer. No browser, no file hunting;
 *  - no dialogs, no sounds, no red badges, no "update now!" pressure.
 */
@Composable
fun UpdateBanner(
    state: GitHubSmartUpdate.State,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onIgnore: () -> Unit,
    onHide: () -> Unit,
) {
    val available = state as? GitHubSmartUpdate.State.Available
    val downloading = state as? GitHubSmartUpdate.State.Downloading
    val ready = state as? GitHubSmartUpdate.State.ReadyToInstall

    // Keep the last known info so the exit animation doesn't blink empty.
    var shown by remember { mutableStateOf<UpdateInfo?>(null) }
    if (available != null) shown = available.info

    AnimatedVisibility(
        visible = available != null || downloading != null || ready != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        when {
            downloading != null -> DownloadingCard(downloading)
            ready != null -> ReadyCard(ready, onInstall)
            shown != null -> AvailableCard(shown!!, onUpdate, onIgnore, onHide)
        }
    }
}

@Composable
private fun DownloadingCard(state: GitHubSmartUpdate.State.Downloading) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = stringResource(R.string.update_icon_a11y),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.update_downloading, state.percent),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReadyCard(
    state: GitHubSmartUpdate.State.ReadyToInstall,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = stringResource(R.string.update_icon_a11y),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.update_ready, state.version),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_install_now))
            }
        }
    }
}

@Composable
private fun AvailableCard(
    info: UpdateInfo,
    onUpdate: () -> Unit,
    onIgnore: () -> Unit,
    onHide: () -> Unit,
) {
    // Tap the card to expand — fixes the original banner where the expand
    // state existed but nothing could ever toggle it.
    var expanded by remember(info.version) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = stringResource(R.string.update_icon_a11y),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.update_title, info.version),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!expanded && info.notes.isNotBlank()) {
                        Text(
                            info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onUpdate) {
                    Text(stringResource(R.string.update_action))
                }
                IconButton(
                    onClick = onHide,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.update_hide_a11y),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (info.notes.isNotBlank()) {
                        Text(
                            info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = onIgnore) {
                            Text(stringResource(R.string.update_ignore_version))
                        }
                    }
                }
            }
        }
    }
}
