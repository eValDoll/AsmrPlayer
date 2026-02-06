package com.asmr.player.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.asmr.player.data.local.db.entities.PlaylistItemEntity

import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun PlaylistDetailScreen(
    windowSizeClass: WindowSizeClass,
    playlistId: Long,
    title: String,
    onPlayAll: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(playlistId) {
        viewModel.setPlaylistId(playlistId)
    }
    val items by viewModel.items.collectAsState()
    var pendingRemoveItem by remember { mutableStateOf<PlaylistItemEntity?>(null) }

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter // 仅用于平板适配：居中显示内容
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                // 仅用于平板适配：限制内容区域最大宽度并填充可用空间
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { idx, item -> "${item.mediaId}#$idx" }) { index, item ->
                    PlaylistItemRow(
                        item = item,
                        onPlay = { onPlayAll(items, item) },
                        onRemove = {
                            pendingRemoveItem = item
                        }
                    )
                    if (index < items.size - 1) {
                        HorizontalDivider(
                             modifier = Modifier.padding(horizontal = 16.dp),
                             thickness = 0.5.dp,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                         )
                    }
                }
            }
        }
    }

    val item = pendingRemoveItem
    if (item != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveItem = null },
            title = { Text("确认移除") },
            text = {
                Text(
                    text = "确定从「$title」移除“${item.title.ifBlank { "未命名" }}”吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveItem = null
                        viewModel.removeItem(item.mediaId)
                    }
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveItem = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PlaylistItemRow(
    item: PlaylistItemEntity,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { "未命名" }, 
                maxLines = 2, 
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.padding(4.dp))
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "移除")
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
        }
    }
}
