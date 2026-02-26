package com.asmr.player.data.remote.dns

import android.content.Context
import com.asmr.player.data.settings.SettingsKeys
import com.asmr.player.data.settings.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsBypassManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var dohEnabled: Boolean = true

    @Volatile
    private var hostsMap: Map<String, List<InetAddress>> = DnsBypassParser.parseHostsText(DnsBypassDefaults.defaultHostsText)

    private val dohDnsProviders: List<Dns> by lazy {
        val bootstrapClient = OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .build()

        val cfBootstrap = listOf(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1")
        )
        val googleBootstrap = listOf(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4")
        )

        val cloudflare = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(cfBootstrap)
            .build()

        val google = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(googleBootstrap)
            .build()

        listOf(cloudflare, google)
    }

    init {
        scope.launch {
            context.settingsDataStore.data
                .map { prefs ->
                    val en = prefs[SettingsKeys.DNS_BYPASS_ENABLED] ?: false
                    val dohEn = prefs[SettingsKeys.DNS_DOH_ENABLED] ?: true
                    val text = prefs[SettingsKeys.DNS_BYPASS_HOSTS].orEmpty()
                    val map = if (text.isBlank()) {
                        DnsBypassParser.parseHostsText(DnsBypassDefaults.defaultHostsText)
                    } else {
                        DnsBypassParser.parseHostsText(text)
                    }
                    Triple(en, dohEn, map)
                }
                .collect { (en, dohEn, map) ->
                    enabled = en
                    dohEnabled = dohEn
                    hostsMap = map
                }
        }
    }

    fun getDns(): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (!enabled && !dohEnabled) return reorder(Dns.SYSTEM.lookup(hostname))
            val host = hostname.lowercase()
            if (enabled) {
                val direct = hostsMap[host]
                if (!direct.isNullOrEmpty()) return direct
                val suffix = hostsMap.entries.firstOrNull { (k, _) -> host == k || host.endsWith(".$k") }?.value
                if (!suffix.isNullOrEmpty()) return suffix
            }
            if (dohEnabled && shouldUseDoh(host)) {
                val doh = dohLookupOrNull(hostname)
                if (!doh.isNullOrEmpty()) return reorder(doh)
            }
            return reorder(Dns.SYSTEM.lookup(hostname))
        }
    }

    private fun shouldUseDoh(host: String): Boolean {
        return DnsBypassDefaults.defaultDohSuffixes.any { s ->
            host == s || host.endsWith(".$s")
        }
    }

    private fun dohLookupOrNull(hostname: String): List<InetAddress>? {
        for (dns in dohDnsProviders) {
            val resolved = runCatching { dns.lookup(hostname) }.getOrNull()
            if (!resolved.isNullOrEmpty()) return resolved
        }
        return null
    }

    private fun reorder(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.size <= 1) return addresses
        return addresses.sortedWith { a, b ->
            val ar = when (a) {
                is Inet4Address -> 0
                is Inet6Address -> 1
                else -> 2
            }
            val br = when (b) {
                is Inet4Address -> 0
                is Inet6Address -> 1
                else -> 2
            }
            ar - br
        }
    }
}
