package com.chrispsz.rotinacheck.ui.screens

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
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrispsz.rotinacheck.R
import com.chrispsz.rotinacheck.data.Progress
import com.chrispsz.rotinacheck.data.RoutineBlock
import com.chrispsz.rotinacheck.data.RoutineDay
import com.chrispsz.rotinacheck.ui.components.UpdateBanner
import com.chrispsz.rotinacheck.viewmodel.RoutineViewModel
import com.chrispsz.rotinacheck.viewmodel.UiState
import com.chrispsz.rotinacheck.viewmodel.UpdateViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** One shared, consistent corner radius for every card surface. */
private val CardRadius = RoundedCornerShape(20.dp)

private val DAY_SHORT = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
private val DAY_LONG = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

/**
 * Main screen, designed phone-first: a live "AGORA" hero card answers
 * "o que eu faço agora?" without scrolling; the day chips pick which weekday
 * is shown (blocks live on their own day); block cards expand on tap. One
 * shared 30 s clock feeds every time-dependent surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ui: UiState,
    vm: RoutineViewModel,
    update: UpdateViewModel,
    onImport: () -> Unit,
) {
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
                    IconButton(onClick = { vm.openSettings() }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.topbar_settings_a11y),
                        )
                    }
                },
            )

            // Discreet OTA card — only rendered when a newer release exists;
            // hides for the session with ✕, quietly returns next open.
            UpdateBanner(
                state = update.state.collectAsStateWithLifecycle().value,
                onUpdate = update::openDownload,
                onIgnore = update::ignoreCurrent,
                onHide = update::hideForSession,
            )

            if (ui.routine == null) {
                EmptyState(
                    onExample = { vm.restoreExample() },
                    onScratch = { vm.startFromScratch() },
                    onImport = onImport,
                )
            } else {
                ChecklistContent(ui = ui, vm = vm)
            }
        }
    }
}

/** Shared minute-of-day clock, refreshed every 30 s (hero + badges). */
@Composable
private fun rememberNowMin(): Int = produceState(
    initialValue = LocalTime.now().let { it.hour * 60 + it.minute },
) {
    while (true) {
        delay(30_000)
        value = LocalTime.now().let { it.hour * 60 + it.minute }
    }
}.value

@Composable
private fun EmptyState(
    onExample: () -> Unit,
    onScratch: () -> Unit,
    onImport: () -> Unit,
) {
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
        Button(onClick = onExample, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.empty_use_example))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onScratch, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.empty_start_scratch))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.empty_import))
        }
    }
}

