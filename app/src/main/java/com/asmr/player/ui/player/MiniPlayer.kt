package com.asmr.player.ui.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

import androidx.compose.ui.text.font.FontWeight
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MiniPlayer(
    onClick: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playback by viewModel.playback.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val item = playback.currentMediaItem ?: return
    val metadata = item.mediaMetadata
    val colorScheme = AsmrTheme.colorScheme
    val context = LocalContext.current
    val currentMediaId = item.mediaId

    var optimisticIsPlaying by remember { mutableStateOf<Boolean?>(null) }
    var stableMediaId by remember { mutableStateOf(currentMediaId) }
    val isPlayingEffective = optimisticIsPlaying ?: playback.isPlaying

    LaunchedEffect(currentMediaId) {
        if (currentMediaId != stableMediaId) {
            stableMediaId = currentMediaId
            optimisticIsPlaying = null
        }
    }

    LaunchedEffect(optimisticIsPlaying) {
        if (optimisticIsPlaying != null) {
            delay(1_500)
            optimisticIsPlaying = null
        }
    }
    
    val progress = if (playback.durationMs > 0) {
        (playback.positionMs.toDouble() / playback.durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(72.dp) // 高度从 84dp 减小到 72dp
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 主卡片部分
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 16.dp)
                .height(56.dp) // 高度从 64dp 减小到 56dp
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.surface)
        ) {
            // ... 背景模糊层保持不变 ...
            val blurredRequest = remember(metadata.artworkUri) {
                ImageRequest.Builder(context)
                    .data(metadata.artworkUri)
                    .size(256)
                    .build()
            }
            SubcomposeAsyncImage(
                model = blurredRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.2f
            ) {
                val s = painter.state
                if (s is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = RenderEffect
                                        .createBlurEffect(30f, 30f, Shader.TileMode.CLAMP)
                                        .asComposeRenderEffect()
                                }
                            }
                            .blur(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 36.dp, end = 8.dp), // 增加左侧间距
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ... 标题副标题保持不变 ...
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metadata.title?.toString().orEmpty().ifBlank { "未播放" },
                            modifier = Modifier.basicMarquee(),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.textPrimary
                        )
                        Text(
                            text = metadata.artist?.toString().orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.textSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) Color.Red else colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = {
                            optimisticIsPlaying = !(optimisticIsPlaying ?: playback.isPlaying)
                            viewModel.togglePlayPause()
                        }) {
                            Icon(
                                imageVector = if (isPlayingEffective) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = onOpenQueue) {
                            Icon(
                                Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.primary.copy(alpha = 0.1f)
                )
            }
        }

        // 圆形超出头像
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp) // 头像更靠左
                .size(64.dp) // 半径增大，从 56dp 增大到 64dp
                .graphicsLayer {
                    shadowElevation = 16f
                    shape = CircleShape
                    clip = false
                    translationY = 4f
                    translationX = 2f
                }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            AsyncImage(
                model = metadata.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
