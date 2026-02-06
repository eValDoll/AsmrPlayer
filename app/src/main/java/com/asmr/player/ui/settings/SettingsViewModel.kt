package com.asmr.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    val floatingLyricsEnabled: StateFlow<Boolean> = settingsRepository.floatingLyricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val floatingLyricsSettings: StateFlow<FloatingLyricsSettings> = settingsRepository.floatingLyricsSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingLyricsSettings())

    val dynamicPlayerHueEnabled: StateFlow<Boolean> = settingsDataStore.dynamicPlayerHueEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val themeMode: StateFlow<String> = settingsDataStore.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val staticHueArgb: StateFlow<Int?> = settingsDataStore.staticHueArgb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val coverBackgroundEnabled: StateFlow<Boolean> = settingsDataStore.coverBackgroundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val coverBackgroundClarity: StateFlow<Float> = settingsDataStore.coverBackgroundClarity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFloatingLyricsEnabled(enabled) }
    }

    fun updateFloatingLyricsSettings(settings: FloatingLyricsSettings) {
        viewModelScope.launch { settingsRepository.updateFloatingLyricsSettings(settings) }
    }

    fun setDynamicPlayerHueEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDynamicPlayerHueEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsDataStore.setTheme(mode) }
    }

    fun setStaticHueArgb(argb: Int?) {
        viewModelScope.launch { settingsDataStore.setStaticHueArgb(argb) }
    }

    fun setCoverBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCoverBackgroundEnabled(enabled) }
    }

    fun setCoverBackgroundClarity(clarity: Float) {
        viewModelScope.launch { settingsDataStore.setCoverBackgroundClarity(clarity) }
    }
}
