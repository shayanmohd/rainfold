package com.mohdshayan.rainfold.ui.listen

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.core.mix.FreeTier
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.data.PlaybackStatus
import com.mohdshayan.rainfold.data.SessionRepository
import com.mohdshayan.rainfold.ui.EngineLabels
import com.mohdshayan.rainfold.ui.components.LabeledSlider
import com.mohdshayan.rainfold.ui.components.LocalUnlock
import com.mohdshayan.rainfold.ui.theme.LocalReducedMotion
import java.util.Date

enum class ListenSheet { NONE, PICKER, TIMER, SAVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    onOpenBatteryHelp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListenViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val access = LocalUnlock.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var sheet by rememberSaveable { mutableStateOf(ListenSheet.NONE) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            when (message) {
                is ListenMessage.Added -> snackbar.showSnackbar(context.getString(R.string.picker_added, context.getString(EngineLabels.name(message.kind))))
                is ListenMessage.Saved -> {
                    val text = context.getString(R.string.saved, message.name)
                    if (message.offerUnlock) {
                        val result = snackbar.showSnackbar(
                            message = text + "\n" + context.getString(R.string.offer_line),
                            actionLabel = context.getString(R.string.offer_action),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) access.open("offer")
                    } else {
                        snackbar.showSnackbar(text)
                    }
                }
                is ListenMessage.Replaced -> snackbar.showSnackbar(context.getString(R.string.save_replaced, message.old, message.name))
                is ListenMessage.TimerSet -> snackbar.showSnackbar(context.getString(R.string.timer_fades_at, clock(context, message.fadesAt)))
                ListenMessage.TimerOff -> snackbar.showSnackbar(context.getString(R.string.timer_turned_off))
                ListenMessage.InCall -> snackbar.showSnackbar(context.getString(R.string.picker_in_call))
            }
        }
    }

    val live = ui.live
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        live?.let { EngineLabels.title(context, it) } ?: stringResource(R.string.nav_listen),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight * 1.15f
            val foldHeight = (maxHeight * 0.42f).coerceAtLeast(200.dp)
            val playable = live?.let { FreeTier.playableLayers(access.unlocked, it.spec.layers) }.orEmpty()
            val fold = @Composable { m: Modifier ->
                if (live == null) {
                    FoldSkeleton(m)
                } else {
                    Fold(
                        layers = live.spec.layers,
                        playable = playable,
                        onVolume = viewModel::setVolume,
                        onTone = viewModel::setTone,
                        modifier = m,
                    )
                }
            }
            val controls = @Composable {
                ControlBar(
                    ui = ui,
                    onTimer = { sheet = ListenSheet.TIMER },
                    onPlay = viewModel::togglePlay,
                    onSave = { sheet = ListenSheet.SAVE },
                    enabled = live != null,
                )
            }
            val list = @Composable { m: Modifier ->
                LayerList(
                    layers = live?.spec?.layers.orEmpty(),
                    playable = playable,
                    stoppedBySystem = ui.stoppedBySystem,
                    onBatteryHelp = onOpenBatteryHelp,
                    onDismissStopped = viewModel::dismissStopped,
                    onVolume = viewModel::setVolume,
                    onTone = viewModel::setTone,
                    onIntensity = viewModel::setIntensity,
                    onRemove = viewModel::remove,
                    onAdd = { sheet = ListenSheet.PICKER },
                    modifier = m,
                )
            }
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    fold(Modifier.weight(1f).fillMaxHeight().padding(bottom = 16.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        list(Modifier.weight(1f))
                        controls()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    fold(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(foldHeight),
                    )
                    list(Modifier.weight(1f))
                    controls()
                }
            }
        }
    }

    when (sheet) {
        ListenSheet.NONE -> Unit
        ListenSheet.PICKER -> PickerSheet(
            inMix = live?.spec?.layers?.map { it.kind }.orEmpty().toSet(),
            previewing = ui.previewing,
            onPreview = viewModel::togglePreview,
            onAdd = { kind ->
                viewModel.add(kind)
                sheet = ListenSheet.NONE
            },
            onDismiss = {
                viewModel.stopPreview()
                sheet = ListenSheet.NONE
            },
        )
        ListenSheet.TIMER -> TimerSheet(
            ui = ui,
            onStart = { minutes, fade ->
                viewModel.startTimer(minutes, fade)
                sheet = ListenSheet.NONE
            },
            onCancel = {
                viewModel.cancelTimer()
                sheet = ListenSheet.NONE
            },
            onEvolving = viewModel::setEvolving,
            onDismiss = { sheet = ListenSheet.NONE },
        )
        ListenSheet.SAVE -> SaveSheet(
            ui = ui,
            onSave = { name, asNew -> viewModel.save(name, asNew) },
            onReplace = { target, name -> viewModel.replace(target, name) },
            onDismiss = { sheet = ListenSheet.NONE },
        )
    }
}

