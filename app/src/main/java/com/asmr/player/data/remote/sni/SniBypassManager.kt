package com.asmr.player.data.remote.sni

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import com.asmr.player.data.settings.SettingsKeys
import com.asmr.player.data.settings.settingsDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SniBypassManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentConfig: SniBypassConfig = defaultConfig()

    init {
        scope.launch {
            context.settingsDataStore.data
                .map { prefs -> decodeConfig(prefs) }
                .collect { cfg -> currentConfig = cfg }
        }
    }

    fun getConfig(): SniBypassConfig = currentConfig

    fun preview(url: String): SniBypassPreview {
        val cfg = currentConfig
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null) {
            return SniBypassPreview(
                enabled = cfg.enabled,
                matched = false,
                mode = cfg.mode,
                originalUrl = url,
                rewrittenUrl = null,
                targetHost = null,
                failureReason = "URL 无效"
            )
        }

        val host = parsed.host.lowercase()
        val matched = cfg.enabled && SniBypassRewriter.matchesAny(host, cfg.rules)
        if (!matched) {
            return SniBypassPreview(
                enabled = cfg.enabled,
                matched = false,
                mode = cfg.mode,
                originalUrl = parsed.toString(),
                rewrittenUrl = null,
                targetHost = null,
                failureReason = if (!cfg.enabled) "未开启" else "未命中域名映射"
            )
        }

        val proxy = SniBypassRewriter.normalizeProxyBaseUrl(cfg.proxyBaseUrl)
        if (proxy == null) {
            return SniBypassPreview(
                enabled = cfg.enabled,
                matched = true,
                mode = cfg.mode,
                originalUrl = parsed.toString(),
                rewrittenUrl = null,
                targetHost = host,
                failureReason = "代理入口无效"
            )
        }

        val rewritten = SniBypassRewriter.rewrite(proxy, parsed, host, cfg.mode)?.toString()
        return SniBypassPreview(
            enabled = cfg.enabled,
            matched = true,
            mode = cfg.mode,
            originalUrl = parsed.toString(),
            rewrittenUrl = rewritten,
            targetHost = host,
            failureReason = if (rewritten == null) "重写失败" else null
        )
    }

    fun rewriteOkHttpRequest(request: Request): Request {
        val cfg = currentConfig
        if (!cfg.enabled) return request

        val host = request.url.host.lowercase()
        if (!SniBypassRewriter.matchesAny(host, cfg.rules)) return request

        val proxy = SniBypassRewriter.normalizeProxyBaseUrl(cfg.proxyBaseUrl) ?: return request
        val newUrl = SniBypassRewriter.rewrite(proxy, request.url, host, cfg.mode) ?: return request

        val builder = request.newBuilder().url(newUrl)
        when (cfg.mode) {
            SniBypassMode.HostHeader -> builder.header("Host", host)
            SniBypassMode.PathEncoded -> {}
        }
        return builder.build()
    }

    fun rewriteUri(uri: Uri): Pair<Uri, Map<String, String>>? {
        val cfg = currentConfig
        if (!cfg.enabled) return null

        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank() || !SniBypassRewriter.matchesAny(host, cfg.rules)) return null

        val proxy = SniBypassRewriter.normalizeProxyBaseUrl(cfg.proxyBaseUrl) ?: return null
        val original = uri.toString().toHttpUrlOrNull() ?: return null
        val newUrl = SniBypassRewriter.rewrite(proxy, original, host, cfg.mode) ?: return null

        val headers = when (cfg.mode) {
            SniBypassMode.HostHeader -> mapOf("Host" to host)
            SniBypassMode.PathEncoded -> emptyMap()
        }
        return Uri.parse(newUrl.toString()) to headers
    }

    fun rewriteJsoupUrl(url: String): Pair<String, Map<String, String>>? {
        val cfg = currentConfig
        if (!cfg.enabled) return null

        val parsed = url.toHttpUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        if (!SniBypassRewriter.matchesAny(host, cfg.rules)) return null

        val proxy = SniBypassRewriter.normalizeProxyBaseUrl(cfg.proxyBaseUrl) ?: return null
        val newUrl = SniBypassRewriter.rewrite(proxy, parsed, host, cfg.mode) ?: return null
        val headers = when (cfg.mode) {
            SniBypassMode.HostHeader -> mapOf("Host" to host)
            SniBypassMode.PathEncoded -> emptyMap()
        }
        return newUrl.toString() to headers
    }

    private fun decodeConfig(prefs: Preferences): SniBypassConfig {
        val enabled = prefs[SettingsKeys.SNI_BYPASS_ENABLED] ?: false
        val legacyProxy = prefs[SettingsKeys.SNI_BYPASS_PROXY_BASE_URL].orEmpty()
        val source = prefs[SettingsKeys.SNI_BYPASS_PROXY_SOURCE]?.let { SniBypassProxySource.fromCode(it) }
            ?: if (legacyProxy.isNotBlank()) SniBypassProxySource.Custom else SniBypassProxySource.BuiltIn
        val customProxy = prefs[SettingsKeys.SNI_BYPASS_PROXY_CUSTOM_BASE_URL].orEmpty().ifBlank { legacyProxy }
        val proxy = when (source) {
            SniBypassProxySource.BuiltIn -> SniBypassDefaults.BUILTIN_PROXY_BASE_URL
            SniBypassProxySource.Custom -> customProxy.ifBlank { SniBypassDefaults.BUILTIN_PROXY_BASE_URL }
        }
        val modeCode = prefs[SettingsKeys.SNI_BYPASS_MODE] ?: SniBypassMode.HostHeader.code
        val rulesJson = prefs[SettingsKeys.SNI_BYPASS_RULES].orEmpty()
        val rules = decodeRules(rulesJson).ifEmpty { defaultRules() }
        return SniBypassConfig(
            enabled = enabled,
            proxyBaseUrl = proxy,
            mode = SniBypassMode.fromCode(modeCode),
            rules = rules
        )
    }

    private fun decodeRules(json: String): List<SniBypassRule> {
        val clean = json.trim()
        if (clean.isBlank()) return emptyList()
        val parsed = runCatching { gson.fromJson(clean, Array<SniBypassRule>::class.java)?.toList() }.getOrNull()
        return parsed.orEmpty()
            .mapNotNull { r ->
                val p = r.pattern.trim().lowercase()
                if (p.isBlank()) null else r.copy(pattern = p)
            }
    }

    private fun defaultConfig(): SniBypassConfig = SniBypassConfig(
        enabled = false,
        proxyBaseUrl = SniBypassDefaults.BUILTIN_PROXY_BASE_URL,
        mode = SniBypassMode.HostHeader,
        rules = defaultRules()
    )

    private fun defaultRules(): List<SniBypassRule> = listOf(
        SniBypassRule("dlsite.com", true),
        SniBypassRule("chobit.cc", true),
        SniBypassRule("byteair.volces.com", true)
    )
}
