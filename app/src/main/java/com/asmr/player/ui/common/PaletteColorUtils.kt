package com.asmr.player.ui.common

import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

internal fun pickBestColorInt(
    palette: Palette,
    fallbackColorInt: Int,
    preferDarkBackground: Boolean,
    hintColorInt: Int? = null
): Int {
    val candidates = listOfNotNull(
        palette.mutedSwatch?.rgb,
        palette.vibrantSwatch?.rgb,
        palette.dominantSwatch?.rgb
    )
    val pickedFromPriority = candidates.firstOrNull { rgb ->
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        val saturation = hsl[1]
        val lightness = hsl[2]
        saturation >= 0.08f && lightness in 0.08f..0.92f
    }
    if (pickedFromPriority != null) return pickedFromPriority

    val swatches = palette.swatches
    if (swatches.isEmpty()) {
        return palette.mutedSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: fallbackColorInt
    }

    val targetLightness = if (preferDarkBackground) 0.48f else 0.40f
    val maxPopulation = swatches.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1

    var bestRgb: Int? = null
    var bestScore = -1f
    val hsl = FloatArray(3)
    val hintLab = if (hintColorInt != null) DoubleArray(3).also { ColorUtils.colorToLAB(hintColorInt, it) } else null
    val tmpLab = if (hintLab != null) DoubleArray(3) else null
    for (swatch in swatches) {
        val rgb = swatch.rgb
        ColorUtils.colorToHSL(rgb, hsl)
        val saturation = hsl[1]
        val lightness = hsl[2]

        val saturationScore = ((saturation - 0.12f) / 0.88f).coerceIn(0f, 1f)
        val lightnessScore = (1f - (abs(lightness - targetLightness) / 0.55f)).coerceIn(0f, 1f)
        val populationScore = sqrt(swatch.population.toFloat() / maxPopulation.toFloat())

        val grayPenalty = if (saturation < 0.10f) 0.25f else 1f
        val extremePenalty = if (lightness < 0.04f || lightness > 0.96f) 0.2f else 1f
        val brightPenalty = when {
            lightness > 0.82f -> 0.30f
            lightness > 0.74f && saturation > 0.22f -> 0.55f
            lightness > 0.68f && saturation > 0.55f -> 0.70f
            else -> 1f
        }

        var score = populationScore * (0.62f * saturationScore + 0.38f * lightnessScore) * grayPenalty * extremePenalty * brightPenalty
        if (hintLab != null && tmpLab != null) {
            ColorUtils.colorToLAB(rgb, tmpLab)
            val dl = (tmpLab[0] - hintLab[0]).toFloat()
            val da = (tmpLab[1] - hintLab[1]).toFloat()
            val db = (tmpLab[2] - hintLab[2]).toFloat()
            val dist = sqrt(dl * dl + da * da + db * db)
            val hintScore = (1f - (dist / 55f)).coerceIn(0f, 1f)
            score *= (0.65f + 0.35f * hintScore)
        }
        if (score > bestScore) {
            bestScore = score
            bestRgb = rgb
        }
    }

    return bestRgb
        ?: palette.mutedSwatch?.rgb
        ?: palette.vibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: fallbackColorInt
}

