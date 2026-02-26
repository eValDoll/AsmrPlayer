package com.asmr.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
    fun eqBandLevel(index: Int) = intPreferencesKey("eq_band_$index")

    val LIBRARY_VIEW_MODE = intPreferencesKey("library_view_mode")

    val PLAY_MODE = intPreferencesKey("play_mode")

    val ASMR_ONE_SITE = intPreferencesKey("asmr_one_site")

    val FLOATING_LYRICS_ENABLED = booleanPreferencesKey("floating_lyrics_enabled")
    val FLOATING_LYRICS_COLOR = intPreferencesKey("floating_lyrics_color")
    val FLOATING_LYRICS_SIZE = floatPreferencesKey("floating_lyrics_size")
    val FLOATING_LYRICS_OPACITY = floatPreferencesKey("floating_lyrics_opacity")
    val FLOATING_LYRICS_Y = intPreferencesKey("floating_lyrics_y")
    val FLOATING_LYRICS_ALIGN = intPreferencesKey("floating_lyrics_align")
    val FLOATING_LYRICS_TOUCHABLE = booleanPreferencesKey("floating_lyrics_touchable")

    // Enhanced EQ & Audio Effects
    val EQ_VIRTUALIZER_STRENGTH = intPreferencesKey("eq_virtualizer_strength") // 0-1000
    val EQ_BALANCE = floatPreferencesKey("eq_balance") // -1.0 to 1.0
    val EQ_PRESET_NAME = stringPreferencesKey("eq_preset_name")
    val CUSTOM_EQ_PRESETS = stringPreferencesKey("custom_eq_presets_json")

    val FX_ORIGINAL_GAIN = floatPreferencesKey("fx_original_gain") // 0.0-2.0

    val FX_REVERB_ENABLED = booleanPreferencesKey("fx_reverb_enabled")
    val FX_REVERB_PRESET = stringPreferencesKey("fx_reverb_preset")
    val FX_REVERB_WET = intPreferencesKey("fx_reverb_wet") // 0-100

    val FX_ORBIT_ENABLED = booleanPreferencesKey("fx_orbit_enabled")
    val FX_ORBIT_SPEED = floatPreferencesKey("fx_orbit_speed") // 0-50
    val FX_ORBIT_DISTANCE = floatPreferencesKey("fx_orbit_distance") // 0-10

    val SNI_BYPASS_ENABLED = booleanPreferencesKey("sni_bypass_enabled")
    val SNI_BYPASS_PROXY_BASE_URL = stringPreferencesKey("sni_bypass_proxy_base_url")
    val SNI_BYPASS_PROXY_SOURCE = intPreferencesKey("sni_bypass_proxy_source")
    val SNI_BYPASS_PROXY_CUSTOM_BASE_URL = stringPreferencesKey("sni_bypass_proxy_custom_base_url")
    val SNI_BYPASS_MODE = intPreferencesKey("sni_bypass_mode")
    val SNI_BYPASS_RULES = stringPreferencesKey("sni_bypass_rules_json")

    val DNS_BYPASS_ENABLED = booleanPreferencesKey("dns_bypass_enabled")
    val DNS_BYPASS_HOSTS = stringPreferencesKey("dns_bypass_hosts_text")
    val DNS_DOH_ENABLED = booleanPreferencesKey("dns_doh_enabled")
}
