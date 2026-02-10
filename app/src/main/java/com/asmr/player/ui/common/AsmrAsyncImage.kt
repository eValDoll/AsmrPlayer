package com.asmr.player.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

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

    SubcomposeAsyncImage(
        model = normalizedModel,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
        alpha = alpha,
        colorFilter = colorFilter,
        modifier = modifier,
        loading = { placeholder(Modifier.fillMaxSize()) },
        error = { placeholder(Modifier.fillMaxSize()) },
        success = { SubcomposeAsyncImageContent() },
    )
}

private fun normalizeImageModel(model: Any?): Any? {
    return when (model) {
        is String -> model.trim().takeIf { it.isNotEmpty() }
        else -> model
    }
}
