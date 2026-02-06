package com.asmr.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.playback.PlayerConnection
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import javax.inject.Inject
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import androidx.core.net.toUri
import androidx.media3.common.Player
import android.os.Bundle
import java.io.File

import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.data.settings.AsmrPreset

import com.asmr.player.util.MessageManager
import kotlin.math.roundToLong

@HiltViewModel
@OptIn(FlowPreview::class)
class PlayerViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
    private val messageManager: MessageManager
) : ViewModel() {
    val playback: StateFlow<PlaybackSnapshot> = playerConnection.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackSnapshot())

    val resolvedDurationMs: StateFlow<Long> = playback
        .map { it.currentMediaItem?.mediaId }
        .distinctUntilChanged()
        .flatMapLatest { _ ->
            flow {
                emit(resolveDurationMs(playback.value.currentMediaItem, playback.value.durationMs))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val queue: StateFlow<List<MediaItem>> = playerConnection.queue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allPlaylists = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val floatingLyricsEnabled: StateFlow<Boolean> = settingsRepository.floatingLyricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _sessionEqualizer = MutableStateFlow<EqualizerSettings?>(null)
    val sessionEqualizer: StateFlow<EqualizerSettings> = combine(
        settingsRepository.equalizerSettings,
        _sessionEqualizer
    ) { global, session ->
        session ?: global
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqualizerSettings())

    val customPresets: StateFlow<List<AsmrPreset>> = settingsRepository.customEqualizerPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _sessionEqualizer
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(60)
                .collect { settings ->
                    settingsRepository.updateEqualizerSettings(settings)
                    val bundle = Bundle().apply {
                        putBoolean("enabled", settings.enabled)
                        putIntArray("levels", settings.bandLevels.toIntArray())
                        putInt("virtualizer", settings.virtualizerStrength)
                        putFloat("balance", settings.balance)
                        putString("preset", settings.presetName)
                        putFloat("gain", settings.originalGain)
                        putBoolean("reverbEnabled", settings.reverbEnabled)
                        putString("reverbPreset", settings.reverbPreset)
                        putInt("reverbWet", settings.reverbWet)
                        putBoolean("orbitEnabled", settings.orbitEnabled)
                        putFloat("orbitSpeed", settings.orbitSpeed)
                        putFloat("orbitDistance", settings.orbitDistance)
                    }
                    playerConnection.sendCustomCommand("UPDATE_SESSION_EQ", bundle)
                }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isFavorite: StateFlow<Boolean> = playback
        .map { it.currentMediaItem?.mediaId }
        .flatMapLatest { mediaId ->
            if (mediaId == null) kotlinx.coroutines.flow.flowOf(false)
            else playlistRepository.observeIsFavorite(mediaId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        val item = playback.value.currentMediaItem ?: return
        val mediaId = item.mediaId
        viewModelScope.launch {
            val favId = playlistRepository.getOrCreateFavoritesPlaylistId()
            if (isFavorite.value) {
                playlistRepository.removeItemFromPlaylist(favId, mediaId)
                messageManager.showInfo("已取消收藏")
            } else {
                playlistRepository.addItemToPlaylist(favId, item)
                messageManager.showSuccess("已添加到我的收藏")
            }
        }
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()

    fun toggleFloatingLyrics() {
        viewModelScope.launch {
            val enabled = !floatingLyricsEnabled.value
            settingsRepository.setFloatingLyricsEnabled(enabled)
            messageManager.showInfo(if (enabled) "悬浮歌词已开启" else "悬浮歌词已关闭")
        }
    }

    fun addToQueue() {
        val item = playback.value.currentMediaItem ?: return
        val added = playerConnection.addMediaItem(item)
        if (added) {
            messageManager.showSuccess("已加入播放队列")
        } else {
            messageManager.showInfo("已在播放队列中")
        }
    }

    fun addTrackToQueue(album: Album, track: Track): Boolean {
        val item = MediaItemFactory.fromTrack(album, track)
        val added = playerConnection.addMediaItem(item)
        if (added) {
            messageManager.showSuccess("已加入播放队列")
        } else {
            messageManager.showInfo("已在播放队列中")
        }
        return added
    }

    suspend fun addToPlaylist(playlistId: Long): Boolean {
        val item = playback.value.currentMediaItem ?: return false
        val added = playlistRepository.addItemToPlaylist(playlistId, item)
        if (added) {
            messageManager.showSuccess("已添加到播放列表")
        } else {
            messageManager.showInfo("已在播放列表中")
        }
        return added
    }

    fun play() = playerConnection.play()
    fun pause() = playerConnection.pause()
    fun seekTo(positionMs: Long) = playerConnection.seekTo(positionMs)
    fun seekForward10s() = playerConnection.seekBy(10_000L)
    fun next() = playerConnection.skipToNext()
    fun previous() = playerConnection.skipToPrevious()
    fun playQueueIndex(index: Int) = playerConnection.seekToQueueIndex(index)
    fun removeFromQueue(index: Int) = playerConnection.removeMediaItem(index)
    fun setPlaybackSpeed(speed: Float) = playerConnection.setPlaybackSpeed(speed)
    fun setPlaybackPitch(pitch: Float) = playerConnection.setPlaybackPitch(pitch)
    fun setPlaybackParameters(speed: Float, pitch: Float) = playerConnection.setPlaybackParameters(speed, pitch)

    fun updateSessionEqualizer(settings: EqualizerSettings) {
        _sessionEqualizer.value = settings
    }

    fun saveCustomPreset(name: String, settings: EqualizerSettings) {
        viewModelScope.launch {
            settingsRepository.saveCustomPreset(
                AsmrPreset(
                    name = name,
                    bandLevels = settings.bandLevels,
                    virtualizerStrength = settings.virtualizerStrength
                )
            )
            updateSessionEqualizer(settings.copy(presetName = name))
        }
    }

    fun deleteCustomPreset(preset: AsmrPreset) {
        viewModelScope.launch { settingsRepository.deleteCustomPreset(preset.name) }
    }

    fun cyclePlayMode() {
        val snap = playback.value
        val currentMode = when {
            snap.shuffleEnabled -> 2
            snap.repeatMode == Player.REPEAT_MODE_ONE -> 1
            else -> 0
        }
        val nextMode = when (currentMode) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        val modeText = when (nextMode) {
            1 -> {
                playerConnection.setRepeatMode(Player.REPEAT_MODE_ONE)
                playerConnection.setShuffleEnabled(false)
                "单曲循环"
            }
            2 -> {
                playerConnection.setRepeatMode(Player.REPEAT_MODE_ALL)
                playerConnection.setShuffleEnabled(true)
                "随机播放"
            }
            else -> {
                playerConnection.setRepeatMode(Player.REPEAT_MODE_ALL)
                playerConnection.setShuffleEnabled(false)
                "列表循环"
            }
        }
        messageManager.showInfo("播放模式：$modeText")
        viewModelScope.launch { settingsRepository.setPlayMode(nextMode) }
    }

    fun playTracks(album: Album, tracks: List<Track>, startTrack: Track) {
        if (playerConnection.getControllerOrNull() == null) {
            messageManager.showError("播放器未连接")
            return
        }
        if (startTrack.path.contains(".m3u8", ignoreCase = true)) {
            messageManager.showError("当前不支持 m3u8 流媒体，请先下载音频文件")
            return
        }
        val items = tracks.map { MediaItemFactory.fromTrack(album, it) }
        val index = tracks.indexOfFirst { it.path == startTrack.path }.coerceAtLeast(0)
        playerConnection.setQueue(items, index, playWhenReady = true)
    }

    fun playVideo(title: String, uriOrPath: String) {
        playVideo(title = title, uriOrPath = uriOrPath, artworkUri = "", artist = "")
    }

    fun playVideo(title: String, uriOrPath: String, artworkUri: String, artist: String) {
        val trimmed = uriOrPath.trim()
        if (trimmed.isBlank()) return
        val uri = if (
            trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            trimmed.toUri()
        } else {
            Uri.fromFile(File(trimmed))
        }
        val ext = trimmed.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
        val mimeType = when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            else -> "video/*"
        }
        val displayTitle = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') }
        val artwork = artworkUri.trim()
        val metadata = MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(artist.trim())
            .setArtworkUri(artwork.takeIf { it.isNotBlank() }?.toUri())
            .setExtras(Bundle().apply { putBoolean("is_video", true) })
            .build()
        val item = MediaItem.Builder()
            .setMediaId(trimmed)
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
        playerConnection.setQueue(listOf(item), 0, playWhenReady = true)
    }

    fun playPlaylistItems(items: List<PlaylistItemEntity>, startItem: PlaylistItemEntity) {
        val mapped = items.mapNotNull { it.toMediaItemOrNull()?.let { mi -> it to mi } }
        if (mapped.isEmpty()) return
        val mediaItems = mapped.map { it.second }
        val index = mapped.indexOfFirst { (entity, _) -> entity.mediaId == startItem.mediaId }
            .takeIf { it >= 0 } ?: 0
        playerConnection.setQueue(mediaItems, index, playWhenReady = true)
    }

    fun playerOrNull(): Player? = playerConnection.getControllerOrNull()

    private suspend fun resolveDurationMs(item: MediaItem?, fallback: Long): Long {
        val safeFallback = fallback.coerceAtLeast(0L)
        if (item == null) return safeFallback
        return withContext(Dispatchers.IO) {
            val extras = item.mediaMetadata.extras
            val trackId = extras?.getLong("track_id") ?: 0L
            if (trackId > 0L) {
                val d = runCatching { trackDao.getTrackByIdOnce(trackId) }.getOrNull()?.duration ?: 0.0
                if (d > 0.0) return@withContext (d * 1000.0).roundToLong()
            }

            val uriString = item.localConfiguration?.uri?.toString().orEmpty().trim()
            val path = when {
                uriString.startsWith("file://", ignoreCase = true) -> runCatching {
                    Uri.parse(uriString).path.orEmpty()
                }.getOrDefault("")
                uriString.startsWith("/") -> uriString
                else -> ""
            }.trim()
            if (path.isNotBlank()) {
                val d = runCatching { trackDao.getTrackByPathOnce(path) }.getOrNull()?.duration ?: 0.0
                if (d > 0.0) return@withContext (d * 1000.0).roundToLong()
            }
            safeFallback
        }
    }
}

private fun PlaylistItemEntity.toMediaItemOrNull(): MediaItem? {
    val trimmed = uri.trim()
    if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) return null
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(artworkUri.takeIf { it.isNotBlank() }?.toUri())
        .build()
    val parsedUri = when {
        trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) -> trimmed.toUri()
        trimmed.startsWith("/") -> Uri.fromFile(File(trimmed))
        else -> return null
    }
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(parsedUri)
        .setMediaMetadata(metadata)
        .build()
}
