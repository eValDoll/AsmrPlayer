package com.asmr.player.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AsmrOneApi {
    @GET("search/{keyword}")
    suspend fun search(
        @Path("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("order") order: String = "release",
        @Query("sort") sort: String = "desc"
    ): SearchResponse

    @GET("work/{workId}")
    suspend fun getWorkDetails(@Path("workId") workId: String): WorkDetailsResponse

    @GET("tracks/{workId}")
    suspend fun getTracks(@Path("workId") workId: String): List<AsmrOneTrackNodeResponse>

    companion object {
        const val BASE_URL = "https://api.asmr.one/api/"
    }
}

data class SearchResponse(
    val works: List<WorkDetailsResponse>,
    val pagination: Pagination
)

data class WorkDetailsResponse(
    val id: Int,
    val source_id: String,
    val title: String,
    val circle: Circle?,
    val vas: List<Artist>?,
    val tags: List<Tag>?,
    val duration: Int,
    val mainCoverUrl: String,
    val dl_count: Int,
    val price: Int
)

data class Circle(val name: String)
data class Artist(val name: String)
data class Tag(val name: String)
data class Pagination(val totalCount: Int, val pageSize: Int, val page: Int)

data class AsmrOneTrackNodeResponse(
    @SerializedName(value = "title", alternate = ["name", "fileName"])
    val title: String? = null,
    @SerializedName(value = "children", alternate = ["child", "items", "tracks"])
    val children: List<AsmrOneTrackNodeResponse>? = null,
    val duration: Double? = null,
    @SerializedName(value = "streamUrl", alternate = ["mediaStreamUrl", "stream_url"])
    val streamUrl: String? = null,
    @SerializedName(value = "mediaDownloadUrl", alternate = ["mediaUrl", "media_url", "downloadUrl", "download_url", "url"])
    val mediaDownloadUrl: String? = null
)
