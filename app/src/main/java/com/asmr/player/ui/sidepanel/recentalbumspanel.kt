package com.asmr.player.ui.sidepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.graphics.ColorUtils
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.rememberDominantColorCenterWeighted
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.ContentScale

@Composable
fun RecentAlbumsPanel(
    onOpenAlbum: (AlbumEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentAlbumsPanelViewModel = hiltViewModel()
) {
    val colorScheme = AsmrTheme.colorScheme
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val baseItems by viewModel.items.collectAsState()
    val optimisticOrderIds = remember { mutableStateListOf<Long>() }
    LaunchedEffect(baseItems) {
        val ids = baseItems.map { it.album.id }.toSet()
        optimisticOrderIds.retainAll(ids)
        while (optimisticOrderIds.size > 5) optimisticOrderIds.removeLast()
    }
    val displayItems = remember(baseItems, optimisticOrderIds.toList()) {
        val byId = baseItems.associateBy { it.album.id }
        val mergedIds = optimisticOrderIds.filter { it in byId.keys } + baseItems.map { it.album.id }
        mergedIds.distinct().take(5).mapNotNull { byId[it] }
    }

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最近听过",
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "最近播放",
                    color = colorScheme.textTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            RecentAlbumsList(
                items = displayItems,
                onOpenAlbum = onOpenAlbum,
                onResumePlay = { item ->
                    optimisticOrderIds.removeAll { it == item.album.id }
                    optimisticOrderIds.add(0, item.album.id)
                    while (optimisticOrderIds.size > 5) optimisticOrderIds.removeLast()
                    val a = albumDomain(item.album)
                    val resume = item.resume
                    playerViewModel.playAlbumResume(
                        album = a,
                        resumeMediaId = resume?.mediaId,
                        startPositionMs = resume?.positionMs ?: 0L
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun RecentAlbumsList(
    items: List<RecentAlbumUiItem>,
    onOpenAlbum: (AlbumEntity) -> Unit,
    onResumePlay: (RecentAlbumUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 8.dp
    val order = remember { mutableStateListOf<Long>() }
    val itemById: SnapshotStateMap<Long, RecentAlbumUiItem> = remember { mutableStateMapOf() }
    val scope = rememberCoroutineScope()
    val removalJobs = remember { mutableMapOf<Long, Job>() }
    val yAnims = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    val alphaAnims = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    val scaleAnims = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }

    LaunchedEffect(items) {
        val target = items.take(5)
        val targetIds = target.map { it.album.id }.toSet()

        target.forEach { itemById[it.album.id] = it }

        val exitingIds = order.filter { it !in targetIds }
        val newOrder = target.map { it.album.id } + exitingIds
        order.clear()
        order.addAll(newOrder.distinct())

        val currentIds = order.toSet()
        val removed = currentIds - targetIds

        removed.forEach { id ->
            if (removalJobs.containsKey(id)) return@forEach
            removalJobs[id] = scope.launch {
                delay(220)
                order.removeAll { it == id }
                itemById.remove(id)
                yAnims.remove(id)
                alphaAnims.remove(id)
                scaleAnims.remove(id)
                removalJobs.remove(id)
            }
        }
        targetIds.forEach { id -> removalJobs.remove(id)?.cancel() }
    }

    BoxWithConstraints(modifier = modifier) {
        if (items.isEmpty()) {
            Text(
                text = "暂无数据",
                color = AsmrTheme.colorScheme.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val target = items.take(5)
        val count = target.size
        val targetIds = target.map { it.album.id }.toSet()
        val featuredId = target.firstOrNull()?.album?.id
        val featuredWeight = 1.6f
        val normalWeight = 1f
        val totalWeight = if (count == 1) 1f else featuredWeight + (count - 1) * normalWeight
        val availableHeight = maxHeight - spacing * (count - 1)
        val minItem = 56.dp
        val maxItem = 120.dp
        val featuredMax = 160.dp

        val targetHeights = remember(items, maxHeight) {
            buildMap<Long, androidx.compose.ui.unit.Dp> {
                if (count <= 0) return@buildMap
                val ids = target.map { it.album.id }
                ids.forEach { id ->
                    val featured = id == featuredId
                    val w = if (count == 1) 1f else if (featured) featuredWeight else normalWeight
                    val h = (availableHeight * (w / totalWeight)).coerceIn(minItem, if (featured) featuredMax else maxItem)
                    put(id, h)
                }
            }
        }

        val targetYById = remember(items, maxHeight, order.toList()) {
            val map = mutableMapOf<Long, androidx.compose.ui.unit.Dp>()
            var y = 0.dp
            val orderedTargets = order.filter { it in targetIds }
            orderedTargets.forEachIndexed { idx, id ->
                map[id] = y
                val h = targetHeights[id] ?: minItem
                y += h
                if (idx != orderedTargets.lastIndex) y += spacing
            }
            val afterTargets = y + if (orderedTargets.isNotEmpty()) spacing else 0.dp
            var exitIndex = 0
            order.filter { it !in targetIds }.forEach { id ->
                map[id] = afterTargets + (minItem + spacing) * exitIndex
                exitIndex++
            }
            map
        }

        val targetYPxById = remember(targetYById) {
            targetYById.mapValues { (_, yDp) ->
                with(density) { yDp.toPx() }
            }
        }

        LaunchedEffect(targetYPxById, targetIds, featuredId, order.toList()) {
            val enterFromPx = with(density) { (-24).dp.toPx() }

            for (id in order) {
                val yTarget = targetYPxById[id] ?: 0f
                val isNew = !yAnims.containsKey(id)
                val yAnim = yAnims.getOrPut(id) { Animatable(yTarget) }
                val aAnim = alphaAnims.getOrPut(id) { Animatable(if (id in targetIds) 1f else 0f) }
                val sAnim = scaleAnims.getOrPut(id) { Animatable(if (id in targetIds) 1f else 0.98f) }

                val isTarget = id in targetIds
                if (isNew && isTarget) {
                    yAnim.snapTo(yTarget + enterFromPx)
                    aAnim.snapTo(0f)
                    sAnim.snapTo(0.98f)
                }
                launch {
                    yAnim.animateTo(yTarget, animationSpec = tween(durationMillis = 240))
                }
                launch {
                    aAnim.animateTo(if (isTarget) 1f else 0f, animationSpec = tween(durationMillis = 180))
                }
                launch {
                    sAnim.animateTo(if (isTarget) 1f else 0.98f, animationSpec = tween(durationMillis = 180))
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            for (id in order) {
                key(id) {
                    val item = itemById[id]
                    if (item != null) {
                        val isTarget = id in targetIds
                        val featured = isTarget && id == featuredId
                        val baseHeight = targetHeights[id] ?: minItem
                        val yPx = yAnims[id]?.value ?: 0f
                        val alpha = alphaAnims[id]?.value ?: (if (isTarget) 1f else 0f)
                        val scale = scaleAnims[id]?.value ?: (if (isTarget) 1f else 0.98f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (id == featuredId) 1f else 0f)
                                .graphicsLayer {
                                    translationY = yPx
                                    this.alpha = alpha
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            RecentAlbumRow(
                                item = item,
                                featured = featured,
                                height = baseHeight,
                                onClick = { if (isTarget) onOpenAlbum(item.album) },
                                onPlay = { if (isTarget) onResumePlay(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAlbumRow(
    item: RecentAlbumUiItem,
    featured: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val glassShape = androidx.compose.foundation.shape.RoundedCornerShape(if (featured) 18.dp else 16.dp)
    
    val blurRadius = 2.dp
    val blurModifier = remember(blurRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                val blurPx = blurRadius.toPx()
                renderEffect = RenderEffect
                    .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier.blur(blurRadius)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(glassShape)
            .clickable(onClick = onClick)
            .height(height)
    ) {
        val contentPadH = if (featured) 18.dp else 14.dp
        val contentPadV = if (featured) 14.dp else 10.dp

        AsmrAsyncImage(
            model = albumThumb(item.album),
            contentDescription = item.album.title,
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier),
            alpha = 1f,
            placeholderCornerRadius = 16,
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = colorScheme.onSurface.copy(alpha = 0.06f),
                    shape = glassShape
                )
        )

        val dominant by rememberDominantColorCenterWeighted(
            model = albumThumb(item.album),
            defaultColor = colorScheme.surface,
            centerRegionRatio = if (featured) 0.58f else 0.62f
        )
        val textColor = remember(dominant) { bestTextOn(dominant) }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPadH, vertical = contentPadV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(if (featured) 40.dp else 34.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.18f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                IconButton(onClick = onPlay, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = textColor
                    )
                }
            }
            Spacer(modifier = Modifier.size(if (featured) 12.dp else 10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = item.album.title,
                    color = textColor,
                    style = (if (featured) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelMedium)
                        .copy(fontWeight = FontWeight.SemiBold),
                    maxLines = if (featured) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                val total = item.totalTracks
                val completed = item.completedTracks
                val subtitle = if (total > 0L) {
                    "已完成 $completed/$total"
                } else {
                    "进度未知"
                }
                Text(
                    text = subtitle,
                    color = textColor.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun bestTextOn(background: Color): Color {
    val bg = background.copy(alpha = 1f).toArgb()
    val white = android.graphics.Color.WHITE
    val black = android.graphics.Color.BLACK
    val cWhite = ColorUtils.calculateContrast(white, bg)
    val cBlack = ColorUtils.calculateContrast(black, bg)
    return if (cWhite >= cBlack) Color.White else Color.Black
}

private fun albumDomain(album: AlbumEntity): Album {
    return Album(
        id = album.id,
        title = album.title,
        path = album.path,
        localPath = album.localPath,
        downloadPath = album.downloadPath,
        circle = album.circle,
        cv = album.cv,
        coverUrl = album.coverUrl,
        coverPath = album.coverPath,
        coverThumbPath = album.coverThumbPath,
        workId = album.workId,
        rjCode = album.rjCode,
        description = album.description
    )
}

private fun albumThumb(album: AlbumEntity): String? {
    return album.coverThumbPath.takeIf { it.isNotBlank() }
        ?: album.coverPath.takeIf { it.isNotBlank() }
        ?: album.coverUrl.takeIf { it.isNotBlank() }
}
