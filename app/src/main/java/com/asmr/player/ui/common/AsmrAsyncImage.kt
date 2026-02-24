package com.asmr.player.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext

@Composable
fun AsmrAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    placeholderCornerRadius: Int = 6,
    placeholder: @Composable (Modifier) -> Unit = { m ->
        DiscPlaceholder(modifier = m, cornerRadius = placeholderCornerRadius)
    },
) {
    val normalizedModel = remember(model) { normalizeImageModel(model) }
    if (normalizedModel == null) {
        placeholder(modifier)
        return
    }

    val ctx = LocalContext.current.applicationContext
    val manager = remember(ctx) {
        EntryPointAccessors.fromApplication(ctx, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val measuredSize: MutableState<IntSize?> = remember { mutableStateOf(null) }
    val painter: MutableState<Painter?> = remember(normalizedModel) { mutableStateOf(null) }
    val sizedModifier = modifier.onSizeChanged { sz ->
        if (sz.width > 0 && sz.height > 0) measuredSize.value = IntSize(sz.width, sz.height)
    }

    LaunchedEffect(normalizedModel, measuredSize.value) {
        val sz = measuredSize.value ?: return@LaunchedEffect
        runCatching {
            val img = manager.loadImage(model = normalizedModel, size = sz, cachePolicy = CachePolicy.DEFAULT)
            painter.value = BitmapPainter(img)
        }.onFailure {
            painter.value = null
        }
    }

    val p = painter.value
    if (p == null) {
        placeholder(sizedModifier)
        return
    }
    Image(
        painter = p,
        contentDescription = contentDescription,
        modifier = sizedModifier,
        contentScale = contentScale,
        alignment = alignment,
        alpha = alpha,
        colorFilter = colorFilter
    )
}

private fun normalizeImageModel(model: Any?): Any? {
    return when (model) {
        is String -> model.trim().takeIf { it.isNotEmpty() }
        else -> model
    }
}
