package com.mohdshayan.rainfold.ui.mixes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.core.mix.FreeTier
import com.mohdshayan.rainfold.data.SavedMix
import com.mohdshayan.rainfold.ui.EngineLabels
import com.mohdshayan.rainfold.ui.components.EmptyState
import com.mohdshayan.rainfold.ui.components.LocalUnlock
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixesScreen(
    onOpenListen: () -> Unit,
    modifier: Modifier = Modifier,
    showOpenListen: Boolean = true,
    viewModel: MixesViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<SavedMix?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val onExport = { if (viewModel.canExport()) exporter.launch(viewModel.exportFileName()) }
    val onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { m ->
            val res = context.resources
            when (m) {
                is MixesMessage.Exported -> snackbar.showSnackbar(res.getQuantityString(R.plurals.mixes_exported, m.count, m.count))
                is MixesMessage.Imported -> {
                    val text = res.getQuantityString(R.plurals.mixes_imported, m.count, m.count) +
                        if (m.skipped > 0) ". " + res.getQuantityString(R.plurals.mixes_skipped, m.skipped, m.skipped) else ""
                    snackbar.showSnackbar(text, duration = if (m.skipped > 0) SnackbarDuration.Long else SnackbarDuration.Short)
                }
                MixesMessage.NotAMixFile -> snackbar.showSnackbar(context.getString(R.string.mixes_not_file))
                MixesMessage.NewerSchema -> snackbar.showSnackbar(context.getString(R.string.mixes_newer))
                MixesMessage.ReadFailed -> snackbar.showSnackbar(context.getString(R.string.mixes_read_failed))
                MixesMessage.WriteFailed -> snackbar.showSnackbar(context.getString(R.string.mixes_write_failed))
                MixesMessage.NothingToExport -> snackbar.showSnackbar(context.getString(R.string.mixes_nothing_to_export))
                is MixesMessage.Duplicated -> snackbar.showSnackbar(context.getString(R.string.mix_duplicated, m.name))
                is MixesMessage.Renamed -> snackbar.showSnackbar(context.getString(R.string.mix_renamed, m.name))
                is MixesMessage.Deleted -> {
                    val r = snackbar.showSnackbar(
                        context.getString(R.string.mix_deleted, m.mix.spec.name),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) viewModel.undoDelete(m.mix, m.position)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mixes_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> {
                    var show by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(300)
                        show = true
                    }
                    if (show) SkeletonRows()
                }
                ui.mixes.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        title = stringResource(R.string.mixes_empty_title),
                        body = stringResource(R.string.mixes_empty_body),
                        actionLabel = if (showOpenListen) stringResource(R.string.mixes_open_listen) else null,
                        onAction = if (showOpenListen) onOpenListen else null,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.mixes_import))
                    }
                }
                else -> MixList(
                    ui = ui,
                    onPlay = viewModel::play,
                    onRename = { renaming = it },
                    onDuplicate = { viewModel.duplicate(it) },
                    onDelete = { viewModel.delete(it) },
                    onMove = { mix, d -> viewModel.move(mix, d) },
                    onReorder = viewModel::reorder,
                    onExport = onExport,
                    onImport = onImport,
                )
            }
        }
    }

    renaming?.let { mix ->
        RenameDialog(
            initial = mix.spec.name,
            onConfirm = {
                viewModel.rename(mix, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun MixList(
    ui: MixesUi,
    onPlay: (SavedMix) -> Unit,
    onRename: (SavedMix) -> Unit,
    onDuplicate: (SavedMix) -> Unit,
    onDelete: (SavedMix) -> Unit,
    onMove: (SavedMix, Int) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    // A local copy while dragging, written back to Room once on release.
    val order = remember(ui.mixes) { ui.mixes.toMutableStateList() }
    val current by rememberUpdatedState(order)
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onExport, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mixes_export), maxLines = 1)
                    }
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onImport, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mixes_import), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mixes_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(order, key = { _, m -> m.id }) { index, mix ->
            val isDragging = draggingId == mix.id
            Column(
                Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) offset else 0f }
                    .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                    .onSizeChanged { rowHeight = it.height.toFloat().coerceAtLeast(1f) }
                    .pointerInput(mix.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = mix.id
                                offset = 0f
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                offset += drag.y
                                val list = current
                                val i = list.indexOfFirst { it.id == mix.id }
                                if (offset > rowHeight / 2 && i < list.lastIndex) {
                                    list.add(i + 1, list.removeAt(i))
                                    offset -= rowHeight
                                } else if (offset < -rowHeight / 2 && i > 0) {
                                    list.add(i - 1, list.removeAt(i))
                                    offset += rowHeight
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                offset = 0f
                                onReorder(current.map { it.id })
                            },
                            onDragCancel = {
                                draggingId = null
                                offset = 0f
                            },
                        )
                    },
            ) {
                MixRow(
                    mix = mix,
                    savedCount = order.size,
                    playing = ui.playingId == mix.id,
                    first = index == 0,
                    last = index == order.lastIndex,
                    onPlay = { onPlay(mix) },
                    onRename = { onRename(mix) },
                    onDuplicate = { onDuplicate(mix) },
                    onDelete = { onDelete(mix) },
                    onMove = { onMove(mix, it) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun MixRow(
    mix: SavedMix,
    savedCount: Int,
    playing: Boolean,
    first: Boolean,
    last: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val context = LocalContext.current
    val access = LocalUnlock.current
    var menu by remember { mutableStateOf(false) }
    val summary = mix.spec.layers.joinToString(", ") { context.getString(EngineLabels.name(it.kind)) }
    val partlyLocked = FreeTier.playableLayers(access.unlocked, mix.spec.layers).any { !it }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(mix.spec.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (partlyLocked) {
                Text(
                    stringResource(R.string.mix_partly_locked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (playing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.mix_playing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.mix_more, mix.spec.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                DropdownMenuItem(text = { Text(stringResource(R.string.mix_rename)) }, onClick = { menu = false; onRename() })
                // A copy is one more saved mix, so at the free cap it opens the unlock like Save mix does.
                val canCopy = FreeTier.canSaveMix(access.unlocked, savedCount)
                DropdownMenuItem(
                    text = { Text(stringResource(if (canCopy) R.string.mix_duplicate else R.string.unlock_unlimited_mixes)) },
                    leadingIcon = if (canCopy) null else {
                        { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    },
                    onClick = {
                        menu = false
                        if (canCopy) onDuplicate() else access.open("mixes")
                    },
                )
                if (!first) DropdownMenuItem(text = { Text(stringResource(R.string.mix_move_up)) }, onClick = { menu = false; onMove(-1) })
                if (!last) DropdownMenuItem(text = { Text(stringResource(R.string.mix_move_down)) }, onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.mix_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                label = { Text(stringResource(R.string.save_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.mix_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Row-shaped skeleton while the list loads for longer than 300 ms. Still, no shimmer. */
@Composable
private fun SkeletonRows() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(140.dp).height(18.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)))
                Box(Modifier.width(220.dp).height(14.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)))
            }
        }
    }
}
