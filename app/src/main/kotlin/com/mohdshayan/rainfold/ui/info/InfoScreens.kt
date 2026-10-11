package com.mohdshayan.rainfold.ui.info

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mohdshayan.rainfold.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

/** OEM battery restrictions explained, with a way into the system's optimisation list. */
@Composable
fun BatteryHelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var fallback by rememberSaveable { mutableStateOf(false) }
    val openAppInfo = {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
    InfoScaffold(stringResource(R.string.battery_title), onBack) {
        Body(stringResource(R.string.battery_lead))
        Body(stringResource(R.string.battery_step_1))
        Body(stringResource(R.string.battery_step_2))
        Body(stringResource(R.string.battery_step_3))
        Button(shape = MaterialTheme.shapes.medium, onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    fallback = true
                    openAppInfo()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.battery_open)) }
        if (fallback) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                Body(stringResource(R.string.battery_fallback))
            }
        }
        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = openAppInfo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.battery_open_app_info))
        }
    }
}

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    InfoScaffold(stringResource(R.string.licences_title), onBack) {
        Heading(stringResource(R.string.licences_sounds_title))
        Body(stringResource(R.string.licences_sounds_body))
        Heading(stringResource(R.string.licences_fonts_title))
        Body(stringResource(R.string.licences_bricolage))
        Body(stringResource(R.string.licences_figtree))
        Body(stringResource(R.string.licences_ofl_note))
        Heading(stringResource(R.string.licences_libraries_title))
        Body(stringResource(R.string.licences_libraries_body))
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    InfoScaffold(stringResource(R.string.privacy_title), onBack) {
        Body(stringResource(R.string.privacy_lead))
        Body(stringResource(R.string.privacy_stored))
        Body(stringResource(R.string.privacy_purchases))
        Heading(stringResource(R.string.privacy_permissions_title))
        Body(stringResource(R.string.privacy_perm_fgs))
        Body(stringResource(R.string.privacy_perm_wake))
        Body(stringResource(R.string.privacy_perm_billing))
    }
}
