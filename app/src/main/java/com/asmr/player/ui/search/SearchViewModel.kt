package com.asmr.player.ui.search

import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.BuildConfig
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
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
                        keyword = normalizedKeyword,
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

    private fun startMarkAsmrOneAvailability(keyword: String, page: Int, baseItems: List<Album>) {
        asmrOneJob?.cancel()
        asmrOneJob = viewModelScope.launch(Dispatchers.IO) {
            val cur0 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (cur0.keyword != keyword || cur0.page != page || cur0.purchasedOnly) return@launch

            val indexByRj = linkedMapOf<String, MutableList<Int>>()
            baseItems.forEachIndexed { idx, a ->
                val rj = a.rjCode.ifBlank { a.workId }.trim().uppercase()
                if (rj.isBlank()) return@forEachIndexed
                val list = indexByRj.getOrPut(rj) { mutableListOf() }
                list.add(idx)
            }
            if (indexByRj.isEmpty()) return@launch

            val normalizedKeywordUpper = keyword.trim().uppercase()
            if (BuildConfig.DEBUG && normalizedKeywordUpper == DEBUG_DELAY_TEST_RJ && debugDelayTestOnce.compareAndSet(false, true)) {
                launch {
                    runDebugDelayTestForRj01491538()
                }
            }

            indexByRj.keys.forEach { rj ->
                val cached = asmrOneAvailabilityCache[rj] ?: return@forEach
                updateAsmrOneAvailability(keyword = keyword, page = page, rj = rj, has = cached, indexByRj = indexByRj)
            }

            val normalizedKeyword = keyword.trim()
            val isRjKeyword = Regex("""RJ\d{6,}""").matches(normalizedKeyword.uppercase())
            if (!isRjKeyword && normalizedKeyword.isNotBlank()) {
                val resp = runCatching { asmrOneCrawler.search(normalizedKeyword) }.getOrNull()
                val ids = resp?.works
                    .orEmpty()
                    .asSequence()
                    .map { it.source_id.trim().uppercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
                if (ids.isNotEmpty()) {
                    indexByRj.keys.forEach { rj ->
                        if (asmrOneAvailabilityCache.containsKey(rj)) return@forEach
                        if (ids.contains(rj)) {
                            asmrOneAvailabilityCache[rj] = true
                            updateAsmrOneAvailability(keyword = keyword, page = page, rj = rj, has = true, indexByRj = indexByRj)
                        }
                    }
                }
            }

            val unresolved = indexByRj.keys.filter { !asmrOneAvailabilityCache.containsKey(it) }
            if (unresolved.isNotEmpty()) {
                val sem = Semaphore(6)
                val checks = coroutineScope {
                    unresolved.map { rj ->
                        async(enrichDispatcher) {
                            val cached = asmrOneAvailabilityCache[rj]
                            if (cached != null) return@async rj to cached
                            val has = sem.withPermit {
                                val resp = runCatching { asmrOneCrawler.search(rj) }.getOrNull()
                                resp?.works?.any { it.source_id.equals(rj, ignoreCase = true) } == true
                            }
                            asmrOneAvailabilityCache[rj] = has
                            rj to has
                        }
                    }
                }
                coroutineScope {
                    checks.forEach { deferred ->
                        launch {
                            val pair = runCatching { deferred.await() }.getOrNull()
                            if (pair != null) {
                                val (rj, has) = pair
                                updateAsmrOneAvailability(keyword = keyword, page = page, rj = rj, has = has, indexByRj = indexByRj)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateAsmrOneAvailability(
        keyword: String,
        page: Int,
        rj: String,
        has: Boolean,
        indexByRj: Map<String, List<Int>>
    ) {
        _uiState.update { state ->
            val cur = state as? SearchUiState.Success ?: return@update state
            if (cur.keyword != keyword || cur.page != page || cur.purchasedOnly) return@update state

            val indices = indexByRj[rj].orEmpty()
            if (indices.isEmpty()) return@update state

            val list = cur.results.toMutableList()
            var changed = false
            indices.forEach { idx ->
                if (idx !in list.indices) return@forEach
                val old = list[idx]
                if (old.hasAsmrOne != has) {
                    list[idx] = old.copy(hasAsmrOne = has)
                    changed = true
                }
            }
            if (!changed) cur else cur.copy(results = list)
        }
    }

    private suspend fun runDebugDelayTestForRj01491538() {
        val rj = DEBUG_DELAY_TEST_RJ
        val durations = ArrayList<Long>(DEBUG_DELAY_TEST_ROUNDS)
        val networkDurations = ArrayList<Long>(DEBUG_DELAY_TEST_ROUNDS)
        var cacheHits = 0
        var fallbackUsed = 0
        var networkCalls = 0

        repeat(DEBUG_DELAY_TEST_ROUNDS) { idx ->
            if (idx % 3 == 2) asmrOneAvailabilityCache.remove(rj)
            val cached = asmrOneAvailabilityCache[rj]
            val cacheHit = cached != null
            if (cacheHit) cacheHits++

            val start = SystemClock.elapsedRealtime()
            if (!cacheHit) {
                val result = runCatching { asmrOneCrawler.searchWithTrace(rj) }.getOrNull()
                val ok = result?.response?.works?.any { it.source_id.equals(rj, ignoreCase = true) } == true
                asmrOneAvailabilityCache[rj] = ok
                networkCalls++
                if (result?.trace?.fallbackUsed == true) fallbackUsed++
            }
            val dt = (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
            durations.add(dt)
            if (!cacheHit) networkDurations.add(dt)
        }

        val networkStats = percentileStatsMs(networkDurations.ifEmpty { durations })
        val overallStats = percentileStatsMs(durations)
        Log.d(
            "SearchViewModel",
            "RJ01491538 delay test: n=$DEBUG_DELAY_TEST_ROUNDS cacheHit=$cacheHits network=$networkCalls fallbackUsed=$fallbackUsed " +
                "overall(p50=${overallStats.p50}ms p95=${overallStats.p95}ms max=${overallStats.max}ms) " +
                "network(p50=${networkStats.p50}ms p95=${networkStats.p95}ms max=${networkStats.max}ms)"
        )
    }

    private data class PercentileStats(val p50: Long, val p95: Long, val max: Long)

    private fun percentileStatsMs(values: List<Long>): PercentileStats {
        if (values.isEmpty()) return PercentileStats(0L, 0L, 0L)
        val sorted = values.sorted()
        val p50 = percentileNearestRank(sorted, 50.0)
        val p95 = percentileNearestRank(sorted, 95.0)
        val max = sorted.last()
        return PercentileStats(p50 = p50, p95 = p95, max = max)
    }

    private fun percentileNearestRank(sorted: List<Long>, percentile: Double): Long {
        if (sorted.isEmpty()) return 0L
        val n = sorted.size
        val rank = ceil(percentile / 100.0 * n).toInt().coerceIn(1, n)
        return sorted[rank - 1]
    }

    private companion object {
        private const val DEBUG_DELAY_TEST_RJ = "RJ01491538"
        private const val DEBUG_DELAY_TEST_ROUNDS = 30
        private val debugDelayTestOnce = AtomicBoolean(false)
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
