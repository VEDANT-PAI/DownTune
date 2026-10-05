/*
 * DownTune (2026) - fork of ArchiveTune
 * ArchiveTune © Rukamori and contributors, GPL-3.0. This file is part of a modified version.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.plugins.PluginSetting
import moe.rukamori.archivetune.plugins.PluginSettingValue
import moe.rukamori.archivetune.plugins.PluginUiState
import moe.rukamori.archivetune.viewmodels.PluginsViewModel

@Composable
fun PluginsSettings(
    navController: NavController,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plugins_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.plugins_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            items(plugins, key = { it.manifest.id }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    onToggle = { viewModel.setEnabled(plugin.manifest.id, it) },
                    onSetting = { key, value -> viewModel.setSetting(plugin.manifest.id, key, value) },
                )
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginUiState,
    onToggle: (Boolean) -> Unit,
    onSetting: (key: String, value: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (plugin.enabled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plugin.manifest.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "v${plugin.manifest.version} · ${plugin.manifest.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = plugin.enabled, onCheckedChange = onToggle)
            }
            Text(plugin.manifest.description, style = MaterialTheme.typography.bodyMedium)

            if (plugin.disabledByError) {
                Text(
                    stringResource(R.string.plugins_disabled_by_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            AnimatedVisibility(visible = plugin.enabled && plugin.settings.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    plugin.settings.forEach { SettingField(it, onSetting) }
                }
            }
        }
    }
}

@Composable
private fun SettingField(
    item: PluginSettingValue,
    onSetting: (String, String) -> Unit,
) {
    when (val s = item.schema) {
        is PluginSetting.Toggle ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = MaterialTheme.typography.bodyLarge)
                    s.description?.let { Hint(it) }
                }
                Switch(
                    checked = item.value.toBooleanStrictOrNull() ?: s.default,
                    onCheckedChange = { onSetting(s.key, it.toString()) },
                )
            }

        is PluginSetting.IntRange -> {
            val saved = item.value.toIntOrNull() ?: s.default
            var draft by remember(saved) { mutableFloatStateOf(saved.toFloat()) }
            Column {
                Row {
                    Text(s.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${draft.toInt()}${s.unit}", style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = draft,
                    onValueChange = { draft = it },
                    onValueChangeFinished = { onSetting(s.key, draft.toInt().toString()) },
                    valueRange = s.min.toFloat()..s.max.toFloat(),
                )
                s.description?.let { Hint(it) }
            }
        }

        is PluginSetting.Text -> {
            var draft by remember(item.value) { mutableStateOf(item.value) }
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(s.label) },
                    placeholder = { if (s.hint.isNotEmpty()) Text(s.hint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSetting(s.key, draft) }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused && draft != item.value) onSetting(s.key, draft) },
                )
                s.description?.let { Hint(it) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
