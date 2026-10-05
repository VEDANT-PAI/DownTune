/*
 * DownTune (2026) - fork of ArchiveTune
 * ArchiveTune © Rukamori and contributors, GPL-3.0. This file is part of a modified version.
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.plugins.PluginManager
import moe.rukamori.archivetune.plugins.PluginUiState
import javax.inject.Inject

@HiltViewModel
class PluginsViewModel
    @Inject
    constructor(
        private val manager: PluginManager,
    ) : ViewModel() {
        val plugins: StateFlow<List<PluginUiState>> = manager.state

        fun setEnabled(
            id: String,
            enabled: Boolean,
        ) = manager.setEnabled(id, enabled)

        fun setSetting(
            id: String,
            key: String,
            value: String,
        ) = manager.setSetting(id, key, value)
    }
