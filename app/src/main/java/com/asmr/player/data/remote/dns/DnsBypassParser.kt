package com.asmr.player.data.remote.dns

import java.net.InetAddress

object DnsBypassParser {
    fun parseHostsText(text: String): Map<String, List<InetAddress>> {
        val map = LinkedHashMap<String, MutableList<InetAddress>>()
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }
            .forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) return@forEach
                val ip = parts[0].trim()
                val host = parts[1].trim().lowercase()
                if (host.isBlank()) return@forEach
                val addr = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return@forEach
                map.getOrPut(host) { mutableListOf() }.add(addr)
            }
        return map
    }
}

