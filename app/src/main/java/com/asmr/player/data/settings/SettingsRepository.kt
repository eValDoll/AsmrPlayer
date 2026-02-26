package com.asmr.player.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.asmr.player.data.remote.sni.SniBypassMode
import com.asmr.player.data.remote.sni.SniBypassDefaults
import com.asmr.player.data.remote.sni.SniBypassProxySource
import com.asmr.player.data.remote.sni.SniBypassRule
import com.asmr.player.data.remote.dns.DnsBypassDefaults
import com.google.gson.Gson

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    val libraryViewMode: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.LIBRARY_VIEW_MODE] ?: 0
    }

    val playMode: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.PLAY_MODE] ?: 0
    }

    val asmrOneSite: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.ASMR_ONE_SITE] ?: 200
    }

    val floatingLyricsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.FLOATING_LYRICS_ENABLED] ?: false
    }

    val floatingLyricsSettings: Flow<FloatingLyricsSettings> = context.settingsDataStore.data.map { prefs ->
        FloatingLyricsSettings(
            color = prefs[SettingsKeys.FLOATING_LYRICS_COLOR] ?: 0xFFFFFFFF.toInt(),
            size = prefs[SettingsKeys.FLOATING_LYRICS_SIZE] ?: 16f,
            opacity = prefs[SettingsKeys.FLOATING_LYRICS_OPACITY] ?: 0.7f,
            yOffset = prefs[SettingsKeys.FLOATING_LYRICS_Y] ?: 120,
            align = prefs[SettingsKeys.FLOATING_LYRICS_ALIGN] ?: 1, // 0:Left, 1:Center, 2:Right
            touchable = prefs[SettingsKeys.FLOATING_LYRICS_TOUCHABLE] ?: true
        )
    }

    val equalizerSettings: Flow<EqualizerSettings> = context.settingsDataStore.data.map { prefs ->
        val enabled = prefs[SettingsKeys.EQ_ENABLED] ?: false
        val levels = (0 until 10).map { idx -> prefs[SettingsKeys.eqBandLevel(idx)] ?: 0 }
        val virt = prefs[SettingsKeys.EQ_VIRTUALIZER_STRENGTH] ?: 0
        val bal = prefs[SettingsKeys.EQ_BALANCE] ?: 0f
        val preset = prefs[SettingsKeys.EQ_PRESET_NAME] ?: "默认"
        val gain = prefs[SettingsKeys.FX_ORIGINAL_GAIN] ?: 1f
        val reverbEnabled = prefs[SettingsKeys.FX_REVERB_ENABLED] ?: false
        val reverbPreset = prefs[SettingsKeys.FX_REVERB_PRESET] ?: "无"
        val reverbWet = prefs[SettingsKeys.FX_REVERB_WET] ?: 0
        val orbitEnabled = prefs[SettingsKeys.FX_ORBIT_ENABLED] ?: false
        val orbitSpeed = prefs[SettingsKeys.FX_ORBIT_SPEED] ?: 25f
        val orbitDistance = prefs[SettingsKeys.FX_ORBIT_DISTANCE] ?: 5f
        EqualizerSettings(
            enabled = enabled,
            bandLevels = levels,
            virtualizerStrength = virt,
            balance = bal,
            presetName = preset,
            originalGain = gain,
            reverbEnabled = reverbEnabled,
            reverbPreset = reverbPreset,
            reverbWet = reverbWet,
            orbitEnabled = orbitEnabled,
            orbitSpeed = orbitSpeed,
            orbitDistance = orbitDistance
        )
    }

    val customEqualizerPresets: Flow<List<AsmrPreset>> = context.settingsDataStore.data.map { prefs ->
        EqualizerPresets.decodeCustomPresets(prefs[SettingsKeys.CUSTOM_EQ_PRESETS])
    }

    val sniBypassEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.SNI_BYPASS_ENABLED] ?: false
    }

    val sniBypassProxySource: Flow<SniBypassProxySource> = context.settingsDataStore.data.map { prefs ->
        val legacy = prefs[SettingsKeys.SNI_BYPASS_PROXY_BASE_URL].orEmpty()
        val source = prefs[SettingsKeys.SNI_BYPASS_PROXY_SOURCE]?.let { SniBypassProxySource.fromCode(it) }
        source ?: if (legacy.isNotBlank()) SniBypassProxySource.Custom else SniBypassProxySource.BuiltIn
    }

    val sniBypassProxyCustomBaseUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val legacy = prefs[SettingsKeys.SNI_BYPASS_PROXY_BASE_URL].orEmpty()
        prefs[SettingsKeys.SNI_BYPASS_PROXY_CUSTOM_BASE_URL].orEmpty().ifBlank { legacy }
    }

    val sniBypassProxyBaseUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val legacy = prefs[SettingsKeys.SNI_BYPASS_PROXY_BASE_URL].orEmpty()
        val source = prefs[SettingsKeys.SNI_BYPASS_PROXY_SOURCE]?.let { SniBypassProxySource.fromCode(it) }
            ?: if (legacy.isNotBlank()) SniBypassProxySource.Custom else SniBypassProxySource.BuiltIn
        val custom = prefs[SettingsKeys.SNI_BYPASS_PROXY_CUSTOM_BASE_URL].orEmpty().ifBlank { legacy }
        when (source) {
            SniBypassProxySource.BuiltIn -> SniBypassDefaults.BUILTIN_PROXY_BASE_URL
            SniBypassProxySource.Custom -> custom.ifBlank { SniBypassDefaults.BUILTIN_PROXY_BASE_URL }
        }
    }

    val sniBypassMode: Flow<SniBypassMode> = context.settingsDataStore.data.map { prefs ->
        SniBypassMode.fromCode(prefs[SettingsKeys.SNI_BYPASS_MODE] ?: SniBypassMode.HostHeader.code)
    }

    val sniBypassRules: Flow<List<SniBypassRule>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[SettingsKeys.SNI_BYPASS_RULES].orEmpty()
        decodeRules(json).ifEmpty { defaultRules() }
    }

    val dnsBypassEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.DNS_BYPASS_ENABLED] ?: false
    }

    val dnsDohEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.DNS_DOH_ENABLED] ?: true
    }

    val dnsBypassHostsText: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.DNS_BYPASS_HOSTS].orEmpty().ifBlank { DnsBypassDefaults.defaultHostsText }
    }

    suspend fun setFloatingLyricsEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.FLOATING_LYRICS_ENABLED] = enabled }
        }
    }

    suspend fun updateFloatingLyricsSettings(settings: FloatingLyricsSettings) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit {
                it[SettingsKeys.FLOATING_LYRICS_COLOR] = settings.color
                it[SettingsKeys.FLOATING_LYRICS_SIZE] = settings.size
                it[SettingsKeys.FLOATING_LYRICS_OPACITY] = settings.opacity
                it[SettingsKeys.FLOATING_LYRICS_Y] = settings.yOffset
                it[SettingsKeys.FLOATING_LYRICS_ALIGN] = settings.align
                it[SettingsKeys.FLOATING_LYRICS_TOUCHABLE] = settings.touchable
            }
        }
    }

    suspend fun updateEqualizerSettings(settings: EqualizerSettings) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.EQ_ENABLED] = settings.enabled
                settings.bandLevels.take(10).forEachIndexed { index, level ->
                    prefs[SettingsKeys.eqBandLevel(index)] = level
                }
                prefs[SettingsKeys.EQ_VIRTUALIZER_STRENGTH] = settings.virtualizerStrength
                prefs[SettingsKeys.EQ_BALANCE] = settings.balance
                prefs[SettingsKeys.EQ_PRESET_NAME] = settings.presetName
                prefs[SettingsKeys.FX_ORIGINAL_GAIN] = settings.originalGain
                prefs[SettingsKeys.FX_REVERB_ENABLED] = settings.reverbEnabled
                prefs[SettingsKeys.FX_REVERB_PRESET] = settings.reverbPreset
                prefs[SettingsKeys.FX_REVERB_WET] = settings.reverbWet
                prefs[SettingsKeys.FX_ORBIT_ENABLED] = settings.orbitEnabled
                prefs[SettingsKeys.FX_ORBIT_SPEED] = settings.orbitSpeed
                prefs[SettingsKeys.FX_ORBIT_DISTANCE] = settings.orbitDistance
            }
        }
    }

    suspend fun saveCustomPreset(preset: AsmrPreset) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { prefs ->
                val current = EqualizerPresets.decodeCustomPresets(prefs[SettingsKeys.CUSTOM_EQ_PRESETS]).toMutableList()
                current.removeAll { it.name == preset.name }
                current.add(preset.copy(isCustom = true))
                prefs[SettingsKeys.CUSTOM_EQ_PRESETS] = EqualizerPresets.encodeCustomPresets(current)
            }
        }
    }

    suspend fun deleteCustomPreset(name: String) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { prefs ->
                val current = EqualizerPresets.decodeCustomPresets(prefs[SettingsKeys.CUSTOM_EQ_PRESETS]).toMutableList()
                current.removeAll { it.name == name }
                prefs[SettingsKeys.CUSTOM_EQ_PRESETS] = EqualizerPresets.encodeCustomPresets(current)
            }
        }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.EQ_ENABLED] = enabled }
        }
    }

    suspend fun setBandLevel(index: Int, level: Int) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.eqBandLevel(index)] = level }
        }
    }

    suspend fun setLibraryViewMode(mode: Int) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.LIBRARY_VIEW_MODE] = mode }
        }
    }

    suspend fun setPlayMode(mode: Int) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.PLAY_MODE] = mode }
        }
    }

    suspend fun setAsmrOneSite(site: Int) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.ASMR_ONE_SITE] = site }
        }
    }

    suspend fun setSniBypassEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.SNI_BYPASS_ENABLED] = enabled }
        }
    }

    suspend fun setSniBypassProxyBaseUrl(url: String) {
        val clean = url.trim()
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit {
                it[SettingsKeys.SNI_BYPASS_PROXY_SOURCE] = SniBypassProxySource.Custom.code
                it[SettingsKeys.SNI_BYPASS_PROXY_CUSTOM_BASE_URL] = clean
                it[SettingsKeys.SNI_BYPASS_PROXY_BASE_URL] = clean
            }
        }
    }

    suspend fun setSniBypassProxySource(source: SniBypassProxySource) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit {
                it[SettingsKeys.SNI_BYPASS_PROXY_SOURCE] = source.code
            }
        }
    }

    suspend fun useBuiltInSniProxy() {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit {
                it[SettingsKeys.SNI_BYPASS_PROXY_SOURCE] = SniBypassProxySource.BuiltIn.code
            }
        }
    }

    suspend fun setSniBypassMode(mode: SniBypassMode) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.SNI_BYPASS_MODE] = mode.code }
        }
    }

    suspend fun setSniBypassRules(rules: List<SniBypassRule>) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.SNI_BYPASS_RULES] = gson.toJson(rules)
            }
        }
    }

    suspend fun setDnsBypassEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.DNS_BYPASS_ENABLED] = enabled }
        }
    }

    suspend fun setDnsBypassHostsText(text: String) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.DNS_BYPASS_HOSTS] = text.trim() }
        }
    }

    suspend fun setDnsDohEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.settingsDataStore.edit { it[SettingsKeys.DNS_DOH_ENABLED] = enabled }
        }
    }

    private fun decodeRules(json: String): List<SniBypassRule> {
        val clean = json.trim()
        if (clean.isBlank()) return emptyList()
        val parsed = runCatching { gson.fromJson(clean, Array<SniBypassRule>::class.java)?.toList() }.getOrNull()
        return parsed.orEmpty().mapNotNull { r ->
            val p = r.pattern.trim().lowercase()
            if (p.isBlank()) null else r.copy(pattern = p)
        }
    }

    private fun defaultRules(): List<SniBypassRule> = listOf(
        SniBypassRule("dlsite.com", true),
        SniBypassRule("chobit.cc", true),
        SniBypassRule("byteair.volces.com", true)
    )

}