fun clock(context: android.content.Context, epochMs: Long): String =
    DateFormat.getTimeFormat(context).format(Date(epochMs))

@Composable
private fun LayerList(
    layers: List<LayerSpec>,
    playable: List<Boolean>,
    stoppedBySystem: Boolean,
    onBatteryHelp: () -> Unit,
    onDismissStopped: () -> Unit,
    onVolume: (Int, Float) -> Unit,
    onTone: (Int, Float) -> Unit,
    onIntensity: (Int, Float) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val access = LocalUnlock.current
    val listState = rememberLazyListState()
    // The banner arrives after the rows: bring it into view instead of leaving it above the scroll.
    LaunchedEffect(stoppedBySystem) { if (stoppedBySystem) listState.scrollToItem(0) }
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (stoppedBySystem) {
            item(key = "stopped") {
                StoppedBanner(onBatteryHelp = onBatteryHelp, onDismiss = onDismissStopped)
            }
        }
        items(layers, key = { it.key }) { layer ->
            val index = layers.indexOf(layer)
            LayerRow(
                layer = layer,
                muted = !playable.getOrElse(index) { true },
                onVolume = { onVolume(layer.key, it) },
                onTone = { onTone(layer.key, it) },
                onIntensity = { onIntensity(layer.key, it) },
                onRemove = { onRemove(layer.key) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item(key = "add") {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val canAdd = FreeTier.canAddLayer(access.unlocked, layers.size)
                when {
                    canAdd -> OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_layer))
                    }
                    !access.unlocked -> OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { access.open("layers") }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.unlock_add_layer))
                    }
                    else -> Text(
                        stringResource(R.string.picker_full),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!access.unlocked && canAdd) {
                    TextButton(onClick = { access.open("sounds") }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.unlock_more_sounds))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoppedBanner(onBatteryHelp: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.listen_stopped_error), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onBatteryHelp, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.listen_battery_help))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.listen_dismiss))
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: LayerSpec,
    muted: Boolean,
    onVolume: (Float) -> Unit,
    onTone: (Float) -> Unit,
    onIntensity: (Float) -> Unit,
    onRemove: () -> Unit,
) {
    val name = stringResource(EngineLabels.name(layer.kind))
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (muted) {
                    // A muted layer says why and opens the unlock, like every other gate.
                    val access = LocalUnlock.current
                    TextButton(
                        onClick = { access.open(if (layer.kind.free) "layers" else "sounds") },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.unlock_part_of), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.layer_remove, name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LabeledSlider(stringResource(R.string.layer_volume), layer.volume, onVolume, 0f..1f)
        LabeledSlider(stringResource(R.string.layer_tone), layer.tone, onTone, -1f..1f)
        if (layer.kind.hasIntensity) {
            LabeledSlider(stringResource(R.string.layer_intensity), layer.intensity, onIntensity, 0f..1f)
        }
    }
}

@Composable
private fun ControlBar(
    ui: ListenUi,
    onTimer: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val playing = ui.status == PlaybackStatus.PLAYING
    val reduced = LocalReducedMotion.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onTimer,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                val plan = ui.plan
                if (plan == null) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.timer_chip_off), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    // "Fades at 12:28 AM" does not fit one line beside the Play button: let it take two.
                    Text(
                        // A no-break space keeps "12:48 AM" whole, so a wrap falls after "Fades at".
                        stringResource(R.string.timer_fades_at, clock(context, plan.fadeStartsAt).replace(' ', '\u00A0')),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            val playLabel = stringResource(if (playing) R.string.action_pause else R.string.action_play)
            val canPlay = enabled && (playing || ui.live?.spec?.layers?.isNotEmpty() == true)
            Surface(
                onClick = onPlay,
                enabled = canPlay,
                shape = CircleShape,
                color = if (canPlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                contentColor = if (canPlay) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp).semantics { contentDescription = playLabel },
            ) {
                Crossfade(targetState = playing, animationSpec = if (reduced) snap() else tween(150), label = "play") { on ->
                    Icon(
                        if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(18.dp).fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onSave,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.save_mix), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        val status = when {
            playing -> stringResource(R.string.listen_playing)
            ui.status == PlaybackStatus.PAUSED_FOR_CALL -> stringResource(R.string.listen_paused_call)
            ui.live?.savedId == null && ui.live?.spec?.layers?.map { it.kind } == SessionRepository.SEED.layers.map { it.kind } ->
                stringResource(R.string.listen_play_seed)
            else -> stringResource(R.string.listen_stopped)
        }
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
