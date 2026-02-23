package com.asmr.player.ui.sidepanel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.ui.library.LibraryQueryBuilder
import com.asmr.player.ui.library.LibraryQuerySpec
import com.asmr.player.ui.library.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RecentAlbumResumeInfo(
    val mediaId: String,
    val trackId: Long?,
    val positionMs: Long
)

data class RecentAlbumUiItem(
    val album: AlbumEntity,
    val completedTracks: Long,
    val totalTracks: Long,
    val resume: RecentAlbumResumeInfo?
)

@HiltViewModel
class RecentAlbumsPanelViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {
    private val query = LibraryQueryBuilder.build(LibraryQuerySpec(sort = LibrarySort.LastPlayedDesc))

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<RecentAlbumUiItem>> = db.albumDao()
        .queryAlbums(query)
        .map { albums -> albums.filter { it.id > 0L }.take(5) }
        .distinctUntilChanged()
        .flatMapLatest { recent ->
            val albumIds = recent.map { it.id }
            if (albumIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                val completedCountsFlow = db.trackPlaybackProgressDao()
                    .observeCompletedCountsForAlbums(albumIds)
                    .map { rows -> rows.associate { it.albumId to it.completedCount } }

                val totalTracksFlow = db.trackDao()
                    .observeTrackCountsForAlbums(albumIds)
                    .map { rows -> rows.associate { it.albumId to it.totalCount } }

                val resumeProgressFlow = db.trackPlaybackProgressDao()
                    .observeProgressForAlbums(albumIds)
                    .map { rows ->
                        rows.groupBy { it.albumId }
                            .mapNotNull { (albumId, list) ->
                                if (albumId == null) return@mapNotNull null
                                val pick = list.firstOrNull { !it.completed && it.positionMs > 0L }
                                    ?: list.firstOrNull { !it.completed }
                                albumId to (pick?.let {
                                    RecentAlbumResumeInfo(
                                        mediaId = it.mediaId,
                                        trackId = it.trackId,
                                        positionMs = it.positionMs.coerceAtLeast(0L)
                                    )
                                })
                            }
                            .toMap()
                    }

                combine(completedCountsFlow, totalTracksFlow, resumeProgressFlow) { completedMap, totalMap, resumeMap ->
                    recent.map { album ->
                        val total = totalMap[album.id] ?: 0L
                        val completed = (completedMap[album.id] ?: 0L).coerceAtMost(total)
                        RecentAlbumUiItem(
                            album = album,
                            completedTracks = completed,
                            totalTracks = total,
                            resume = resumeMap[album.id]
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
}
