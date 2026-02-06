package com.asmr.player.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsitePlayLibraryClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.domain.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dlsiteScraper: DLSiteScraper,
    private val dlsitePlayLibraryClient: DlsitePlayLibraryClient,
    private val asmrOneCrawler: AsmrOneCrawler
) : ViewModel() {
    private val pageSize = 30
    private var currentOrder: SearchSortOption = SearchSortOption.Trend
    private var purchasedOnly: Boolean = false
    private var enrichJob: Job? = null
    private var asmrOneJob: Job? = null
    private val dlsiteDetailCache = ConcurrentHashMap<String, Album>()
    private val enrichDispatcher = Dispatchers.IO
    private val asmrOneAvailabilityCache = ConcurrentHashMap<String, Boolean>()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _viewMode = MutableStateFlow(1) // 0: List, 1: Grid
    val viewMode = _viewMode.asStateFlow()

    fun setViewMode(mode: Int) {
        _viewMode.value = mode
    }

    fun search(keyword: String) {
        Log.d("SearchViewModel", "Search requested: keyword=$keyword")
        val normalizedKeyword = keyword.trim()
        goToPage(normalizedKeyword, targetPage = 1, showFullScreenLoading = true)
    }

    fun setOrder(order: SearchSortOption) {
        if (currentOrder == order) return
        currentOrder = order
        val current = _uiState.value as? SearchUiState.Success ?: return
        goToPage(current.keyword, targetPage = 1, showFullScreenLoading = false)
    }

    fun setPurchasedOnly(enabled: Boolean) {
        if (purchasedOnly == enabled) return
        purchasedOnly = enabled
        val current = _uiState.value as? SearchUiState.Success ?: return
        goToPage(current.keyword, targetPage = 1, showFullScreenLoading = false)
    }

    fun nextPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canGoNext || current.isPaging) return
        goToPage(current.keyword, targetPage = current.page + 1, showFullScreenLoading = false)
    }

    fun prevPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canGoPrev || current.isPaging) return
        goToPage(current.keyword, targetPage = current.page - 1, showFullScreenLoading = false)
    }

    fun refreshPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (current.isPaging) return
        goToPage(current.keyword, targetPage = current.page, showFullScreenLoading = false)
    }

    private fun goToPage(keyword: String, targetPage: Int, showFullScreenLoading: Boolean) {
        val normalizedKeyword = keyword.trim()
        val page = targetPage.coerceAtLeast(1)

        viewModelScope.launch {
            val current = _uiState.value as? SearchUiState.Success
            _uiState.value = when {
                showFullScreenLoading -> SearchUiState.Loading
                current != null && current.keyword == normalizedKeyword -> current.copy(isPaging = true, isEnriching = false)
                else -> SearchUiState.Loading
            }
            try {
                val pageResult = fetchPage(normalizedKeyword, page = page, order = currentOrder, purchasedOnly = purchasedOnly)
                val canGoNext = pageResult.canGoNext
                _uiState.value = SearchUiState.Success(
                    results = pageResult.items,
                    keyword = normalizedKeyword,
                    page = page,
                    order = currentOrder,
                    purchasedOnly = purchasedOnly,
                    canGoPrev = page > 1,
                    canGoNext = canGoNext,
                    isPaging = false,
                    isEnriching = false
                )
                if (!purchasedOnly && pageResult.items.isNotEmpty()) {
                    startEnrichDlsiteDetails(
                        keyword = normalizedKeyword,
                        page = page,
                        baseItems = pageResult.items
                    )
                    startMarkAsmrOneAvailability(
                        page = page,
                        baseItems = pageResult.items
                    )
                } else {
                    enrichJob?.cancel()
                    asmrOneJob?.cancel()
                }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Search paging failed", e)
                _uiState.value = SearchUiState.Error(e.message ?: "搜索失败")
            }
        }
    }

    private suspend fun fetchPage(keyword: String, page: Int, order: SearchSortOption, purchasedOnly: Boolean): SearchPageResult {
        if (purchasedOnly) {
            val resp = dlsitePlayLibraryClient.searchPurchased(keyword, page, pageSize)
            return SearchPageResult(items = resp.items, canGoNext = resp.canGoNext)
        }
        val normalizedKeyword = keyword.trim()
        val normalizedRj = normalizedKeyword.uppercase()
        if (page == 1 && Regex("""RJ\d{6,}""").matches(normalizedRj)) {
            val info = runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "zh_CN") }.getOrNull()
                ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "ja_JP") }.getOrNull()
                ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj) }.getOrNull()
            if (info != null) {
                val album = info.album.copy(workId = normalizedRj, rjCode = normalizedRj)
                return SearchPageResult(items = listOf(album), canGoNext = false)
            }
        }
        val items = dlsiteScraper.search(keyword, page, order.dlsiteOrder)
        return SearchPageResult(items = items, canGoNext = items.size >= pageSize)
    }

    private fun startEnrichDlsiteDetails(keyword: String, page: Int, baseItems: List<Album>) {
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val current0 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (current0.keyword != keyword || current0.page != page || current0.purchasedOnly) return@launch
            _uiState.value = current0.copy(isEnriching = true)

            coroutineScope {
                val sem = Semaphore(6)
                val deferreds = baseItems.mapIndexedNotNull { index, base ->
                    val rj = base.rjCode.ifBlank { base.workId }.trim().uppercase()
                    if (rj.isBlank()) return@mapIndexedNotNull null
                    async(enrichDispatcher) {
                        sem.withPermit {
                            val cached = dlsiteDetailCache[rj]
                            val detail = cached ?: runCatching { dlsiteScraper.getWorkInfo(rj)?.album }.getOrNull()
                            if (detail != null) dlsiteDetailCache[rj] = detail
                            index to detail
                        }
                    }
                }
                deferreds.forEach { deferred ->
                    val (idx, detail) = runCatching { deferred.await() }.getOrNull() ?: return@forEach
                    if (detail == null) return@forEach
                    val cur = _uiState.value as? SearchUiState.Success ?: return@forEach
                    if (cur.keyword != keyword || cur.page != page || cur.purchasedOnly) return@forEach
                    val list = cur.results.toMutableList()
                    if (idx !in list.indices) return@forEach
                    list[idx] = mergeAlbum(list[idx], detail)
                    _uiState.value = cur.copy(results = list)
                }
            }

            val current1 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (current1.keyword == keyword && current1.page == page && !current1.purchasedOnly) {
                _uiState.value = current1.copy(isEnriching = false)
            }
        }
    }

    private fun mergeAlbum(base: Album, detail: Album): Album {
        return base.copy(
            title = base.title.ifBlank { detail.title },
            circle = base.circle.ifBlank { detail.circle },
            cv = base.cv.ifBlank { detail.cv },
            tags = if (base.tags.isEmpty()) detail.tags else base.tags,
            coverUrl = base.coverUrl.ifBlank { detail.coverUrl },
            ratingValue = detail.ratingValue ?: base.ratingValue,
            ratingCount = maxOf(base.ratingCount, detail.ratingCount),
            releaseDate = base.releaseDate.ifBlank { detail.releaseDate },
            dlCount = maxOf(base.dlCount, detail.dlCount),
            priceJpy = if (base.priceJpy > 0) base.priceJpy else detail.priceJpy
        )
    }

    private fun startMarkAsmrOneAvailability(page: Int, baseItems: List<Album>) {
        asmrOneJob?.cancel()
        asmrOneJob = viewModelScope.launch(Dispatchers.IO) {
            val cur0 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (cur0.page != page || cur0.purchasedOnly) return@launch

            val sem = Semaphore(4)
            val checks = coroutineScope {
                baseItems.mapIndexedNotNull { idx, a ->
                    val rj = a.rjCode.ifBlank { a.workId }.trim().uppercase()
                    if (rj.isBlank()) return@mapIndexedNotNull null
                    async(enrichDispatcher) {
                        val cached = asmrOneAvailabilityCache[rj]
                        val has = cached ?: sem.withPermit {
                            val resp = runCatching { asmrOneCrawler.search(rj) }.getOrNull()
                            val ok = resp?.works?.any { it.source_id.equals(rj, ignoreCase = true) } == true
                            asmrOneAvailabilityCache[rj] = ok
                            ok
                        }
                        idx to has
                    }
                }
            }

            val cur1 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (cur1.page != page || cur1.purchasedOnly) return@launch
            val list = cur1.results.toMutableList()
            checks.forEach { deferred ->
                val (idx, has) = runCatching { deferred.await() }.getOrNull() ?: return@forEach
                if (idx !in list.indices) return@forEach
                val old = list[idx]
                if (old.hasAsmrOne != has) list[idx] = old.copy(hasAsmrOne = has)
            }
            _uiState.value = cur1.copy(results = list)
        }
    }
}

private data class SearchPageResult(
    val items: List<Album>,
    val canGoNext: Boolean
)

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(
        val results: List<Album>,
        val keyword: String,
        val page: Int,
        val order: SearchSortOption,
        val purchasedOnly: Boolean,
        val canGoPrev: Boolean,
        val canGoNext: Boolean,
        val isPaging: Boolean,
        val isEnriching: Boolean = false
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
