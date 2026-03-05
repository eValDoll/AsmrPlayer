package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

@UnstableApi
class VolumeThresholdAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val MODE_THRESHOLD = 0
        const val MODE_LOUDNESS = 1
    }

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var minDb: Float = -24f

    @Volatile
    private var maxDb: Float = -6f

    @Volatile
    private var mode: Int = MODE_LOUDNESS

    @Volatile
    private var loudnessTargetDb: Float = -18f

    private var passthrough = false
    private var currentGain = 1f
    private var sampleRateHz = 44_100
    private val peakSafety = 0.95f
    private val boostNoiseFloorDb = -75f
    private var loudnessEmaPower = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setMode(mode: Int) {
        this.mode = mode.coerceIn(MODE_THRESHOLD, MODE_LOUDNESS)
    }

    fun setThresholds(minDb: Float, maxDb: Float) {
        var minV = minDb
        var maxV = maxDb
        if (minV >= maxV) {
            minV = maxV - 1f
        }
        this.minDb = minV
        this.maxDb = maxV
    }

    fun setLoudnessTargetDb(targetDb: Float) {
        loudnessTargetDb = targetDb.coerceIn(-60f, 0f)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        passthrough =
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        currentGain = 1f
        loudnessEmaPower = 0.0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val countBytes = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(countBytes)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val enabledSnapshot = enabled
        if (passthrough || !enabledSnapshot) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val encoding = inputAudioFormat.encoding
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val bytesPerSample = if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) 4 else 2
        val frameCount = countBytes / (bytesPerSample * channels)
        val dtSec = if (sampleRateHz > 0) frameCount.toDouble() / sampleRateHz.toDouble() else 0.0
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val levels = computeLevels(inputBuffer, encoding)
        val rms = levels.rms
        val peak = levels.peak
        val db = if (rms <= 1e-9f) -120f else (20f * log10(rms))

        val modeSnapshot = mode
        val minGain = 0.125f
        var desiredGain = when (modeSnapshot) {
            MODE_LOUDNESS -> {
                if (dtSec > 0.0 && db > boostNoiseFloorDb) {
                    val power = (rms * rms).toDouble()
                    val tauSec = 1.5
                    val alpha = exp(-dtSec / tauSec)
                    loudnessEmaPower = if (loudnessEmaPower <= 0.0) power else (loudnessEmaPower * alpha + power * (1.0 - alpha))
                }
                val loudDb = if (loudnessEmaPower <= 1e-12) -120f else (10f * log10(loudnessEmaPower.toFloat()))
                val deltaDb = loudnessTargetDb - loudDb
                var g = dbToGain(deltaDb).coerceIn(minGain, 8f)
                if (db <= boostNoiseFloorDb && g > 1f) g = 1f
                g
            }
            else -> {
                val minT = minDb
                val maxT = maxDb
                val desiredGainDb = when {
                    db > maxT -> (maxT - db)
                    db < minT && db > boostNoiseFloorDb -> (minT - db)
                    else -> 0f
                }
                dbToGain(desiredGainDb).coerceIn(minGain, 8f)
            }
        }

        if (desiredGain > 1f && peak > 1e-9f) {
            val peakCap = (peakSafety / peak).coerceAtMost(8f)
            desiredGain = minOf(desiredGain, peakCap)
        }

        if (desiredGain == 1f && currentGain == 1f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val attackMs = if (modeSnapshot == MODE_LOUDNESS) 300.0 else 30.0
        val releaseMs = if (modeSnapshot == MODE_LOUDNESS) 3_000.0 else 200.0
        val sr = sampleRateHz
        val alphaAttack = if (sr > 0) exp(-1.0 / (sr.toDouble() * (attackMs / 1000.0))).toFloat() else 0f
        val alphaRelease = if (sr > 0) exp(-1.0 / (sr.toDouble() * (releaseMs / 1000.0))).toFloat() else 0f

        var gain = currentGain

        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 4 * channels) {
                val alpha = if (desiredGain < gain) alphaAttack else alphaRelease
                gain = (desiredGain + (gain - desiredGain) * alpha).coerceIn(minGain, 8f)
                repeat(channels) {
                    val v = inputBuffer.float
                    outputBuffer.putFloat((v * gain).coerceIn(-1f, 1f))
                }
            }
        } else {
            while (inputBuffer.remaining() >= 2 * channels) {
                val alpha = if (desiredGain < gain) alphaAttack else alphaRelease
                gain = (desiredGain + (gain - desiredGain) * alpha).coerceIn(minGain, 8f)
                repeat(channels) {
                    val v = inputBuffer.short
                    val out = (v * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    outputBuffer.putShort(out)
                }
            }
        }
        currentGain = gain
        outputBuffer.flip()
    }

    private data class Levels(
        val rms: Float,
        val peak: Float,
    )

    private fun computeLevels(input: ByteBuffer, encoding: Int): Levels {
        val dup = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        var peak = 0.0
        var n = 0
        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            while (dup.remaining() >= 4) {
                val v = dup.float.toDouble()
                val av = kotlin.math.abs(v)
                if (av > peak) peak = av
                sumSq += v * v
                n++
            }
        } else {
            while (dup.remaining() >= 2) {
                val s = dup.short.toInt()
                val v = (s / 32768.0)
                val av = kotlin.math.abs(v)
                if (av > peak) peak = av
                sumSq += v * v
                n++
            }
        }
        if (n <= 0) return Levels(0f, 0f)
        val rms = sqrt(sumSq / n.toDouble()).toFloat()
        return Levels(rms, peak.toFloat().coerceIn(0f, 1f))
    }

    private fun dbToGain(db: Float): Float {
        val dbDouble = db.toDouble()
        return exp(dbDouble * ln(10.0) / 20.0).toFloat()
    }
}
