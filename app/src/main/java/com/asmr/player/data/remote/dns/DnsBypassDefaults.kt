package com.asmr.player.data.remote.dns

object DnsBypassDefaults {
    val defaultDohSuffixes: List<String> = listOf(
        "dlsite.com",
        "chobit.cc"
    )

    val defaultHostsText: String = """
        104.18.3.173 www.dlsite.com
        104.18.2.173 www.dlsite.com
        104.18.3.173 ssl.dlsite.com
        104.18.2.173 ssl.dlsite.com
        18.178.19.156 play.dlsite.com
        52.196.205.186 play.dlsite.com
        35.72.203.42 login.dlsite.com
        3.112.120.146 login.dlsite.com
        3.169.55.3 download.dlsite.com
        3.169.55.49 download.dlsite.com
        3.169.55.112 download.dlsite.com
        54.178.187.141 webup.dlsite.com
        57.181.47.167 webup.dlsite.com
    """.trimIndent()
}