internal fun adjustHslForUi(hsl: FloatArray, preferDarkBackground: Boolean) {
    val saturation = hsl[1].coerceIn(0f, 1f)
    val lightness = hsl[2].coerceIn(0f, 1f)

    val boostedSaturation = when {
        saturation < 0.14f && lightness in 0.15f..0.9f -> 0.34f
        saturation < 0.28f -> 0.38f
        saturation < 0.5f -> (saturation * 1.12f).coerceAtMost(0.72f)
        else -> saturation.coerceAtMost(0.9f)
    }

    val hue = hsl[0]
    val isNeonLike = boostedSaturation >= 0.55f && lightness >= 0.62f

    val deepenedLightness = if (isNeonLike) {
        val maxLightness = when {
            hue in 40f..85f -> if (preferDarkBackground) 0.44f else 0.40f
            hue in 85f..170f -> if (preferDarkBackground) 0.46f else 0.42f
            hue in 170f..260f -> if (preferDarkBackground) 0.50f else 0.46f
            else -> if (preferDarkBackground) 0.48f else 0.44f
        }
        lightness.coerceAtMost(maxLightness)
    } else {
        lightness
    }

    val isBright = deepenedLightness >= 0.62f
    val furtherDeepenedLightness = if (isBright) {
        val baseMax = if (preferDarkBackground) 0.66f else 0.60f
        val saturationAdj = when {
            boostedSaturation >= 0.60f -> 0.08f
            boostedSaturation >= 0.45f -> 0.05f
            boostedSaturation >= 0.30f -> 0.03f
            else -> 0f
        }
        val hueAdj = when {
            hue in 40f..85f -> 0.07f
            hue in 85f..170f -> 0.05f
            hue in 170f..260f -> 0.02f
            else -> 0.04f
        }
        val maxLightness = (baseMax - saturationAdj - hueAdj).coerceIn(0.38f, baseMax)
        deepenedLightness.coerceAtMost(maxLightness)
    } else {
        deepenedLightness
    }

    val adjustedLightness = if (preferDarkBackground) {
        furtherDeepenedLightness.coerceIn(0.18f, 0.70f)
    } else {
        furtherDeepenedLightness.coerceIn(0.12f, 0.62f)
    }

    val adjustedSaturation = if (isNeonLike) boostedSaturation.coerceAtMost(0.72f) else boostedSaturation

    hsl[1] = adjustedSaturation
    hsl[2] = adjustedLightness
}

internal fun computeCenterWeightedHintColorInt(bitmap: android.graphics.Bitmap, centerRegionRatio: Float): Int? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return null

    val ratio = centerRegionRatio.coerceIn(0.1f, 1f)
    val regionW = (width * ratio).toInt().coerceAtLeast(1)
    val regionH = (height * ratio).toInt().coerceAtLeast(1)
    val left = ((width - regionW) / 2).coerceAtLeast(0)
    val top = ((height - regionH) / 2).coerceAtLeast(0)

    val pixels = IntArray(regionW * regionH)
    runCatching { bitmap.getPixels(pixels, 0, regionW, left, top, regionW, regionH) }.getOrNull() ?: return null

    val cx = (regionW - 1) / 2f
    val cy = (regionH - 1) / 2f
    val sigma = 0.55f
    val inv2Sigma2 = 1f / (2f * sigma * sigma)

    var sumW = 0f
    var sumR = 0f
    var sumG = 0f
    var sumB = 0f

    val step = if (regionW * regionH <= 24_000) 1 else 2
    for (y in 0 until regionH step step) {
        val yn = (y - cy) / (cy.coerceAtLeast(1f))
        val row = y * regionW
        for (x in 0 until regionW step step) {
            val argb = pixels[row + x]
            val a = (argb ushr 24) and 0xFF
            if (a < 32) continue

            val xn = (x - cx) / (cx.coerceAtLeast(1f))
            val w = exp(-(xn * xn + yn * yn) * inv2Sigma2)

            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f

            val lr = srgbToLinear(r)
            val lg = srgbToLinear(g)
            val lb = srgbToLinear(b)

            sumW += w
            sumR += w * lr
            sumG += w * lg
            sumB += w * lb
        }
    }

    if (sumW <= 0f) return null
    val r = linearToSrgb(sumR / sumW).coerceIn(0f, 1f)
    val g = linearToSrgb(sumG / sumW).coerceIn(0f, 1f)
    val b = linearToSrgb(sumB / sumW).coerceIn(0f, 1f)

    val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
}

private fun srgbToLinear(c: Float): Float {
    return if (c <= 0.04045f) c / 12.92f else (((c + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()
}

private fun linearToSrgb(c: Float): Float {
    return if (c <= 0.0031308f) c * 12.92f else ((1.055 * c.toDouble().pow(1.0 / 2.4) - 0.055)).toFloat()
}

