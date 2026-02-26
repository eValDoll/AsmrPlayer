package com.asmr.player.data.remote.sni

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SniBypassInterceptor @Inject constructor(
    private val manager: SniBypassManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = manager.rewriteOkHttpRequest(chain.request())
        return chain.proceed(request)
    }
}

