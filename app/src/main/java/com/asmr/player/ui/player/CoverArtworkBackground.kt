package com.asmr.player.ui.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import kotlin.math.max
import kotlin.math.min

@Composable
fun CoverArtworkBackground(
    artworkModel: Any?,
    enabled: Boolean,
    clarity: Float,
    overlayBaseColor: Color,
    tintBaseColor: Color,
    isDark: Boolean = true
) {
    if (!enabled || artworkModel == null) return
    val context = LocalContext.current

    val c = remember(clarity) { min(1f, max(0f, clarity)) }
    val blurDp: Dp = remember(c) { (2f + (1f - c) * 18f).dp }
    
    // 基础图片透明度
    val alpha = remember(c) { 0.30f * c }
    
    // 背景叠加透明度：深色模式下稍微增加基础叠加，确保更暗
    val overlayAlpha = remember(c, isDark) { 
        val base = if (isDark) 0.35f else 0.20f
        base + 0.20f * c 
    }
    
    // 氛围色透明度：深色模式下降低氛围色的权重，防止过亮
    val tintAlpha = remember(c, isDark) {
        val maxTint = if (isDark) 0.62f else 0.72f
        maxTint * (1f - c) + 0.12f * c
    }
    
    val artworkModifier = remember(blurDp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                val blurPx = blurDp.toPx()
                renderEffect = RenderEffect
                    .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier.blur(blurDp)
        }
    }

    val request = remember(artworkModel) {
        ImageRequest.Builder(context)
            .data(artworkModel)
            .size(384)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alpha = alpha
    ) {
        val s = painter.state
        if (s is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize().then(artworkModifier))
        }
    }
    
    // 叠加层 1: 基础背景色叠加 (Overlay)
    Box(modifier = Modifier.fillMaxSize().background(overlayBaseColor.copy(alpha = overlayAlpha)))
    
    // 叠加层 2: 氛围色/主导色叠加 (Tint)
    Box(modifier = Modifier.fillMaxSize().background(tintBaseColor.copy(alpha = tintAlpha)))
    
    // 叠加层 3: 深色模式额外压暗层 (Scrim) - 确保在任何氛围色下文字都有足够对比度
    if (isDark) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
    }
}
