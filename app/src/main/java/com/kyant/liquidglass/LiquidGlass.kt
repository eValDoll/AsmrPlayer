package com.kyant.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.asmr.player.ui.theme.AsmrTheme

private val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            background()
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            content()
        }
    }
}

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    blurRadius: Dp = 12.dp,
    vibrancyEnabled: Boolean = false,
    backdropFillAlpha: Float = 0.98f,
    refractionHeight: Dp = 24.dp,
    refractionAmount: Dp = 32.dp,
    chromaticAberration: Boolean = true,
    tint: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    highlight: Highlight? = Highlight.Default,
    shadow: Shadow? = Shadow.Default,
    innerShadow: InnerShadow? = null,
    exportedBackdrop: LayerBackdrop? = null,
    backdrop: Backdrop? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = backdrop ?: LocalBackdrop.current
    val clipped = modifier.clip(shape)
    val lensSupported = shape is CornerBasedShape
    val isDark = AsmrTheme.colorScheme.isDark
    val resolvedTint =
        tint ?: if (isDark) {
            Color.Black.copy(alpha = 0.45f)
        } else {
            Color.White.copy(alpha = 0.55f)
        }
    val resolvedBorderColor =
        borderColor ?: if (isDark) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.35f)
        }
    val resolvedShadow =
        if (shadowElevation > 0.dp) {
            (shadow ?: Shadow()).copy(radius = shadowElevation)
        } else {
            shadow
        }
    val glassModifier =
        if (backdrop != null) {
            clipped.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (blurRadius > 0.dp) {
                        if (vibrancyEnabled) vibrancy()
                        blur(blurRadius.toPx())
                    }
                    if (lensSupported && (refractionHeight > 0.dp || refractionAmount > 0.dp)) {
                        lens(
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            chromaticAberration = chromaticAberration
                        )
                    }
                },
                highlight = { highlight },
                shadow = { resolvedShadow },
                innerShadow = if (innerShadow != null) ({ innerShadow }) else null,
                exportedBackdrop = exportedBackdrop,
                onDrawBackdrop = { drawBackdrop ->
                    if (backdropFillAlpha > 0f) {
                        drawRect(
                            color = resolvedTint.copy(alpha = backdropFillAlpha),
                            blendMode = BlendMode.SrcOver
                        )
                    }
                    drawBackdrop()
                },
                onDrawSurface = {
                    drawRect(resolvedTint)
                    drawRect(resolvedBorderColor, style = Stroke(width = borderWidth.toPx()))
                }
            )
        } else {
            clipped
                .background(resolvedTint)
                .border(borderWidth, resolvedBorderColor, shape)
        }

    Box(glassModifier) {
        if (backdrop != null && exportedBackdrop != null) {
            CompositionLocalProvider(LocalBackdrop provides exportedBackdrop) {
                content()
            }
        } else {
            content()
        }
    }
}
