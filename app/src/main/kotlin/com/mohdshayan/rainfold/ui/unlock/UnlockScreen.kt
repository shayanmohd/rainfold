package com.mohdshayan.rainfold.ui.unlock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.billing.Entitlement
import com.mohdshayan.rainfold.billing.StoreState

/**
 * The one place the unlock is sold and restored. It says what the unlock adds, shows Google Play's own
 * price, and stays an honest message on a phone where Play cannot sell, where the free version carries
 * on. [reason] is the locked feature that opened it, used for the first line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(reason: String, onClose: () -> Unit, viewModel: UnlockViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val wide = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.unlock_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.unlocked) {
                Text(stringResource(R.string.unlock_done), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onClose, modifier = wide) { Text(stringResource(R.string.unlock_back)) }
                return@Column
            }
            reasonLine(reason)?.let {
                Text(stringResource(it), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.unlock_body), style = MaterialTheme.typography.bodyLarge, color = muted)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                for (line in stringArrayResource(R.array.unlock_features)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp).padding(top = 2.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(stringResource(R.string.unlock_free_note), style = MaterialTheme.typography.bodyMedium, color = muted)
            Spacer(Modifier.height(4.dp))
            val price = state.price
            val message = when {
                state.entitlement == Entitlement.PENDING -> R.string.unlock_pending
                state.store == StoreState.UNAVAILABLE -> R.string.unlock_unavailable
                state.store == StoreState.OFFLINE -> R.string.unlock_offline
                state.store == StoreState.NOT_OFFERED -> R.string.unlock_not_offered
                else -> null
            }
            when {
                message != null -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                }
                // Loading: the button's own shape, disabled, until Google Play's price arrives.
                price == null -> Button(shape = MaterialTheme.shapes.medium, onClick = {}, enabled = false, modifier = wide) {
                    Text(stringResource(R.string.unlock_checking))
                }
                else -> Button(shape = MaterialTheme.shapes.medium, onClick = { activity?.let(viewModel::buy) }, enabled = activity != null, modifier = wide) {
                    Text(stringResource(R.string.unlock_buy, price), style = MaterialTheme.typography.labelLarge)
                }
            }
            // As large as the buy button and never delayed: declining is as easy as buying.
            OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onClose, modifier = wide) { Text(stringResource(R.string.unlock_close)) }
            val result = when (note) {
                UnlockNote.CANCELLED -> R.string.unlock_cancelled
                UnlockNote.FAILED -> R.string.unlock_failed
                UnlockNote.NOT_OFFERED -> R.string.unlock_not_offered
                UnlockNote.NO_ANSWER -> R.string.unlock_no_answer
                UnlockNote.NOT_OWNED -> R.string.unlock_not_owned
                UnlockNote.NONE -> null
            }
            if (result != null) Text(stringResource(result), style = MaterialTheme.typography.bodyMedium)
            if (state.store != StoreState.UNAVAILABLE) {
                TextButton(onClick = viewModel::restore, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.unlock_restore))
                }
            }
            Text(stringResource(R.string.unlock_account_note), style = MaterialTheme.typography.bodyMedium, color = muted)
            Text(
                stringResource(R.string.unlock_support, stringResource(R.string.support_email)),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
    }
}

private fun reasonLine(reason: String): Int? = when (reason) {
    "layers" -> R.string.unlock_reason_layers
    "sounds", "offer" -> R.string.unlock_reason_sounds
    "mixes" -> R.string.unlock_reason_mixes
    "evolving" -> R.string.unlock_reason_evolving
    "tile" -> R.string.unlock_reason_tile
    else -> null
}
