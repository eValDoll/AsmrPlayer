package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY itemOrder ASC, rowid ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun deleteItem(playlistId: Long, mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId)")
    fun observeIsItemInPlaylist(playlistId: Long, mediaId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId)")
    suspend fun isItemInPlaylist(playlistId: Long, mediaId: String): Boolean

    @Transaction
    suspend fun replaceAll(playlistId: Long, items: List<PlaylistItemEntity>) {
        clearPlaylist(playlistId)
        upsertItems(items)
    }
}
