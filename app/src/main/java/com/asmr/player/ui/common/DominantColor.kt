package com.asmr.player.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDominantColor(
    model: Any?,
    defaultColor: Color,
    imageSizePx: Int = 256
): State<Color> {
    val context = LocalContext.current
    val key = model?.toString().orEmpty()
    val animatable = remember { Animatable(defaultColor, ColorVectorConverter) }
    val animatedColor = remember { derivedStateOf { animatable.value } }

    LaunchedEffect(key, defaultColor) {
        if (key.isBlank()) {
            animatable.animateTo(defaultColor, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        DominantColorCache.get(key)?.let {
            animatable.animateTo(it, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        val constrainedColor = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .allowHardware(false)
                    .size(imageSizePx)
                    .build()
                val drawable = runCatching { context.imageLoader.execute(request).drawable }.getOrNull()
                    ?: return@withContext null
                val bitmap = runCatching { drawable.toBitmap() }.getOrNull() ?: return@withContext null
                val colorInt = runCatching {
                    val palette = Palette.from(bitmap).generate()
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    pickBestColorInt(palette, defaultColor.toArgb(), preferDarkBackground)
                }.getOrNull() ?: defaultColor.toArgb()

                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(colorInt, hsl)
                val preferDarkBackground = defaultColor.luminance() < 0.5f
                adjustHslForUi(hsl, preferDarkBackground)

                Color(ColorUtils.HSLToColor(hsl))
            }
        } ?: return@LaunchedEffect

        animatable.animateTo(constrainedColor, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    return animatedColor
}

@Composable
fun rememberDominantColorCenterWeighted(
    model: Any?,
    defaultColor: Color,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f
): State<Color> {
    val context = LocalContext.current
    val baseKey = model?.toString().orEmpty()
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "cw:$regionKey:$baseKey"
    val animatable = remember { Animatable(defaultColor, ColorVectorConverter) }
    val animatedColor = remember { derivedStateOf { animatable.value } }

    LaunchedEffect(key, defaultColor, baseKey) {
        if (baseKey.isBlank()) {
            animatable.animateTo(defaultColor, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        DominantColorCache.get(key)?.let {
            animatable.animateTo(it, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        val constrainedColor = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .allowHardware(false)
                    .size(imageSizePx)
                    .build()
                val drawable = runCatching { context.imageLoader.execute(request).drawable }.getOrNull()
                    ?: return@withContext null
                val bitmap = runCatching { drawable.toBitmap() }.getOrNull() ?: return@withContext null
                val colorInt = runCatching {
                    val palette = Palette.from(bitmap).generate()
                    val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    pickBestColorInt(
                        palette = palette,
                        fallbackColorInt = defaultColor.toArgb(),
                        preferDarkBackground = preferDarkBackground,
                        hintColorInt = hint
                    )
                }.getOrNull() ?: defaultColor.toArgb()

                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(colorInt, hsl)
                val preferDarkBackground = defaultColor.luminance() < 0.5f
                adjustHslForUi(hsl, preferDarkBackground)

                Color(ColorUtils.HSLToColor(hsl))
            }
        } ?: return@LaunchedEffect

        animatable.animateTo(constrainedColor, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    return animatedColor
}

private object DominantColorCache {
    private const val MAX_SIZE = 64
    private val lru = LinkedHashMap<String, Color>(MAX_SIZE, 0.75f, true)
    private val inFlight = HashMap<String, Deferred<Color?>>()

    @Synchronized
    fun get(key: String): Color? = lru[key]

    @Synchronized
    fun put(key: String, color: Color) {
        lru[key] = color
        if (lru.size > MAX_SIZE) {
            val it = lru.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    suspend fun getOrCompute(key: String, compute: suspend () -> Color?): Color? {
        get(key)?.let { return it }

        val created = CompletableDeferred<Color?>()
        val toAwait: Deferred<Color?>
        val doCompute: Boolean
        synchronized(this) {
            lru[key]?.let { return it }
            val existing = inFlight[key]
            if (existing != null) {
                toAwait = existing
                doCompute = false
            } else {
                inFlight[key] = created
                toAwait = created
                doCompute = true
            }
        }

        if (!doCompute) return toAwait.await()

        val computed = runCatching { compute() }.getOrNull()
        synchronized(this) {
            inFlight.remove(key)
            if (computed != null) put(key, computed)
        }
        created.complete(computed)
        return computed
    }
}

private val ColorVectorConverter = TwoWayConverter<Color, AnimationVector4D>(
    convertToVector = { color ->
        AnimationVector4D(color.red, color.green, color.blue, color.alpha)
    },
    convertFromVector = { vector ->
        Color(vector.v1, vector.v2, vector.v3, vector.v4)
    }
)
