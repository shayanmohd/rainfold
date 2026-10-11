package com.mohdshayan.rainfold.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.core.mix.SleepPlan
import com.mohdshayan.rainfold.data.prefs.ThemeMode
import com.mohdshayan.rainfold.ui.components.LabeledSlider
import com.mohdshayan.rainfold.ui.components.LocalUnlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBatteryHelp: () -> Unit,
    onLicences: () -> Unit,
    onPrivacy: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val access = LocalUnlock.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val email = stringResource(R.string.support_email)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.restoreResults.collect {
            snackbar.showSnackbar(
                context.getString(
                    when (it) {
                        RestoreResult.RESTORED -> R.string.settings_restore_done
                        RestoreResult.NONE -> R.string.settings_restore_none
                        RestoreResult.NO_ANSWER -> R.string.settings_restore_no_answer
                    },
                ),
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Group(stringResource(R.string.settings_theme)) {
                val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = ui.theme == mode,
                            onClick = { viewModel.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSurface,
                                inactiveContainerColor = MaterialTheme.colorScheme.background,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                activeBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
            Group(stringResource(R.string.settings_default_fade)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    LabeledSlider(
                        stringResource(R.string.timer_fade),
                        ui.fadeMinutes.toFloat(),
                        { viewModel.setFade(it.toInt()) },
                        SleepPlan.MIN_FADE.toFloat()..SleepPlan.MAX_FADE.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.minutes_short, ui.fadeMinutes), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp).padding(start = 8.dp))
                }
            }
            Group(stringResource(R.string.settings_purchase)) {
                if (access.unlocked) {
                    Text(
                        stringResource(R.string.settings_unlocked),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                } else {
                    LinkRow(stringResource(R.string.unlock_everything_row), null) { access.open("settings") }
                }
                LinkRow(stringResource(R.string.unlock_restore), null, enabled = !ui.restoring) { viewModel.restore() }
            }
            Group(stringResource(R.string.settings_help)) {
                LinkRow(stringResource(R.string.settings_battery), stringResource(R.string.settings_battery_body), onClick = onBatteryHelp)
                LinkRow(stringResource(R.string.settings_licences), null, onClick = onLicences)
                LinkRow(stringResource(R.string.settings_privacy), null, onClick = onPrivacy)
                LinkRow(stringResource(R.string.settings_support), email) {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_email_failed, email)) }
                    }
                }
            }
            Group(null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setUsage(!ui.usage.optedIn) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.settings_counts), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (ui.usage.optedIn) stringResource(R.string.settings_counts_value, ui.usage.nights, ui.usage.mixesSaved)
                            else stringResource(R.string.settings_counts_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = ui.usage.optedIn,
                        onCheckedChange = viewModel::setUsage,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        content()
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun LinkRow(title: String, body: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
