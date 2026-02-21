package com.asmr.player.playback

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStateDataStore by preferencesDataStore(name = "playback_state")

data class PersistedPlaybackState(
    val queueMediaIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val speed: Float,
    val pitch: Float,
    val savedAtEpochMs: Long
)

@Singleton
class PlaybackStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = Gson()
    private val key: Preferences.Key<String> = stringPreferencesKey("persisted_playback_state_v1")

    suspend fun save(state: PersistedPlaybackState) {
        withContext(Dispatchers.IO) {
            context.playbackStateDataStore.edit { prefs ->
                prefs[key] = gson.toJson(state)
            }
        }
    }

    suspend fun load(): PersistedPlaybackState? {
        return withContext(Dispatchers.IO) {
            val prefs = context.playbackStateDataStore.data.first()
            val json = prefs[key].orEmpty()
            if (json.isBlank()) return@withContext null
            runCatching {
                gson.fromJson(json, PersistedPlaybackState::class.java)
            }.getOrNull()
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            context.playbackStateDataStore.edit { prefs ->
                prefs.remove(key)
            }
        }
    }
}
