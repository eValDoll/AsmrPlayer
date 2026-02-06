package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.api.Asmr100Api
import com.asmr.player.data.remote.api.Asmr200Api
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.api.Asmr300Api
import com.asmr.player.data.remote.api.Asmr200Work
import com.asmr.player.data.remote.api.Circle
import com.asmr.player.data.remote.api.Pagination
import com.asmr.player.data.remote.api.SearchResponse
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AsmrOneCrawler @Inject constructor(
    private val api: AsmrOneApi,
    private val asmr100Api: Asmr100Api,
    private val asmr200Api: Asmr200Api,
    private val asmr300Api: Asmr300Api,
    private val settingsRepository: SettingsRepository
) {
    suspend fun search(keyword: String, page: Int = 1): SearchResponse {
        val normalized = keyword.trim()
        val primary = runCatching { api.search(keyword = normalized, page = page) }.getOrNull()
        if (primary != null && primary.works.isNotEmpty()) return primary

        val isRj = normalized.startsWith("RJ", ignoreCase = true)
        if (!isRj) return primary ?: SearchResponse(works = emptyList(), pagination = Pagination(0, 20, page))

        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backupApis = backupApisInOrder(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        for (backupApi in backupApis) {
            val from = runCatching { backupApi.search(keyword = " $normalized", page = page) }.getOrNull()
            val mapped = mapBackupWorks(from?.works.orEmpty(), normalized)
            if (mapped.isNotEmpty()) {
                return SearchResponse(
                    works = mapped,
                    pagination = Pagination(totalCount = mapped.size, pageSize = mapped.size, page = page)
                )
            }
        }

        return primary ?: SearchResponse(works = emptyList(), pagination = Pagination(0, 20, page))
    }

    suspend fun getDetails(workId: String): WorkDetailsResponse {
        val normalized = workId.trim()
        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backupApis = backupApisInOrder(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        return runCatching { api.getWorkDetails(normalized) }.getOrElse {
            var last: Throwable = it
            for (backupApi in backupApis) {
                val result = runCatching { backupApi.getWorkDetails(normalized) }.getOrElse { e ->
                    last = e
                    null
                }
                if (result != null) return result
            }
            throw last
        }
    }

    suspend fun getTracks(workId: String): List<AsmrOneTrackNodeResponse> {
        val normalized = workId.trim()
        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backupApis = backupApisInOrder(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        return runCatching { api.getTracks(normalized) }.getOrElse {
            var last: Throwable = it
            for (backupApi in backupApis) {
                val result = runCatching { backupApi.getTracks(normalized) }.getOrElse { e ->
                    last = e
                    null
                }
                if (result != null) return result
            }
            throw last
        }
    }
}

private interface AsmrBackupApi {
    suspend fun search(keyword: String, page: Int = 1): com.asmr.player.data.remote.api.Asmr200SearchResponse
    suspend fun getWorkDetails(workId: String): WorkDetailsResponse
    suspend fun getTracks(workId: String): List<AsmrOneTrackNodeResponse>
}

private fun Asmr100Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int) = this@asBackup.search(keyword = keyword, page = page)
    override suspend fun getWorkDetails(workId: String) = this@asBackup.getWorkDetails(workId)
    override suspend fun getTracks(workId: String) = this@asBackup.getTracks(workId)
}

private fun Asmr200Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int) = this@asBackup.search(keyword = keyword, page = page)
    override suspend fun getWorkDetails(workId: String) = this@asBackup.getWorkDetails(workId)
    override suspend fun getTracks(workId: String) = this@asBackup.getTracks(workId)
}

private fun Asmr300Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int) = this@asBackup.search(keyword = keyword, page = page)
    override suspend fun getWorkDetails(workId: String) = this@asBackup.getWorkDetails(workId)
    override suspend fun getTracks(workId: String) = this@asBackup.getTracks(workId)
}

private fun backupApisInOrder(
    preferredSite: Int,
    asmr100Api: Asmr100Api,
    asmr200Api: Asmr200Api,
    asmr300Api: Asmr300Api
): List<AsmrBackupApi> {
    val api100 = asmr100Api.asBackup()
    val api200 = asmr200Api.asBackup()
    val api300 = asmr300Api.asBackup()
    return when (preferredSite) {
        100 -> listOf(api100, api200, api300)
        300 -> listOf(api300, api200, api100)
        else -> listOf(api200, api100, api300)
    }
}

private fun mapBackupWorks(works: List<Asmr200Work>, normalizedRj: String): List<WorkDetailsResponse> {
    return works.mapNotNull { w ->
        if (w.id <= 0) return@mapNotNull null
        val mappedSourceId = run {
            val editions = w.language_editions.orEmpty()
            val hasEdition = editions.any { it.workno?.trim()?.equals(normalizedRj, ignoreCase = true) == true }
            if (hasEdition) normalizedRj else w.source_id.orEmpty().ifBlank { normalizedRj }
        }
        WorkDetailsResponse(
            id = w.id,
            source_id = mappedSourceId,
            title = w.title.orEmpty(),
            circle = w.circle ?: w.name?.takeIf { it.isNotBlank() }?.let { Circle(it) },
            vas = w.vas,
            tags = w.tags,
            duration = w.duration ?: 0,
            mainCoverUrl = w.mainCoverUrl.orEmpty(),
            dl_count = w.dl_count ?: 0,
            price = w.price ?: 0
        )
    }
}
