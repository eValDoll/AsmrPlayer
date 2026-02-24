package com.asmr.player.cache

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.IntSize
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class ImageLoaderFacade(
    private val context: Context,
    okHttpClient: OkHttpClient
) {
    private val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .okHttpClient { okHttpClient }
        .memoryCache(null)
        .diskCache(null)
        .build()

    suspend fun loadBitmap(model: Any, size: IntSize?): Bitmap = withContext(Dispatchers.IO) {
        val request = buildRequest(model, size)
        val result = imageLoader.execute(request)
        if (result is SuccessResult) {
            result.drawable.toBitmap().also { it.prepareToDraw() }
        } else {
            throw IllegalStateException("Image load failed: ${result::class.java.simpleName}")
        }
    }

    private fun buildRequest(model: Any, size: IntSize?): ImageRequest {
        val b = ImageRequest.Builder(context)
            .data(
                when (model) {
                    is CacheImageModel -> model.data
                    else -> model
                }
            )
            .allowHardware(false)
        val headers = (model as? CacheImageModel)?.headers.orEmpty()
        if (headers.isNotEmpty()) {
            headers.forEach { (k, v) -> b.addHeader(k, v) }
        }
        if (size != null && size.width > 0 && size.height > 0) {
            b.size(size.width, size.height)
        }
        return b.build()
    }
}

