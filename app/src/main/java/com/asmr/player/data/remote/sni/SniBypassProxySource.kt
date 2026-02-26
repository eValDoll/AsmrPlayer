package com.asmr.player.data.remote.sni

enum class SniBypassProxySource(val code: Int) {
    BuiltIn(0),
    Custom(1);

    companion object {
        fun fromCode(code: Int): SniBypassProxySource = entries.firstOrNull { it.code == code } ?: BuiltIn
    }
}

