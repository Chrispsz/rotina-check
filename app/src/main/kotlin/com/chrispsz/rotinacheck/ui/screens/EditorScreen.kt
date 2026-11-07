package com.chrispsz.rotinacheck.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chrispsz.rotinacheck.R
import com.chrispsz.rotinacheck.data.RoutineBlock
import com.chrispsz.rotinacheck.data.TimeRange
import com.chrispsz.rotinacheck.viewmodel.RoutineViewModel
import com.chrispsz.rotinacheck.viewmodel.UiState

/**
 * Real-time block editor: every change persists immediately (no save step —
 * "Salvar" simply confirms and returns). Text fields keep local cursor state
 * to avoid fighting the round-trip through DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(ui: UiState, vm: RoutineViewModel) {
    val located = ui.routine?.dias?.asSequence()
        ?.flatMap { day -> day.blocos.asSequence().map { day.id to it } }
        ?.firstOrNull { it.second.id == ui.editorBlockId }

    if (located == null) {
        // Block vanished (deleted elsewhere) — bounce back home.
        LaunchedEffect(ui.editorBlockId) { vm.closeEditor() }
        Box(Modifier.fillMaxSize())
        return
    }
    val (dayId, block) = located

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.editor_title)) },
            navigationIcon = {
                IconButton(onClick = { vm.closeEditor() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.topbar_back_a11y),
                    )
                }
            },
            actions = {
                TextButton(onClick = { vm.closeEditor() }) {
                    Text(stringResource(R.string.editor_save))
                }
            },
        )

        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                var nameText by remember(block.id) { mutableStateOf(block.nome) }
                LaunchedEffect(block.nome) { if (nameText != block.nome) nameText = block.nome }
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        vm.renameBlock(dayId, block.id, it)
                    },
                    label = { Text(stringResource(R.string.editor_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- Horários -------------------------------------------------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeButton(
                        label = stringResource(R.string.editor_start_label),
                        minutes = block.startMin,
                    ) { showStartPicker = true }
                    Spacer(Modifier.width(12.dp))
                    TimeButton(
                        label = stringResource(R.string.editor_end_label),
                        minutes = block.endMin,
                        enabled = block.startMin != null,
                    ) { showEndPicker = true }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.editor_no_end),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(
                            checked = block.endMin == null,
                            enabled = block.startMin != null,
                            onCheckedChange = { noEnd ->
                                vm.setBlockTime(
                                    dayId,
                                    block.id,
                                    block.startMin,
                                    if (noEnd) null else minOf((block.startMin ?: 0) + 60, 23 * 60 + 59),
                                )
                            },
                        )
                    }
                }

                // ---- Tarefas --------------------------------------------------------
                Text(
                    stringResource(R.string.editor_tasks_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                block.tarefas.forEachIndexed { index, task ->
                    var taskText by remember(block.id, index) { mutableStateOf(task) }
                    LaunchedEffect(block.tarefas.getOrNull(index)) {
                        val current = block.tarefas.getOrNull(index) ?: ""
                        if (taskText != current) taskText = current
                    }
                    OutlinedTextField(
                        value = taskText,
                        onValueChange = {
                            taskText = it
                            vm.renameTask(dayId, block.id, index, it)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { vm.removeTask(dayId, block.id, index) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.editor_remove_task_a11y),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }

                var newTaskText by remember(block.id, block.tarefas.size) { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        label = { Text(stringResource(R.string.editor_add_task)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        vm.addTask(dayId, block.id, newTaskText)
                        newTaskText = ""
                    }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.editor_add_a11y),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                TextButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_delete_block))
                }
            }
        }
    }

    // ---- Dialogs ---------------------------------------------------------------

    if (showStartPicker) {
        TimeDialog(
            initialMinutes = block.startMin ?: (8 * 60),
            onConfirm = { minutes ->
                vm.setBlockTime(dayId, block.id, minutes, block.endMin)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimeDialog(
            initialMinutes = block.endMin ?: ((block.startMin ?: 480) + 60).coerceAtMost(23 * 60 + 59),
            onConfirm = { minutes ->
                vm.setBlockTime(dayId, block.id, block.startMin, minutes)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.editor_delete_confirm_title)) },
            text = { Text(stringResource(R.string.editor_delete_confirm_body, block.nome)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.removeBlock(dayId, block.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TimeButton(label: String, minutes: Int?, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) {
        Text(if (minutes == null) label else "$label ${TimeRange.label(minutes)}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state: TimePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_pick_time_a11y)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
