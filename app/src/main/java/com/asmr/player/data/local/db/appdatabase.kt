package com.asmr.player.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.AlbumFtsDao
import com.asmr.player.data.local.db.dao.DailyStatDao
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.dao.LocalTreeCacheDao
import com.asmr.player.data.local.db.dao.PlaylistDao
import com.asmr.player.data.local.db.dao.PlaylistItemDao
import com.asmr.player.data.local.db.dao.PlayStatDao
import com.asmr.player.data.local.db.dao.TagDao
import com.asmr.player.data.local.db.dao.TrackTagDao
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.dao.RemoteSubtitleSourceDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumPlayStatEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.DailyStatEntity
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistTrackCrossRef
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity

@Database(
    entities = [
        AlbumEntity::class,
        AlbumFtsEntity::class,
        AlbumPlayStatEntity::class,
        DailyStatEntity::class,
        TagEntity::class,
        AlbumTagEntity::class,
        TrackTagEntity::class,
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlaylistTrackCrossRef::class,
        SubtitleEntity::class,
        DownloadTaskEntity::class,
        DownloadItemEntity::class,
        LocalTreeCacheEntity::class,
        RemoteSubtitleSourceEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun albumFtsDao(): AlbumFtsDao
    abstract fun tagDao(): TagDao
    abstract fun trackTagDao(): TrackTagDao
    abstract fun playStatDao(): PlayStatDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun downloadDao(): DownloadDao
    abstract fun localTreeCacheDao(): LocalTreeCacheDao
    abstract fun remoteSubtitleSourceDao(): RemoteSubtitleSourceDao

    companion object {
        const val DATABASE_NAME = "asmr_player.db"
    }
}
