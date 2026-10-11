package com.mohdshayan.rainfold.ui.listen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.SleepPlan
import com.mohdshayan.rainfold.data.SavedMix
import com.mohdshayan.rainfold.ui.EngineLabels
import com.mohdshayan.rainfold.ui.components.LabeledSlider
import com.mohdshayan.rainfold.ui.components.LocalUnlock
import com.mohdshayan.rainfold.ui.components.UnlockGate
import com.mohdshayan.rainfold.ui.components.lockedAction
import com.mohdshayan.rainfold.ui.theme.TimerReadout
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { content() }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Free sounds, then the unlock's sounds. A tap previews for 8 seconds; Add adds. */
@Composable
fun PickerSheet(
    inMix: Set<EngineKind>,
    previewing: EngineKind?,
    onPreview: (EngineKind) -> Unit,
    onAdd: (EngineKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val free = EngineKind.entries.filter { it.free }
    val paid = EngineKind.entries.filterNot { it.free }
    Sheet(onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SheetTitle(stringResource(R.string.picker_title)) }
            item { SectionLabel(stringResource(R.string.picker_free)) }
            items(free, key = { it.id }) { kind ->
                SoundRow(kind, kind in inMix, previewing == kind, locked = false, onPreview = { onPreview(kind) }, onAdd = { onAdd(kind) })
            }
            item { SectionLabel(stringResource(R.string.unlock_part_of)) }
            item {
                UnlockGate(
                    reason = "sounds",
                    locked = { open ->
                        Column {
                            paid.forEach { kind ->
                                SoundRow(kind, false, previewing == kind, locked = true, onPreview = { onPreview(kind) }, onAdd = open)
                            }
                        }
                    },
                ) {
                    Column {
                        paid.forEach { kind ->
                            SoundRow(kind, kind in inMix, previewing == kind, locked = false, onPreview = { onPreview(kind) }, onAdd = { onAdd(kind) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundRow(
    kind: EngineKind,
    inMix: Boolean,
    previewing: Boolean,
    locked: Boolean,
    onPreview: () -> Unit,
    onAdd: () -> Unit,
) {
    val name = stringResource(EngineLabels.name(kind))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview)
            .padding(start = 12.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreview) {
            Icon(
                if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = if (previewing) stringResource(R.string.picker_stop_preview) else stringResource(R.string.picker_preview, name),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (previewing) stringResource(R.string.picker_previewing) else stringResource(EngineLabels.detail(kind)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (inMix) {
            Text(stringResource(R.string.picker_in_mix), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FilledTonalButton(shape = MaterialTheme.shapes.medium, onClick = onAdd, modifier = Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                if (locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.picker_add))
            }
        }
    }
}

private val DURATIONS = listOf(15, 30, 45, 60, 90, 120, 180, 240, 300, 360, 420, 480, 540, 600)

@Composable
fun durationText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.minutes_short, minutes)
    minutes % 60 == 0 -> stringResource(R.string.hours_short, minutes / 60)
    else -> stringResource(R.string.hours_minutes_short, minutes / 60, minutes % 60)
}

/** Duration, fade length, Evolving night (part of the unlock) and Start timer. */
@Composable
fun TimerSheet(
    ui: ListenUi,
    onStart: (minutes: Int, fade: Int) -> Unit,
    onCancel: () -> Unit,
    onEvolving: (on: Boolean, range: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val access = LocalUnlock.current
    var index by rememberSaveable { mutableIntStateOf(DURATIONS.indices.minBy { kotlin.math.abs(DURATIONS[it] - ui.timerMinutes) }) }
    var fade by rememberSaveable { mutableIntStateOf(ui.fadeMinutes.coerceIn(SleepPlan.MIN_FADE, SleepPlan.MAX_FADE)) }
    val live = ui.live
    var drift by rememberSaveable { mutableFloatStateOf(live?.spec?.driftRange ?: 0.3f) }
    val minutes = DURATIONS[index]
    val preview = SleepPlan.of(System.currentTimeMillis(), minutes, fade)
    Sheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            SheetTitle(stringResource(R.string.timer_title))
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(durationText(minutes), style = TimerReadout, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.timer_fades_at, clock(context, preview.fadeStartsAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    stringResource(R.string.timer_duration),
                    index.toFloat(),
                    { index = it.toInt().coerceIn(0, DURATIONS.lastIndex) },
                    0f..DURATIONS.lastIndex.toFloat(),
                    steps = DURATIONS.size - 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledSlider(
                        stringResource(R.string.timer_fade),
                        fade.toFloat(),
                        { fade = it.toInt().coerceIn(SleepPlan.MIN_FADE, SleepPlan.MAX_FADE) },
                        SleepPlan.MIN_FADE.toFloat()..SleepPlan.MAX_FADE.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.minutes_short, fade),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp).padding(start = 8.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val evolvingOn = access.unlocked && live?.spec?.evolving == true
                val lockedToggle = lockedAction("evolving") { onEvolving(!(live?.spec?.evolving ?: false), drift) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.timer_evolving), style = MaterialTheme.typography.titleMedium)
                            if (!access.unlocked) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            if (access.unlocked) stringResource(R.string.timer_evolving_body) else stringResource(R.string.unlock_part_of),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = evolvingOn,
                        onCheckedChange = { lockedToggle() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                if (evolvingOn) {
                    LabeledSlider(
                        stringResource(R.string.timer_drift),
                        drift,
                        {
                            drift = it
                            onEvolving(true, it)
                        },
                        0.1f..0.5f,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(shape = MaterialTheme.shapes.medium, onClick = { onStart(minutes, fade) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.timer_start), style = MaterialTheme.typography.labelLarge)
                }
                if (ui.plan != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.timer_off))
                    }
                }
            }
        }
    }
}

/** Name and Save mix; at the free cap, Replace a mix or Unlock unlimited mixes. */
@Composable
fun SaveSheet(
    ui: ListenUi,
    onSave: suspend (name: String, asNew: Boolean) -> SaveOutcome,
    onReplace: suspend (SavedMix, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val access = LocalUnlock.current
    val scope = rememberCoroutineScope()
    val live = ui.live ?: return
    val fromSaved = ui.saved.firstOrNull { it.id == live.savedId }
    val initial = fromSaved?.spec?.name ?: ""
    var name by rememberSaveable { mutableStateOf(initial) }
    var showError by rememberSaveable { mutableStateOf(false) }
    var atCap by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val valid = name.isNotBlank()

    fun save(asNew: Boolean) {
        if (!valid) {
            showError = true
            return
        }
        scope.launch {
            when (onSave(name, asNew)) {
                SaveOutcome.SAVED -> onDismiss()
                SaveOutcome.AT_CAP -> atCap = true
            }
        }
    }

    Sheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            SheetTitle(stringResource(R.string.save_mix))
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                        showError = false
                    },
                    label = { Text(stringResource(R.string.save_name)) },
                    supportingText = {
                        Text(stringResource(if (showError) R.string.save_name_empty else R.string.save_name_help))
                    },
                    isError = showError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!atCap) save(asNew = fromSaved == null) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        errorBorderColor = MaterialTheme.colorScheme.onSurface,
                        errorSupportingTextColor = MaterialTheme.colorScheme.onSurface,
                        errorLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!atCap) {
                    if (fromSaved != null) {
                        Button(shape = MaterialTheme.shapes.medium, onClick = { save(asNew = false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.save_changes, fromSaved.spec.name))
                        }
                        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { save(asNew = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.save_as_new))
                        }
                    } else {
                        Button(shape = MaterialTheme.shapes.medium, onClick = { save(asNew = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.save_mix))
                        }
                    }
                } else if (!picking) {
                    Text(stringResource(R.string.save_at_cap), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { picking = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.save_replace))
                    }
                    Button(shape = MaterialTheme.shapes.medium, onClick = { access.open("mixes") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.unlock_unlimited_mixes))
                    }
                } else {
                    Text(stringResource(R.string.save_replace_pick), style = MaterialTheme.typography.bodyLarge)
                    ui.saved.forEach { target ->
                        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = {
                                if (!valid) {
                                    showError = true
                                } else {
                                    scope.launch {
                                        onReplace(target, name)
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.save_replace_one, target.spec.name))
                        }
                    }
                }
            }
        }
    }
}
