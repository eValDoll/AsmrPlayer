package com.asmr.player.ui.theme

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.asmr.player.ui.common.adjustHslForUi
import com.asmr.player.ui.common.computeCenterWeightedHintColorInt
import com.asmr.player.ui.common.pickBestColorInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDynamicHuePalette(
    artworkModel: Any?,
    mode: ThemeMode,
    neutral: NeutralPalette,
    fallbackHue: HuePalette,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f
): State<HuePalette> {
    val context = LocalContext.current
    val rawBaseKey = artworkModel?.toString().orEmpty()
    val lastNonBlankBaseKeyState = rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    if (rawBaseKey.isNotBlank() && rawBaseKey != lastNonBlankBaseKeyState.value) {
        lastNonBlankBaseKeyState.value = rawBaseKey
    }
    val baseKey = if (rawBaseKey.isNotBlank()) rawBaseKey else lastNonBlankBaseKeyState.value
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "hue:cw:$regionKey:$baseKey:${mode.name}"
    
    val animatable = remember(key) { // Removed fallbackHue.primary dependency to avoid reset on theme change
        Animatable(DynamicHueCache.get(key) ?: fallbackHue.primary, ColorVectorConverter)
    }


    val preferDarkBackground = mode.isDark
    
    LaunchedEffect(key, mode) { // Removed fallbackHue.primary from key to avoid restart on fallback change
        if (baseKey.isBlank()) {
            return@LaunchedEffect
        }
        
        // If we have it in cache, snap immediately to avoid animation if we are just scrolling/recomposing?
        DynamicHueCache.get(key)?.let {
            animatable.animateTo(it, animationSpec = tween(520)) 
            return@LaunchedEffect
        }
        
        var constrainedPrimary: Color? = null
        var attempt = 0
        // Retry logic for color extraction failure (e.g. Coil loading issue)
        while (constrainedPrimary == null && attempt < 3) {
            if (attempt > 0) kotlinx.coroutines.delay(300)
            
            constrainedPrimary = DynamicHueCache.getOrCompute(key) {
                withContext(Dispatchers.Default) {
                    val request = ImageRequest.Builder(context)
                        .data(artworkModel)
                        .allowHardware(false)
                        .size(imageSizePx)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val drawable = result.drawable ?: return@withContext null
                    
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap 
                        ?: runCatching { drawable.toBitmap() }.getOrNull() 
                        ?: return@withContext null
                        
                    if (bitmap.width < 10 || bitmap.height < 10) return@withContext null

                    val colorInt = runCatching {
                        val palette = Palette.from(bitmap).generate()
                        val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                        pickBestColorInt(
                            palette = palette,
                            fallbackColorInt = fallbackHue.primary.toArgb(),
                            preferDarkBackground = preferDarkBackground,
                            hintColorInt = hint
                        )
                    }.getOrNull() ?: fallbackHue.primary.toArgb()

                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(colorInt, hsl)
                    adjustHslForUi(hsl, preferDarkBackground)
                    clampPrimaryHslForMode(hsl, mode)

                    Color(ColorUtils.HSLToColor(hsl))
                }
            }
            attempt++
        }

        if (constrainedPrimary == null) {
            // If failed after retries, stick to fallback
            animatable.animateTo(
                fallbackHue.primary,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
            return@LaunchedEffect
        }

        animatable.animateTo(
            constrainedPrimary,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
    }

    return remember(mode, neutral, fallbackHue, animatable) {
        derivedStateOf {
            deriveHuePalette(animatable.value, mode, neutral, fallbackHue.onPrimary)
        }
    }
}

private object DynamicHueCache {
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
