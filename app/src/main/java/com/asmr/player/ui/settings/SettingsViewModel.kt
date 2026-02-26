package com.asmr.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.dns.DnsBypassManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient,
    private val dnsBypassManager: DnsBypassManager
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

    val dnsBypassEnabled: StateFlow<Boolean> = settingsRepository.dnsBypassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val dnsDohEnabled: StateFlow<Boolean> = settingsRepository.dnsDohEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _dnsDiagnostics = MutableStateFlow<String?>(null)
    val dnsDiagnostics: StateFlow<String?> = _dnsDiagnostics.asStateFlow()

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

    fun setDnsBypassEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDnsBypassEnabled(enabled) }
    }

    fun setDnsDohEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDnsDohEnabled(enabled) }
    }

    fun runDnsDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val client = okHttpClient.newBuilder()
                .callTimeout(12, TimeUnit.SECONDS)
                .build()

            val sb = StringBuilder()
            sb.appendLine("DNS 诊断中…")
            sb.appendLine("开启：${dnsBypassEnabled.value}")
            sb.appendLine("DoH：${dnsDohEnabled.value}")
            _dnsDiagnostics.value = sb.toString().trim()

            val targets = listOf(
                "www.dlsite.com",
                "ssl.dlsite.com",
                "play.dlsite.com",
                "chobit.cc"
            )
            targets.forEach { host ->
                sb.appendLine()
                sb.appendLine("解析：$host")
                val ips = runCatching { dnsBypassManager.getDns().lookup(host) }.getOrDefault(emptyList())
                if (ips.isEmpty()) {
                    sb.appendLine("结果：(空)")
                } else {
                    sb.appendLine("结果：${ips.joinToString(", ") { it.hostAddress.orEmpty() }}")
                }

                val request = Request.Builder()
                    .url("https://$host/")
                    .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                    .head()
                    .build()

                val result = runCatching {
                    client.newCall(request).execute().use { resp ->
                        "请求结果：HTTP ${resp.code}"
                    }
                }.getOrElse { e ->
                    val msg = when (e) {
                        is IOException -> e.message.orEmpty().ifBlank { "IO 异常" }
                        else -> e.message.orEmpty().ifBlank { e::class.java.simpleName }
                    }
                    "请求失败：$msg"
                }
                sb.appendLine(result)
                _dnsDiagnostics.value = sb.toString().trim()
            }
            _dnsDiagnostics.value = sb.toString().trim()
        }
    }
}
