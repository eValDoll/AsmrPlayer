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

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var minDb: Float = -24f

    @Volatile
    private var maxDb: Float = -6f

    private var passthrough = false
    private var currentGain = 1f
    private var sampleRateHz = 44_100

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
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

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        passthrough =
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        currentGain = 1f
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
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val rms = computeRms(inputBuffer, encoding)
        val db = if (rms <= 1e-9f) -120f else (20f * log10(rms))

        val minT = minDb
        val maxT = maxDb
        val desiredGainDb = when {
            db > maxT -> (maxT - db)
            db < minT -> (minT - db)
            else -> 0f
        }
        var desiredGain = dbToGain(desiredGainDb)
        desiredGain = desiredGain.coerceIn(0f, 8f)

        val frameCount = when (encoding) {
            androidx.media3.common.C.ENCODING_PCM_FLOAT -> countBytes / 4 / inputAudioFormat.channelCount
            else -> countBytes / 2 / inputAudioFormat.channelCount
        }
        val dtSec = if (sampleRateHz > 0) frameCount.toDouble() / sampleRateHz.toDouble() else 0.0
        val attackMs = 30.0
        val releaseMs = 200.0
        val tauMs = if (desiredGain < currentGain) attackMs else releaseMs
        val alpha = if (dtSec <= 0.0) 0.0 else exp(-dtSec / (tauMs / 1000.0))
        currentGain = (desiredGain + (currentGain - desiredGain) * alpha.toFloat()).coerceIn(0f, 8f)

        val g = currentGain
        if (g == 1f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 4) {
                val v = inputBuffer.float
                outputBuffer.putFloat((v * g).coerceIn(-1f, 1f))
            }
        } else {
            while (inputBuffer.remaining() >= 2) {
                val v = inputBuffer.short
                val out = (v * g).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outputBuffer.putShort(out)
            }
        }
        outputBuffer.flip()
    }

    private fun computeRms(input: ByteBuffer, encoding: Int): Float {
        val dup = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        var n = 0
        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            while (dup.remaining() >= 4) {
                val v = dup.float.toDouble()
                sumSq += v * v
                n++
            }
        } else {
            while (dup.remaining() >= 2) {
                val s = dup.short.toInt()
                val v = (s / 32768.0)
                sumSq += v * v
                n++
            }
        }
        if (n <= 0) return 0f
        return sqrt(sumSq / n.toDouble()).toFloat()
    }

    private fun dbToGain(db: Float): Float {
        val dbDouble = db.toDouble()
        return exp(dbDouble * ln(10.0) / 20.0).toFloat()
    }
}

