package com.asmr.player.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.library.AlbumGridItem
import com.asmr.player.ui.library.AlbumItem
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.foundation.lazy.items as lazyItems

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private enum class SearchResultViewMode { Grid, List }

@Composable
fun SearchScreen(
    windowSizeClass: WindowSizeClass,
    onAlbumClick: (Album) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    val viewMode by viewModel.viewMode.collectAsState()
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var purchasedOnly by rememberSaveable { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val currentPageKey = (uiState as? SearchUiState.Success)?.page ?: 0
    val listState = rememberSaveable(currentPageKey, saver = LazyListState.Saver) { LazyListState(0, 0) }
    val gridState = rememberSaveable(currentPageKey, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    val colorScheme = AsmrTheme.colorScheme
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (uiState is SearchUiState.Idle) {
            viewModel.setPurchasedOnly(purchasedOnly)
            viewModel.search(keyword)
        }
    }

    val success = uiState as? SearchUiState.Success
    
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
            TextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp)),
                leadingIcon = {
                    val currentOrder = success?.order ?: SearchSortOption.Trend
                    val label = if (purchasedOnly) "仅已购" else currentOrder.label
                    TextButton(
                        onClick = { scopeMenuExpanded = true },
                        enabled = success != null && !(success.isPaging),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(
                        expanded = scopeMenuExpanded,
                        onDismissRequest = { scopeMenuExpanded = false },
                        modifier = Modifier.background(colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("仅已购", color = colorScheme.textPrimary) },
                            onClick = {
                                scopeMenuExpanded = false
                                purchasedOnly = true
                                viewModel.setPurchasedOnly(true)
                            }
                        )
                        SearchSortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label, color = colorScheme.textPrimary) },
                                onClick = {
                                    scopeMenuExpanded = false
                                    purchasedOnly = false
                                    viewModel.setPurchasedOnly(false)
                                    viewModel.setOrder(option)
                                }
                            )
                        }
                    }
                },
                placeholder = { Text("搜索专辑、社团、CV...", color = colorScheme.textTertiary) },
                trailingIcon = {
                    IconButton(onClick = { 
                        viewModel.search(keyword)
                        // 只有主动点击搜索时才回顶
                        scope.launch {
                            listState.scrollToItem(0)
                            gridState.scrollToItem(0)
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = colorScheme.primary)
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = colorScheme.surface.copy(alpha = 0.3f),
                    disabledContainerColor = colorScheme.surface.copy(alpha = 0.1f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colorScheme.textPrimary,
                    unfocusedTextColor = colorScheme.textSecondary
                ),
                singleLine = true
            )
            
            if (success?.isEnriching == true) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
    
            if (success != null) {
                SearchPaginationHeader(
                    page = success.page,
                    canGoPrev = success.canGoPrev,
                    canGoNext = success.canGoNext,
                    isPaging = success.isPaging,
                    onPrev = {
                        scope.launch {
                            listState.scrollToItem(0)
                            gridState.scrollToItem(0)
                        }
                        viewModel.prevPage()
                    },
                    onNext = {
                        scope.launch {
                            listState.scrollToItem(0)
                            gridState.scrollToItem(0)
                        }
                        viewModel.nextPage()
                    }
                )
            }
    
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is SearchUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = colorScheme.primary
                    )
                    is SearchUiState.Success -> {
                        if (viewMode == 0) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                lazyItems(
                                    items = state.results,
                                    key = { album -> album.rjCode.ifBlank { album.workId }.ifBlank { album.title } }
                                ) { album ->
                                    AlbumItem(album = album, onClick = { onAlbumClick(album) })
                                }
                            }
                        } else {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Adaptive(150.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalItemSpacing = 16.dp
                            ) {
                                items(
                                    state.results.size,
                                    key = { idx ->
                                        val a = state.results[idx]
                                        a.rjCode.ifBlank { a.workId }.ifBlank { idx.toString() }
                                    }
                                ) { idx ->
                                    val album = state.results[idx]
                                    AlbumGridItem(
                                        album = album,
                                        onClick = { onAlbumClick(album) }
                                    )
                                }
                            }
                        }
                    }
                    is SearchUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = colorScheme.danger)
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun SearchPaginationHeader(
    page: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    isPaging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colorScheme.surface.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrev,
                enabled = canGoPrev && !isPaging
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = null,
                    tint = if (canGoPrev && !isPaging) colorScheme.primary else colorScheme.textTertiary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 $page 页", 
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.textPrimary
                )
                if (isPaging) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), 
                        strokeWidth = 2.dp,
                        color = colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onNext,
                enabled = canGoNext && !isPaging
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, 
                    contentDescription = null,
                    tint = if (canGoNext && !isPaging) colorScheme.primary else colorScheme.textTertiary
                )
            }
        }
    }
}
