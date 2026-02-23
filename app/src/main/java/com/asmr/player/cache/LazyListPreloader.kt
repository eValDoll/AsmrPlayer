package com.asmr.player.cache

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun LazyListPreloader(
    state: LazyListState,
    models: List<Any>,
    preloadNext: Int = 8,
    cacheManagerProvider: () -> ImageCacheManager
) {
    val manager = remember { cacheManagerProvider() }
    LaunchedEffect(state, models, preloadNext) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo }
            .map { visible ->
                val last = visible.maxOfOrNull { it.index } ?: -1
                val start = (last + 1).coerceAtLeast(0)
                val end = (start + preloadNext).coerceAtMost(models.size)
                if (start >= end) emptyList() else models.subList(start, end)
            }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .collect { toPreload ->
                manager.preload(toPreload)
            }
    }
}

@Composable
fun LazyListPreloader(
    state: LazyListState,
    itemCount: Int,
    preloadNext: Int = 8,
    cacheManagerProvider: () -> ImageCacheManager,
    modelAt: (Int) -> Any?
) {
    val manager = remember { cacheManagerProvider() }
    LaunchedEffect(state, itemCount, preloadNext) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo }
            .map { visible ->
                val last = visible.maxOfOrNull { it.index } ?: -1
                val start = (last + 1).coerceAtLeast(0)
                val end = (start + preloadNext).coerceAtMost(itemCount)
                if (start >= end) emptyList() else (start until end).mapNotNull(modelAt)
            }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .collect { toPreload ->
                manager.preload(toPreload)
            }
    }
}
