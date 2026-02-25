package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.NetworkHeaders
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

data class AsmrOneSearchTrace(
    val keyword: String,
    val primarySucceeded: Boolean,
    val primaryHasWorks: Boolean,
    val fallbackAttempted: Boolean,
    val fallbackUsed: Boolean,
    val fallbackSite: Int?
)

data class AsmrOneSearchResult(
    val response: SearchResponse,
    val trace: AsmrOneSearchTrace
)

@Singleton
class AsmrOneCrawler @Inject constructor(
    private val api: AsmrOneApi,
    private val asmr100Api: Asmr100Api,
    private val asmr200Api: Asmr200Api,
    private val asmr300Api: Asmr300Api,
    private val settingsRepository: SettingsRepository
) {
    suspend fun searchWithTrace(keyword: String, page: Int = 1): AsmrOneSearchResult {
        val normalized = keyword.trim()
        val primaryTry = runCatching {
            api.search(keyword = normalized, page = page, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
        }
        val primary = primaryTry.getOrNull()
        val primarySucceeded = primaryTry.isSuccess && primary != null
        val primaryHasWorks = primary?.works?.isNotEmpty() == true
        if (primaryHasWorks) {
            return AsmrOneSearchResult(
                response = primary!!,
                trace = AsmrOneSearchTrace(
                    keyword = normalized,
                    primarySucceeded = primarySucceeded,
                    primaryHasWorks = true,
                    fallbackAttempted = false,
                    fallbackUsed = false,
                    fallbackSite = null
                )
            )
        }

        val isRj = normalized.startsWith("RJ", ignoreCase = true)
        if (!isRj) {
            val resp = primary ?: SearchResponse(works = emptyList(), pagination = Pagination(0, 20, page))
            return AsmrOneSearchResult(
                response = resp,
                trace = AsmrOneSearchTrace(
                    keyword = normalized,
                    primarySucceeded = primarySucceeded,
                    primaryHasWorks = false,
                    fallbackAttempted = false,
                    fallbackUsed = false,
                    fallbackSite = null
                )
            )
        }

        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backupApis = backupApisInOrder(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        for (backup in backupApis) {
            val from = runCatching {
                backup.api.search(
                    keyword = " $normalized",
                    page = page,
                    silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON
                )
            }.getOrNull()
            val mapped = mapBackupWorks(from?.works.orEmpty(), normalized)
            if (mapped.isNotEmpty()) {
                return AsmrOneSearchResult(
                    response = SearchResponse(
                        works = mapped,
                        pagination = Pagination(totalCount = mapped.size, pageSize = mapped.size, page = page)
                    ),
                    trace = AsmrOneSearchTrace(
                        keyword = normalized,
                        primarySucceeded = primarySucceeded,
                        primaryHasWorks = false,
                        fallbackAttempted = true,
                        fallbackUsed = true,
                        fallbackSite = backup.site
                    )
                )
            }
        }

        val resp = primary ?: SearchResponse(works = emptyList(), pagination = Pagination(0, 20, page))
        return AsmrOneSearchResult(
            response = resp,
            trace = AsmrOneSearchTrace(
                keyword = normalized,
                primarySucceeded = primarySucceeded,
                primaryHasWorks = false,
                fallbackAttempted = true,
                fallbackUsed = false,
                fallbackSite = null
            )
        )
    }

    suspend fun search(keyword: String, page: Int = 1): SearchResponse {
        return searchWithTrace(keyword, page).response
    }

    suspend fun getDetails(workId: String): WorkDetailsResponse {
        val normalized = workId.trim()
        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backupApis = backupApisInOrder(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        return runCatching { api.getWorkDetails(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON) }.getOrElse {
            var last: Throwable = it
            for (backupApi in backupApis) {
                val result = runCatching {
                    backupApi.api.getWorkDetails(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
                }.getOrElse { e ->
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
        return runCatching { api.getTracks(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON) }.getOrElse {
            var last: Throwable = it
            for (backupApi in backupApis) {
                val result = runCatching {
                    backupApi.api.getTracks(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
                }.getOrElse { e ->
                    last = e
                    null
                }
                if (result != null) return result
            }
            throw last
        }
    }
}

private data class AsmrBackupApiEntry(
    val site: Int,
    val api: AsmrBackupApi
)

private interface AsmrBackupApi {
    suspend fun search(
        keyword: String,
        page: Int = 1,
        silentIoError: String? = null
    ): com.asmr.player.data.remote.api.Asmr200SearchResponse
    suspend fun getWorkDetails(workId: String, silentIoError: String? = null): WorkDetailsResponse
    suspend fun getTracks(workId: String, silentIoError: String? = null): List<AsmrOneTrackNodeResponse>
}

private fun Asmr100Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        this@asBackup.search(keyword = keyword, page = page, silentIoError = silentIoError)
    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asBackup.getWorkDetails(workId, silentIoError = silentIoError)
    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asBackup.getTracks(workId, silentIoError = silentIoError)
}

private fun Asmr200Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        this@asBackup.search(keyword = keyword, page = page, silentIoError = silentIoError)
    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asBackup.getWorkDetails(workId, silentIoError = silentIoError)
    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asBackup.getTracks(workId, silentIoError = silentIoError)
}

private fun Asmr300Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        this@asBackup.search(keyword = keyword, page = page, silentIoError = silentIoError)
    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asBackup.getWorkDetails(workId, silentIoError = silentIoError)
    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asBackup.getTracks(workId, silentIoError = silentIoError)
}

private fun backupApisInOrder(
    preferredSite: Int,
    asmr100Api: Asmr100Api,
    asmr200Api: Asmr200Api,
    asmr300Api: Asmr300Api
): List<AsmrBackupApiEntry> {
    val api100 = asmr100Api.asBackup()
    val api200 = asmr200Api.asBackup()
    val api300 = asmr300Api.asBackup()
    return when (preferredSite) {
        100 -> listOf(
            AsmrBackupApiEntry(100, api100),
            AsmrBackupApiEntry(200, api200),
            AsmrBackupApiEntry(300, api300)
        )
        300 -> listOf(
            AsmrBackupApiEntry(300, api300),
            AsmrBackupApiEntry(200, api200),
            AsmrBackupApiEntry(100, api100)
        )
        else -> listOf(
            AsmrBackupApiEntry(200, api200),
            AsmrBackupApiEntry(100, api100),
            AsmrBackupApiEntry(300, api300)
        )
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