@Composable
private fun ChecklistContent(ui: UiState, vm: RoutineViewModel) {
    val nowMin = rememberNowMin()
    val day: RoutineDay? = ui.activeDay

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DayChipsRow(
                selected = ui.selectedDate,
                today = LocalDate.now(),
                onSelect = { vm.selectDay(it.toString()) },
            )
        }
        if (!ui.isToday && day != null) {
            item {
                PreviewBanner(
                    dayName = day.nome,
                    onBackToToday = { vm.selectDay(null) },
                )
            }
        }
        item {
            if (day != null) {
                if (ui.isToday) {
                    NowHeroCard(day = day, nowMin = nowMin)
                } else {
                    PreviewHeroCard(day = day)
                }
            } else {
                MissingDayCard(dayName = DAY_LONG[ui.selectedDate.dayOfWeek.value - 1])
            }
        }
        if (ui.isToday) {
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
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        gapSize = 0.dp,
                        // No end-stop dot: a clean, continuous track.
                        drawStopIndicator = {},
                    )
                }
            }
            item {
                ConsistencyRow(ui = ui, onReset = { vm.resetHistory() })
            }
        }
        items(day?.blocos ?: emptyList(), key = { it.id }) { block ->
            BlockCard(
                block = block,
                dayId = day?.id ?: "",
                dayKey = ui.todayKey,
                checks = ui.checks,
                nowMin = nowMin,
                editable = ui.isToday,
                vm = vm,
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

/**
 * Seg–Dom chips for the current week. The selected chip is filled; a small
 * dot marks today. Selecting a day shows ITS blocks — each weekday keeps
 * its own routine.
 */
@Composable
private fun DayChipsRow(selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..6).forEach { i ->
            val date = monday.plusDays(i.toLong())
            val isSelected = date == selected
            val isToday = date == today
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .clickable { onSelect(date) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    DAY_SHORT[i],
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (isToday && !isSelected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 5.dp, end = 7.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBanner(dayName: String, onBackToToday: () -> Unit) {
    Surface(
        shape = CardRadius,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.view_other_day, dayName),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onBackToToday) {
                Text(stringResource(R.string.back_to_today))
            }
        }
    }
}

/**
 * Answers "o que eu faço agora?" at a glance: the block in progress (with a
 * countdown), or the next scheduled block, or a calm idle message.
 */
@Composable
private fun NowHeroCard(day: RoutineDay, nowMin: Int) {
    val current = day.blocos.firstOrNull { it.isCurrent(nowMin) }
    val next = day.blocos
        .filter { it.startMin != null && it.startMin > nowMin }
        .minByOrNull { it.startMin!! }
    val block = current ?: next
    val dayName = day.nome

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(
            containerColor = if (current != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (block != null) {
                    val verb = stringResource(
                        if (current != null) R.string.hero_now else R.string.hero_next,
                    )
                    "$verb · $dayName"
                } else {
                    dayName
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (current != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(4.dp))
            if (block != null) {
                val name = block.nome.ifBlank { stringResource(R.string.block_no_name) }
                val detail = if (current != null) {
                    val end = current.endMin
                    when (end) {
                        null -> stringResource(R.string.hero_open_ended)
                        else -> stringResource(R.string.hero_minutes_left, formatMinutesLabel(end - nowMin))
                    }
                } else {
                    val startsIn = (block.startMin ?: nowMin) - nowMin
                    stringResource(R.string.hero_starts_in, formatMinutesLabel(startsIn))
                }
                val schedule = block.timeRange?.format().orEmpty()
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (current != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$detail · $schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            } else {
                Text(
                    text = stringResource(R.string.hero_idle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Hero for other weekdays (no "agora" semantics in the past/future). */
@Composable
private fun PreviewHeroCard(day: RoutineDay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = day.nome,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (day.blocos.isEmpty()) {
                    stringResource(R.string.day_missing_title)
                } else {
                    stringResource(
                        R.string.hero_summary,
                        day.blocos.size,
                        Progress.totalTasks(day),
                    )
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** The selected weekday has no day in the routine — invite creating one. */
@Composable
private fun MissingDayCard(dayName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.day_missing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.day_missing_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `42 min` / `1 h 20 min` — integer-exact, no locale drift. */
private fun formatMinutesLabel(minutes: Int): String = when {
    minutes >= 60 -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "$h h" else "$h h $m min"
    }
    else -> "$minutes min"
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
        Spacer(Modifier.width(10.dp))
        ui.consistency.forEach { day ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(12.dp)
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
    nowMin: Int,
    editable: Boolean,
    vm: RoutineViewModel,
) {
    // The block in progress starts expanded — checking tasks off is the
    // single most frequent action, so it must be one tap away.
    var expanded by remember(block.id) { mutableStateOf(editable && block.isCurrent(nowMin)) }
    val isNow = editable && block.isCurrent(nowMin)
    val timeLabel = block.timeRange?.format() ?: stringResource(R.string.no_time)
    val name = block.nome.ifBlank { stringResource(R.string.block_no_name) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(
            containerColor = if (isNow) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
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
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.block_tasks_count, block.tarefas.size, timeLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isNow) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.block_open_now),
                            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
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
                        val checked = checks.contains(Progress.key(dayKey, dayId, block.id, index))
                        if (editable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { vm.toggleTask(dayId, block.id, index) },
                                    modifier = Modifier.semantics { contentDescription = task },
                                )
                                Text(
                                    task,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            // Preview mode (other day): no marks, just the list.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outline),
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    task,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (block.tarefas.isEmpty()) {
                        Text(
                            stringResource(R.string.block_no_tasks),
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
