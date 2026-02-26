package com.asmr.player.service

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.asmr.player.MainActivity
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.TrackPlaybackProgressEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.data.settings.AudioEffectController
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.data.repository.StatisticsRepository
import com.asmr.player.playback.AsmrRenderersFactory
import com.asmr.player.playback.BalanceAudioProcessor
import com.asmr.player.playback.FadingPlayer
import com.asmr.player.playback.GainAudioProcessor
import com.asmr.player.playback.StereoFftAnalyzer
import com.asmr.player.playback.StereoPcmRingBuffer
import com.asmr.player.playback.StereoSpectrumBus
import com.asmr.player.playback.StereoSpectrumTapAudioProcessor
import com.asmr.player.playback.VolumeFader
import com.asmr.player.util.EmbeddedMediaExtractor
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: EnvironmentalReverb? = null
    private val balanceAudioProcessor = BalanceAudioProcessor()
    private val gainAudioProcessor = GainAudioProcessor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val volumeFader = VolumeFader(serviceScope)
    private val spectrumPcmBuffer = StereoPcmRingBuffer(frameSize = 1024, slotCount = 8)
    private val spectrumAnalyzer = StereoFftAnalyzer(
        pcmBuffer = spectrumPcmBuffer,
        spectrumStore = StereoSpectrumBus.store,
        scope = serviceScope,
        fftSize = 1024,
        binCount = StereoSpectrumBus.DefaultBinCount
    )
    private val spectrumTapAudioProcessor = StereoSpectrumTapAudioProcessor(spectrumPcmBuffer) { sr ->
        spectrumAnalyzer.setSampleRate(sr)
    }
    
    // Temporary settings for current session
    private val sessionSettings = MutableStateFlow<EqualizerSettings?>(null)
    private var orbitJob: Job? = null
    private var effectApplyJob: Job? = null
    @Volatile private var manualBalance: Float = 0f
    @Volatile private var manualGain: Float = 1f
    @Volatile private var orbitEnabled: Boolean = false
    @Volatile private var orbitSpeed: Float = 25f
    @Volatile private var orbitDistance: Float = 5f
    @Volatile private var currentAudioSessionId: Int = 0
    @Volatile private var lastEffectiveSettings: EqualizerSettings = EqualizerSettings()
    
    private var currentLyrics: List<SubtitleEntry> = emptyList()
    private var lyricsIndexFinder: SubtitleIndexFinder? = null
    private var lastLyricIndex: Int = Int.MIN_VALUE
    private var floatingLyricsEnabled: Boolean = false
    private var overlay: FloatingLyricsOverlay? = null

    @Inject
    lateinit var audioEffectController: AudioEffectController

    @Inject
    lateinit var lyricsLoader: LyricsLoader

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var statisticsRepository: StatisticsRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private var lastMarkedMediaId: String? = null
    private var lastMarkedElapsedMs: Long = 0L

    private var statsJob: Job? = null
    private var currentTrackListenedMs: Long = 0L
    private var isCurrentTrackCounted: Boolean = false
    private var currentMediaId: String? = null
    private var lastProgressPersistElapsedMs: Long = 0L

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        runCatching {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val sr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            val fpb = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
            val bufferMs = if (sr != null && fpb != null && sr > 0 && fpb > 0) {
                (fpb * 1000) / sr
            } else {
                20
            }
            val delayMs = (bufferMs * 3).coerceIn(0, 200)
            spectrumAnalyzer.setVisualDelayMs(delayMs)
        }
        val authStore = DlsiteAuthStore(applicationContext)
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(DLSITE_UA)
        
        val transferListener = object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork) {
                    serviceScope.launch {
                        statisticsRepository.addNetworkTraffic(bytesTransferred.toLong())
                    }
                }
            }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        }

        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            ResolvingDataSource.Factory(httpFactory) { dataSpec ->
                val uri = dataSpec.uri
                val host = uri.host.orEmpty().lowercase()
                val newHeaders = LinkedHashMap(dataSpec.httpRequestHeaders)
                var headersChanged = false
                if (host.endsWith("dlsite.com") || host.endsWith("chobit.cc")) {
                    newHeaders["User-Agent"] = DLSITE_UA
                    newHeaders["Accept-Language"] = NetworkHeaders.ACCEPT_LANGUAGE
                    newHeaders["Referer"] = "https://www.dlsite.com/"
                    val cookie = buildDlsiteCookieHeader(authStore.getDlsiteCookie())
                    if (cookie.isNotBlank() && !newHeaders.containsKey("Cookie")) {
                        newHeaders["Cookie"] = cookie
                    }
                    headersChanged = true
                }

                val b = dataSpec.buildUpon()
                if (headersChanged) b.setHttpRequestHeaders(newHeaders)
                b.build()
            }
        ).apply {
            setTransferListener(transferListener)
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(AsmrRenderersFactory(this, gainAudioProcessor, balanceAudioProcessor, spectrumTapAudioProcessor))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        spectrumAnalyzer.start()
        startEffectLoops()

        val sessionPlayer = FadingPlayer(
            delegate = exoPlayer,
            volumeFader = volumeFader,
            playFadeMs = 1000L,
            pauseFadeMs = 500L,
            switchFadeOutMs = 250L,
            switchFadeInMs = 250L
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(createContentIntent())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val base = super.onConnect(session, controller)
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(androidx.media3.session.SessionCommand("GET_AUDIO_SESSION_ID", android.os.Bundle.EMPTY))
                        .add(androidx.media3.session.SessionCommand("UPDATE_SESSION_EQ", android.os.Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(base.availablePlayerCommands)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: androidx.media3.session.SessionCommand,
                    args: android.os.Bundle
                ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                    when (customCommand.customAction) {
                        "GET_AUDIO_SESSION_ID" -> {
                            val resultBundle = android.os.Bundle()
                            resultBundle.putInt("AUDIO_SESSION_ID", exoPlayer.audioSessionId)
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, resultBundle)
                            )
                        }
                        "UPDATE_SESSION_EQ" -> {
                            val prev = sessionSettings.value ?: EqualizerSettings()
                            val enabled = if (args.containsKey("enabled")) args.getBoolean("enabled") else prev.enabled
                            val levels = args.getIntArray("levels")?.toList() ?: prev.bandLevels
                            val virt = if (args.containsKey("virtualizer")) args.getInt("virtualizer") else prev.virtualizerStrength
                            val bal = if (args.containsKey("balance")) args.getFloat("balance") else prev.balance
                            val preset = args.getString("preset") ?: prev.presetName
                            val gain = if (args.containsKey("gain")) args.getFloat("gain") else prev.originalGain
                            val reverbEnabled = if (args.containsKey("reverbEnabled")) args.getBoolean("reverbEnabled") else prev.reverbEnabled
                            val reverbPreset = args.getString("reverbPreset") ?: prev.reverbPreset
                            val reverbWet = if (args.containsKey("reverbWet")) args.getInt("reverbWet") else prev.reverbWet
                            val orbitEnabled = if (args.containsKey("orbitEnabled")) args.getBoolean("orbitEnabled") else prev.orbitEnabled
                            val orbitSpeed = if (args.containsKey("orbitSpeed")) args.getFloat("orbitSpeed") else prev.orbitSpeed
                            val orbitDistance = if (args.containsKey("orbitDistance")) args.getFloat("orbitDistance") else prev.orbitDistance
                            sessionSettings.value = prev.copy(
                                enabled = enabled,
                                bandLevels = levels,
                                virtualizerStrength = virt,
                                balance = bal,
                                presetName = preset,
                                originalGain = gain,
                                reverbEnabled = reverbEnabled,
                                reverbPreset = reverbPreset,
                                reverbWet = reverbWet,
                                orbitEnabled = orbitEnabled,
                                orbitSpeed = orbitSpeed,
                                orbitDistance = orbitDistance
                            )
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, android.os.Bundle.EMPTY)
                            )
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        setMediaNotificationProvider(LyricMediaNotificationProvider(this))
        overlay = FloatingLyricsOverlay(this)
        
        // Basic Equalizer setup
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    Log.i("PlaybackService", "AudioSessionId changed: $audioSessionId")
                    setupEqualizer(audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                StereoSpectrumBus.playbackActive = isPlaying
                if (isPlaying) {
                    markCurrentAlbumPlayed()
                } else {
                    serviceScope.launch { persistCurrentTrackProgressIfNeeded(force = true) }
                }
                val sid = exoPlayer.audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET && sid != 0 && sid != currentAudioSessionId) {
                    setupEqualizer(sid)
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                serviceScope.launch { updateArtworkForCurrentMedia() }
                serviceScope.launch { loadLyricsForCurrentMedia() }
                if (exoPlayer.isPlaying) {
                    markCurrentAlbumPlayed()
                }
                
                // Reset track stats for new item
                currentMediaId = mediaItem?.mediaId
                currentTrackListenedMs = 0L
                isCurrentTrackCounted = false
                lastProgressPersistElapsedMs = 0L
            }
        })
        StereoSpectrumBus.playbackActive = exoPlayer.isPlaying
        val currentSessionId = exoPlayer.audioSessionId
        if (currentSessionId != C.AUDIO_SESSION_ID_UNSET && currentSessionId != 0) {
            Log.i("PlaybackService", "Initial AudioSessionId: $currentSessionId")
            setupEqualizer(currentSessionId)
        }

        statsJob = serviceScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    statisticsRepository.addListeningDuration(1000L)
                    
                    currentTrackListenedMs += 1000L
                    val totalDuration = exoPlayer.duration
                    if (!isCurrentTrackCounted && totalDuration > 0 && currentTrackListenedMs > totalDuration * 0.25) {
                        statisticsRepository.incrementTrackCount()
                        isCurrentTrackCounted = true
                    }
                    persistCurrentTrackProgressIfNeeded(force = false)
                }
                delay(1000L)
            }
        }

        serviceScope.launch {
            settingsRepository.floatingLyricsEnabled.collect { enabled ->
                floatingLyricsEnabled = enabled
                if (!enabled) {
                    overlay?.hide()
                } else {
                    lastLyricIndex = Int.MIN_VALUE
                }
            }
        }
        serviceScope.launch {
            settingsRepository.floatingLyricsSettings.collect { settings ->
                overlay?.applySettings(settings)
            }
        }
        serviceScope.launch(Dispatchers.Default) {
            loadLyricsForCurrentMedia()
            updateArtworkForCurrentMedia()
            while (isActive) {
                val nextDelayMs = updateLyricsTick()
                delay(nextDelayMs)
            }
        }
    }

    private suspend fun updateArtworkForCurrentMedia() {
        val (index, item) = withContext(Dispatchers.Main.immediate) {
            exoPlayer.currentMediaItemIndex to exoPlayer.currentMediaItem
        }
        if (index < 0) return
        if (item == null) return

        val uriString = item.localConfiguration?.uri?.toString().orEmpty().trim()
        if (uriString.isBlank()) return
        if (uriString.startsWith("http", ignoreCase = true)) return

        val mime = item.localConfiguration?.mimeType.orEmpty()
        val ext = uriString.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
        val isVideo = item.mediaMetadata.extras?.getBoolean("is_video") == true ||
            mime.startsWith("video/") ||
            ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
        if (isVideo) return

        val cacheKey = "track:" + (item.mediaId.ifBlank { uriString })
        val file = EmbeddedMediaExtractor.getArtworkCacheFile(applicationContext, cacheKey)

        if (!file.exists() || file.length() <= 0L) {
            val bmp = EmbeddedMediaExtractor.extractArtwork(applicationContext, uriString) ?: return
            val saved = EmbeddedMediaExtractor.saveArtworkToCache(applicationContext, cacheKey, bmp) ?: return
            if (saved.isBlank()) return
        }
        if (!file.exists() || file.length() <= 0L) return

        val newUri = Uri.fromFile(file)
        val oldUri = item.mediaMetadata.artworkUri
        if (oldUri != null && oldUri.toString() == newUri.toString()) return

        val updatedMeta = item.mediaMetadata.buildUpon().setArtworkUri(newUri).build()
        val updatedItem = item.buildUpon().setMediaMetadata(updatedMeta).build()
        withContext(Dispatchers.Main.immediate) {
            if (exoPlayer.currentMediaItemIndex == index) {
                exoPlayer.replaceMediaItem(index, updatedItem)
            }
        }
    }

    private fun markCurrentAlbumPlayed() {
        val item = exoPlayer.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val albumId = extras.getLong("album_id", -1L)
        if (albumId <= 0L) return

        val mediaId = item.mediaId
        val nowElapsed = SystemClock.elapsedRealtime()
        if (mediaId == lastMarkedMediaId && nowElapsed - lastMarkedElapsedMs < 5_000L) return
        lastMarkedMediaId = mediaId
        lastMarkedElapsedMs = nowElapsed

        val playedAt = System.currentTimeMillis()
        serviceScope.launch(Dispatchers.IO) {
            runCatching { database.playStatDao().markAlbumPlayed(albumId, playedAt) }
        }
    }

    private suspend fun persistCurrentTrackProgressIfNeeded(force: Boolean) {
        data class Snapshot(
            val mediaId: String,
            val albumId: Long,
            val trackId: Long,
            val positionMs: Long,
            val durationMs: Long
        )

        val snapshot = withContext(Dispatchers.Main.immediate) {
            val item = exoPlayer.currentMediaItem
            val extras = item?.mediaMetadata?.extras
            Snapshot(
                mediaId = item?.mediaId.orEmpty(),
                albumId = extras?.getLong("album_id", -1L) ?: -1L,
                trackId = extras?.getLong("track_id", -1L) ?: -1L,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            )
        }

        if (snapshot.mediaId.isBlank()) return
        if (snapshot.albumId <= 0L) return

        val durationMs = snapshot.durationMs
        val isCompleted = if (durationMs > 0L) {
            val remaining = (durationMs - snapshot.positionMs).coerceAtLeast(0L)
            remaining <= 10_000L || snapshot.positionMs.toDouble() / durationMs.toDouble() >= 0.95
        } else {
            false
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val shouldPersist = force || isCompleted || nowElapsed - lastProgressPersistElapsedMs >= 10_000L
        if (!shouldPersist) return
        lastProgressPersistElapsedMs = nowElapsed

        withContext(Dispatchers.IO) {
            val dao = database.trackPlaybackProgressDao()
            val now = System.currentTimeMillis()
            val existing = dao.getByMediaId(snapshot.mediaId)
            val mergedDurationMs = when {
                snapshot.durationMs > 0L -> snapshot.durationMs
                existing != null && existing.durationMs > 0L -> existing.durationMs
                else -> 0L
            }
            val mergedCompleted = existing?.completed == true || isCompleted
            val mergedPositionMs = when {
                mergedDurationMs > 0L -> snapshot.positionMs.coerceIn(0L, mergedDurationMs)
                else -> snapshot.positionMs
            }

            dao.upsert(
                TrackPlaybackProgressEntity(
                    mediaId = snapshot.mediaId,
                    albumId = snapshot.albumId,
                    trackId = snapshot.trackId.takeIf { it > 0L },
                    positionMs = mergedPositionMs,
                    durationMs = mergedDurationMs,
                    completed = mergedCompleted,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(LYRICS_CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            LYRICS_CHANNEL_ID,
            "播放控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            description = "用于后台播放控制与锁屏媒体面板"
        }
        manager.createNotificationChannel(channel)
    }

    private suspend fun loadLyricsForCurrentMedia() {
        val (mediaId, fallback) = withContext(Dispatchers.Main.immediate) {
            val item = exoPlayer.currentMediaItem
            item?.mediaId.orEmpty() to item?.mediaMetadata?.title?.toString().orEmpty()
        }
        val result = lyricsLoader.load(mediaId, fallback)
        currentLyrics = result.lyrics
        lyricsIndexFinder = if (result.lyrics.isNotEmpty()) SubtitleIndexFinder(result.lyrics) else null
        lastLyricIndex = Int.MIN_VALUE
    }

    private suspend fun updateLyricsTick(): Long {
        data class TickState(
            val overlayNeeded: Boolean,
            val positionMs: Long,
            val isPlaying: Boolean
        )

        val state = withContext(Dispatchers.Main.immediate) {
            val overlayReady = overlay?.canDraw() == true
            val need = floatingLyricsEnabled && overlayReady
            if (need && overlay?.isShown() != true) overlay?.show()
            if (!need && overlay?.isShown() == true) overlay?.hide()
            TickState(
                overlayNeeded = need,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                isPlaying = exoPlayer.isPlaying
            )
        }
        val overlayNeeded = state.overlayNeeded
        val positionMs = state.positionMs
        val playing = state.isPlaying

        if (!overlayNeeded) return 1_000L
        val lyrics = currentLyrics
        if (lyrics.isEmpty()) {
            withContext(Dispatchers.Main.immediate) {
                if (overlayNeeded) overlay?.updateLine("暂无歌词")
            }
            return 2_000L
        }

        val idx = lyricsIndexFinder?.findActiveIndex(positionMs) ?: -1
        if (idx != lastLyricIndex) {
            lastLyricIndex = idx

            val current = lyrics.getOrNull(idx)?.text.orEmpty().ifBlank { " " }
            val next = lyrics.getOrNull(idx + 1)?.text.orEmpty()
            withContext(Dispatchers.Main.immediate) {
                if (overlayNeeded) overlay?.updateLine(current)
            }
        }

        val nextStartMs = when {
            idx + 1 in lyrics.indices -> lyrics[idx + 1].startMs
            idx < 0 && lyrics.isNotEmpty() -> lyrics.first().startMs
            else -> null
        }
        val rawDelay = nextStartMs?.let { it - positionMs } ?: 2_000L
        val maxDelay = if (playing) 2_000L else 1_500L
        return rawDelay.coerceIn(200L, maxDelay)
    }

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            currentAudioSessionId = audioSessionId
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            virtualizer?.release()
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
            }
            reverb?.release()
            reverb = EnvironmentalReverb(0, audioSessionId).apply {
                enabled = true
            }
            applyEffectsToCurrentSession(lastEffectiveSettings)
        } catch (e: Exception) {
            Log.e("PlaybackService", "setupEqualizer failed (sessionId=$audioSessionId)", e)
        }
    }

    private fun startEffectLoops() {
        effectApplyJob?.cancel()
        effectApplyJob = serviceScope.launch {
            combine(audioEffectController.equalizerSettings, sessionSettings) { global, session ->
                session ?: global
            }.collect { settings ->
                lastEffectiveSettings = settings
                manualBalance = settings.balance
                manualGain = settings.originalGain.coerceIn(0f, 2f)
                orbitEnabled = settings.orbitEnabled
                orbitSpeed = settings.orbitSpeed
                orbitDistance = settings.orbitDistance

                if (!orbitEnabled) {
                    balanceAudioProcessor.setBalance(manualBalance)
                    gainAudioProcessor.setGain(manualGain)
                }

                applyEffectsToCurrentSession(settings)
            }
        }

        orbitJob?.cancel()
        orbitJob = serviceScope.launch {
            var phase = 0f
            val twoPi = (Math.PI * 2.0).toFloat()
            while (isActive) {
                val enabled = orbitEnabled
                if (!enabled) {
                    balanceAudioProcessor.setBalance(manualBalance)
                    gainAudioProcessor.setGain(manualGain)
                    delay(50)
                    continue
                }
                val speed = orbitSpeed.coerceIn(0f, 50f)
                val distance = orbitDistance.coerceIn(0f, 10f)
                val panAmount = (distance / 10f).coerceIn(0f, 0.9f)
                val angularVel = (speed / 25f) * (twoPi / 4f)
                phase += angularVel * 0.033f
                if (phase > twoPi) phase -= twoPi
                val orbitOffset = kotlin.math.sin(phase) * panAmount
                val finalBalance = (manualBalance + orbitOffset).coerceIn(-1f, 1f)
                val attenuation = 1f / (1f + distance * 0.12f)
                balanceAudioProcessor.setBalance(finalBalance)
                gainAudioProcessor.setGain(manualGain * attenuation)
                delay(33)
            }
        }
    }

    private fun applyEffectsToCurrentSession(settings: EqualizerSettings) {
        val eq = equalizer
        val virt = virtualizer
        if (eq != null && virt != null) {
            runCatching {
                audioEffectController.applyTo(eq, settings)
                if (settings.enabled) {
                    virt.enabled = true
                    virt.setStrength(settings.virtualizerStrength.toShort())
                } else {
                    virt.enabled = false
                }
            }.onFailure { e ->
                Log.w("PlaybackService", "apply equalizer failed (sessionId=$currentAudioSessionId)", e)
            }
        }

        val rev = reverb
        if (rev != null) {
            runCatching { applyReverb(rev, settings) }.onFailure { e ->
                Log.w("PlaybackService", "apply reverb failed (sessionId=$currentAudioSessionId)", e)
            }
        }
    }

    private fun applyReverb(reverb: EnvironmentalReverb, settings: EqualizerSettings) {
        val enabled = settings.reverbEnabled && settings.reverbPreset != "无"
        if (reverb.enabled != enabled) reverb.enabled = enabled
        if (!enabled) return
        val wet = settings.reverbWet.coerceIn(0, 100)
        val wetMb = (-9000 + wet * 90).coerceIn(-9000, 0)
        val roomMb = (wetMb - 1200).coerceIn(-9000, 0)
        reverb.reverbLevel = wetMb.toShort()
        reverb.roomLevel = roomMb.toShort()
        when (settings.reverbPreset) {
            "电话" -> {
                reverb.decayTime = 260
                reverb.decayHFRatio = 300
                reverb.roomHFLevel = (-7000).toShort()
                reverb.reflectionsLevel = (roomMb - 800).toShort()
                reverb.reflectionsDelay = 5
                reverb.reverbDelay = 12
                reverb.diffusion = 250
                reverb.density = 200
            }
            "教堂" -> {
                reverb.decayTime = 5200
                reverb.decayHFRatio = 850
                reverb.roomHFLevel = (-2500).toShort()
                reverb.reflectionsLevel = (roomMb - 600).toShort()
                reverb.reflectionsDelay = 18
                reverb.reverbDelay = 35
                reverb.diffusion = 1000
                reverb.density = 1000
            }
            "大厅" -> {
                reverb.decayTime = 2600
                reverb.decayHFRatio = 700
                reverb.roomHFLevel = (-3200).toShort()
                reverb.reflectionsLevel = (roomMb - 700).toShort()
                reverb.reflectionsDelay = 12
                reverb.reverbDelay = 26
                reverb.diffusion = 900
                reverb.density = 900
            }
            "电影院" -> {
                reverb.decayTime = 1200
                reverb.decayHFRatio = 600
                reverb.roomHFLevel = (-4200).toShort()
                reverb.reflectionsLevel = (roomMb - 900).toShort()
                reverb.reflectionsDelay = 10
                reverb.reverbDelay = 20
                reverb.diffusion = 700
                reverb.density = 800
            }
            "餐厅" -> {
                reverb.decayTime = 850
                reverb.decayHFRatio = 550
                reverb.roomHFLevel = (-4800).toShort()
                reverb.reflectionsLevel = (roomMb - 1000).toShort()
                reverb.reflectionsDelay = 8
                reverb.reverbDelay = 16
                reverb.diffusion = 600
                reverb.density = 650
            }
            "卫生间" -> {
                reverb.decayTime = 1600
                reverb.decayHFRatio = 900
                reverb.roomHFLevel = (-600).toShort()
                reverb.reflectionsLevel = (roomMb - 500).toShort()
                reverb.reflectionsDelay = 6
                reverb.reverbDelay = 14
                reverb.diffusion = 800
                reverb.density = 850
            }
            "室内" -> {
                reverb.decayTime = 520
                reverb.decayHFRatio = 500
                reverb.roomHFLevel = (-5200).toShort()
                reverb.reflectionsLevel = (roomMb - 1100).toShort()
                reverb.reflectionsDelay = 6
                reverb.reverbDelay = 14
                reverb.diffusion = 450
                reverb.density = 500
            }
            "反馈弹簧" -> {
                reverb.decayTime = 1400
                reverb.decayHFRatio = 1000
                reverb.roomHFLevel = (-900).toShort()
                reverb.reflectionsLevel = (roomMb - 300).toShort()
                reverb.reflectionsDelay = 3
                reverb.reverbDelay = 8
                reverb.diffusion = 1000
                reverb.density = 900
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ?: PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    override fun onDestroy() {
        overlay?.hide()
        statsJob?.cancel()
        effectApplyJob?.cancel()
        orbitJob?.cancel()
        spectrumAnalyzer.stop()
        equalizer?.release()
        virtualizer?.release()
        reverb?.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private companion object {
        private const val DLSITE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val LYRICS_CHANNEL_ID = "playback"
    }
}
