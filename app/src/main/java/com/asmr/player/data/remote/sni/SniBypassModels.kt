package com.asmr.player.data.remote.sni

enum class SniBypassMode(val code: Int) {
    HostHeader(0),
    PathEncoded(1);

    companion object {
        fun fromCode(code: Int): SniBypassMode = entries.firstOrNull { it.code == code } ?: HostHeader
    }
}

data class SniBypassRule(
    val pattern: String,
    val enabled: Boolean = true
)

data class SniBypassConfig(
    val enabled: Boolean,
    val proxyBaseUrl: String,
    val mode: SniBypassMode,
    val rules: List<SniBypassRule>
)

data class SniBypassPreview(
    val enabled: Boolean,
    val matched: Boolean,
    val mode: SniBypassMode,
    val originalUrl: String,
    val rewrittenUrl: String?,
    val targetHost: String?,
    val failureReason: String?
)

