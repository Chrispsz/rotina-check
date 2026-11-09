package com.chrispsz.rotinacheck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 *  - "Ignorar esta versão" (expand →) mutes that exact version for good;
 *  - no dialogs, no sounds, no red badges, no "update now!" pressure.
 */
@Composable
fun UpdateBanner(
    state: GitHubSmartUpdate.State,
    onUpdate: () -> Unit,
    onIgnore: () -> Unit,
    onHide: () -> Unit,
) {
    val info = (state as? GitHubSmartUpdate.State.Available)?.info

    // Keep the last known info so the exit animation doesn't blink empty.
    var shown by remember { mutableStateOf<UpdateInfo?>(null) }
    if (info != null) shown = info

    AnimatedVisibility(
        visible = info != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val current = shown ?: return@AnimatedVisibility
        var expanded by remember(current.version) { mutableStateOf(false) }

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
                Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
                            stringResource(R.string.update_title, current.version),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (!expanded && current.notes.isNotBlank()) {
                            Text(
                                current.notes,
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
                        if (current.notes.isNotBlank()) {
                            Text(
                                current.notes,
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
}
