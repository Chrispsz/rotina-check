package com.chrispsz.rotinacheck.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.chrispsz.rotinacheck.R
import com.chrispsz.rotinacheck.data.RoutineBlock
import com.chrispsz.rotinacheck.data.RoutineDay
import com.chrispsz.rotinacheck.data.RoutineStore
import com.chrispsz.rotinacheck.data.TimeRange
import com.chrispsz.rotinacheck.viewmodel.RoutineViewModel
import com.chrispsz.rotinacheck.viewmodel.UiState
import java.time.LocalTime

/**
 * Main checklist screen: mode switch → daily progress → 7-day consistency →
 * collapsible block cards. All math comes pre-computed from [UiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ui: UiState,
    vm: RoutineViewModel,
    onImport: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (ui.routine != null) {
                FloatingActionButton(
                    onClick = { vm.newBlock() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.fab_new_block_a11y))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (ui.pomodoroUrl.startsWith("http")) {
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(ui.pomodoroUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) {
                            Icon(
                                Icons.Outlined.Timer,
                                contentDescription = stringResource(R.string.topbar_pomodoro_a11y),
                            )
                        }
                    }
                    IconButton(onClick = { vm.openSettings() }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.topbar_settings_a11y),
                        )
                    }
                },
            )

            if (ui.routine == null || ui.activeDay == null) {
                EmptyState(onImport)
            } else {
                ChecklistContent(ui = ui, vm = vm)
            }
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.EventNote,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) {
            Text(stringResource(R.string.action_import))
        }
    }
}

@Composable
private fun ChecklistContent(ui: UiState, vm: RoutineViewModel) {
    val day: RoutineDay = ui.activeDay!!

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !ui.weekendActive,
                    onClick = { vm.setMode(RoutineStore.MODE_WEEK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.mode_week)) }
                SegmentedButton(
                    selected = ui.weekendActive,
                    onClick = { vm.setMode(RoutineStore.MODE_WEEKEND) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.mode_weekend)) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ui.percent}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                LinearProgressIndicator(
                    progress = { ui.percent / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
        item {
            ConsistencyRow(ui = ui, onReset = { vm.resetHistory() })
        }
        items(day.blocos, key = { it.id }) { block ->
            BlockCard(
                block = block,
                dayId = day.id,
                dayKey = ui.todayKey,
                checks = ui.checks,
                vm = vm,
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ConsistencyRow(ui: UiState, onReset: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.consistency_label, ui.completedLast7),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        ui.consistency.forEach { day ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            day.complete -> MaterialTheme.colorScheme.secondary
                            day.active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onReset) {
            Icon(
                Icons.Outlined.RestartAlt,
                contentDescription = stringResource(R.string.consistency_reset_a11y),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockCard(
    block: RoutineBlock,
    dayId: String,
    dayKey: String,
    checks: Set<String>,
    vm: RoutineViewModel,
) {
    var expanded by remember(block.id) { mutableStateOf(false) }
    val nowMin = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val isNow = block.isCurrent(nowMin)
    val timeLabel = block.timeRange?.format() ?: stringResource(R.string.no_time)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(block.nome, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.block_tasks_count, block.tarefas.size, timeLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isNow) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.block_open_now),
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = { vm.openEditor(block.id) }) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.block_edit_a11y),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.block_toggle_a11y),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 4.dp, end = 16.dp, bottom = 12.dp)) {
                    block.tarefas.forEachIndexed { index, task ->
                        val checked = checks.contains(com.chrispsz.rotinacheck.data.Progress.key(dayKey, dayId, block.id, index))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { vm.toggleTask(dayId, block.id, index) },
                            )
                            Text(
                                task,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (checked) TextDecoration.LineThrough else null,
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (block.tarefas.isEmpty()) {
                        Text(
                            stringResource(R.string.editor_tasks_label),
                            Modifier.padding(start = 16.dp, top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
