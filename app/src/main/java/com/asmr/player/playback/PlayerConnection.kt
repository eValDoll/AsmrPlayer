package com.asmr.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.service.PlaybackService
import com.asmr.player.util.MessageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val messageManager: MessageManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val _queue = MutableStateFlow<List<androidx.media3.common.MediaItem>>(emptyList())
    val queue: StateFlow<List<androidx.media3.common.MediaItem>> = _queue.asStateFlow()

    private var controller: MediaController? = null
    private var lastErrorAtMs: Long = 0L
    private var lastErrorKey: String = ""

    init {
        scope.launch {
            connect()
        }
        scope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    val duration = c.duration.coerceAtLeast(0L)
                    var sessionId = _snapshot.value.audioSessionId
                    
                    // If session ID is missing, try to fetch it
                    if (sessionId == 0 || sessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                        try {
                            val cmd = androidx.media3.session.SessionCommand("GET_AUDIO_SESSION_ID", android.os.Bundle.EMPTY)
                            val result = awaitSessionResult(context, c.sendCustomCommand(cmd, android.os.Bundle.EMPTY))
                            if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                                val fetchedId = result.extras.getInt("AUDIO_SESSION_ID")
                                if (fetchedId != 0) {
                                    sessionId = fetchedId
                                    android.util.Log.d("PlayerConnection", "Polled Session ID: $sessionId")
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore errors during polling
                        }
                    }

                    _snapshot.value = _snapshot.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = duration,
                        audioSessionId = sessionId,
                        playbackSpeed = c.playbackParameters.speed,
                        playbackPitch = c.playbackParameters.pitch
                    )
                }
                delay(250)
            }
        }
    }

    private suspend fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val c = awaitMediaController(context, future)
        controller = c
        c.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    val prev = _snapshot.value
                    _snapshot.value = player.toSnapshot(isConnected = true, audioSessionId = prev.audioSessionId)
                    if (
                        events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                    ) {
                        updateQueue()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    _snapshot.value = _snapshot.value.copy(audioSessionId = audioSessionId)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val now = System.currentTimeMillis()
                    val key = "${error.errorCodeName}:${error.message.orEmpty()}"
                    if (key != lastErrorKey || now - lastErrorAtMs > 2_000L) {
                        lastErrorKey = key
                        lastErrorAtMs = now
                        val item = controller?.currentMediaItem
                        val uri = item?.localConfiguration?.uri?.toString().orEmpty()
                        val msg = if (uri.contains(".m3u8", ignoreCase = true)) {
                            "当前不支持 m3u8 流媒体，请先下载音频文件"
                        } else {
                            "播放失败：${error.errorCodeName}"
                        }
                        messageManager.showError(msg)
                        android.util.Log.e(
                            "PlayerConnection",
                            "Player error: ${error.errorCodeName} ${error.message} uri=$uri mediaId=${item?.mediaId}",
                            error
                        )
                    }
                }
            }
        )
        _snapshot.value = c.toSnapshot(isConnected = true, audioSessionId = _snapshot.value.audioSessionId)
        updateQueue()

        val mode = runCatching { settingsRepository.playMode.first() }.getOrDefault(0)
        applyPlayModeToController(c, mode)
        
        try {
            val cmd = androidx.media3.session.SessionCommand("GET_AUDIO_SESSION_ID", android.os.Bundle.EMPTY)
            val result = awaitSessionResult(context, c.sendCustomCommand(cmd, android.os.Bundle.EMPTY))
            if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                val sessionId = result.extras.getInt("AUDIO_SESSION_ID")
                if (sessionId != 0) {
                     _snapshot.value = _snapshot.value.copy(audioSessionId = sessionId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getControllerOrNull(): MediaController? = controller

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val duration = c.duration.coerceAtLeast(0L)
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        val bounded = if (duration > 0L) target.coerceAtMost(duration) else target
        c.seekTo(bounded)
    }

    fun seekToQueueIndex(index: Int) {
        val c = controller ?: return
        val safe = index.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        c.seekToDefaultPosition(safe)
        c.play()
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun setRepeatMode(repeatMode: Int) {
        controller?.repeatMode = repeatMode
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun setPlaybackPitch(pitch: Float) {
        val c = controller ?: return
        val cur = c.playbackParameters
        c.setPlaybackParameters(PlaybackParameters(cur.speed, pitch.coerceIn(0.5f, 2f)))
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val c = controller ?: return
        c.setPlaybackParameters(
            PlaybackParameters(
                speed.coerceIn(0.5f, 2f),
                pitch.coerceIn(0.5f, 2f)
            )
        )
    }

    fun setQueue(items: List<androidx.media3.common.MediaItem>, startIndex: Int, playWhenReady: Boolean) {
        val c = controller ?: return
        c.setMediaItems(items, startIndex.coerceAtLeast(0), 0L)
        c.prepare()
        if (playWhenReady) c.play()
        _queue.value = items
    }

    fun addMediaItem(item: androidx.media3.common.MediaItem): Boolean {
        val c = controller ?: return false
        val count = c.mediaItemCount
        for (i in 0 until count) {
            if (c.getMediaItemAt(i).mediaId == item.mediaId) {
                // Already exists, do not add
                return false
            }
        }
        c.addMediaItem(item)
        return true
    }

    fun removeMediaItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun sendCustomCommand(action: String, args: android.os.Bundle) {
        val c = controller ?: return
        val cmd = androidx.media3.session.SessionCommand(action, android.os.Bundle.EMPTY)
        c.sendCustomCommand(cmd, args)
    }

    private fun updateQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { idx -> c.getMediaItemAt(idx) }
    }
}

private fun applyPlayModeToController(controller: MediaController, mode: Int) {
    when (mode) {
        1 -> {
            controller.repeatMode = Player.REPEAT_MODE_ONE
            controller.shuffleModeEnabled = false
        }
        2 -> {
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.shuffleModeEnabled = true
        }
        else -> {
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.shuffleModeEnabled = false
        }
    }
}

private fun Player.toSnapshot(isConnected: Boolean, audioSessionId: Int): PlaybackSnapshot {
    return PlaybackSnapshot(
        isConnected = isConnected,
        isPlaying = isPlaying,
        playbackState = playbackState,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleModeEnabled,
        playbackSpeed = playbackParameters.speed,
        playbackPitch = playbackParameters.pitch,
        currentMediaItem = currentMediaItem,
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.coerceAtLeast(0L),
        audioSessionId = audioSessionId
    )
}

private suspend fun awaitMediaController(
    context: Context,
    future: com.google.common.util.concurrent.ListenableFuture<MediaController>
): MediaController {
    return suspendCancellableCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: CancellationException) {
                    cont.cancel(e)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        cont.invokeOnCancellation { future.cancel(true) }
    }
}

private suspend fun awaitSessionResult(
    context: Context,
    future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult>
): androidx.media3.session.SessionResult {
    return suspendCancellableCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        cont.invokeOnCancellation { future.cancel(true) }
    }
}
