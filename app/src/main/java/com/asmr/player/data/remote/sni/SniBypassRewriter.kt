package com.asmr.player.data.remote.sni

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SniBypassRewriter {
    fun normalizeProxyBaseUrl(raw: String): HttpUrl? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val withSlash = if (s.endsWith("/")) s else "$s/"
        return withSlash.toHttpUrlOrNull()
    }

    fun matchesAny(host: String, rules: List<SniBypassRule>): Boolean {
        if (host.isBlank()) return false
        val h = host.lowercase()
        return rules.any { rule ->
            if (!rule.enabled) return@any false
            val p0 = rule.pattern.trim().lowercase()
            if (p0.isBlank()) return@any false
            val p = p0.removePrefix("*.").removePrefix(".")
            h == p || h.endsWith(".$p")
        }
    }

    fun rewrite(proxy: HttpUrl, original: HttpUrl, targetHost: String, mode: SniBypassMode): HttpUrl? {
        val host = targetHost.trim().lowercase()
        if (host.isBlank()) return null
        return when (mode) {
            SniBypassMode.HostHeader -> {
                proxy.newBuilder()
                    .encodedPath(original.encodedPath)
                    .encodedQuery(original.encodedQuery)
                    .build()
            }
            SniBypassMode.PathEncoded -> {
                val path = "/" + host + original.encodedPath
                proxy.newBuilder()
                    .encodedPath(path)
                    .encodedQuery(original.encodedQuery)
                    .build()
            }
        }
    }
}

