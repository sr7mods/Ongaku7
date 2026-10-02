package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Realtime 8D Spatial Audio Engine for ExoPlayer.
 * Implements full 360-degree orbital LFO panning, Interaural Time Difference (ITD),
 * and acoustic reflections directly in the audio playback pipeline.
 */
@UnstableApi
class EightDAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var speed: Float = 0.15f // LFO rotation frequency in Hz (default: Classic 0.15 Hz)

    @Volatile
    var depth: Float = 90.0f // Spatial rotation depth 0%..100% (default: Classic 90%)

    private var currentPhase: Double = 0.0

    // Spatial acoustic reflection delay line (~35ms)
    private var reflectionBufferLeft = FloatArray(44100)
    private var reflectionBufferRight = FloatArray(44100)
    private var reflectionPos = 0

    // Interaural Time Difference (ITD) delay line (~1ms max, 64 samples at 44.1kHz)
    private var itdBufferLeft = FloatArray(128)
    private var itdBufferRight = FloatArray(128)
    private var itdPos = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }

        val sampleRate = inputAudioFormat.sampleRate
        val reflectionSamples = (sampleRate * 0.035).toInt().coerceAtLeast(512)
        if (reflectionBufferLeft.size != reflectionSamples) {
            reflectionBufferLeft = FloatArray(reflectionSamples)
            reflectionBufferRight = FloatArray(reflectionSamples)
            reflectionPos = 0
        }

        val itdSamples = (sampleRate * 0.002).toInt().coerceAtLeast(64)
        if (itdBufferLeft.size != itdSamples) {
            itdBufferLeft = FloatArray(itdSamples)
            itdBufferRight = FloatArray(itdSamples)
            itdPos = 0
        }

        // Always produce stereo output (2 channels) preserving input sample rate and encoding
        return AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun isActive(): Boolean {
        // Must always remain active in the Media3 pipeline so toggling 8D on/off is instant
        return outputAudioFormat != AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val encoding = inputAudioFormat.encoding
        val channelCount = inputAudioFormat.channelCount
        val sampleRate = inputAudioFormat.sampleRate

        if (encoding == C.ENCODING_PCM_16BIT) {
            process16BitPcm(inputBuffer, remaining, channelCount, sampleRate)
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            processFloatPcm(inputBuffer, remaining, channelCount, sampleRate)
        }
    }

    private fun process16BitPcm(
        inputBuffer: ByteBuffer,
        remaining: Int,
        channelCount: Int,
        sampleRate: Int
    ) {
        val bytesPerFrame = channelCount * 2
        val frames = remaining / bytesPerFrame
        val outputBytes = frames * 4 // 2 channels * 2 bytes = 4 bytes per stereo frame

        val out = replaceOutputBuffer(outputBytes)
        val is8DActive = enabled
        val radiansPerSample = (2.0 * Math.PI * speed.toDouble()) / sampleRate.toDouble()
        val reflectionLen = reflectionBufferLeft.size
        val itdLen = itdBufferLeft.size
        val sqrt2 = 1.4142135623730951

        for (i in 0 until frames) {
            val rawLeft = inputBuffer.short.toFloat()
            val rawRight = if (channelCount >= 2) inputBuffer.short.toFloat() else rawLeft
            // Discard any additional surround channels if input has > 2 channels
            for (c in 2 until channelCount) {
                inputBuffer.short
            }

            if (!is8DActive) {
                // Bit-perfect stereo passthrough
                out.putShort(rawLeft.toInt().coerceIn(-32768, 32767).toShort())
                out.putShort(rawRight.toInt().coerceIn(-32768, 32767).toShort())
            } else {
                // 360-degree orbital LFO modulation
                // Phase angle theta rotates continuously around listener's head
                val theta = currentPhase
                currentPhase += radiansPerSample
                if (currentPhase >= 2.0 * Math.PI) {
                    currentPhase -= 2.0 * Math.PI
                }

                // x is horizontal pan (-1.0 = full left, +1.0 = full right)
                val depthFactor = (depth.toDouble() / 100.0).coerceIn(0.0, 1.0)
                val x = Math.sin(theta) * depthFactor
                // y is front-to-back elevation (+1.0 = front, -1.0 = behind head)
                val y = Math.cos(theta)
                // Head shadow / distance attenuation when audio is behind listener
                val rearShadow = 1.0 - 0.15 * ((1.0 - y) / 2.0)

                // Equal-power stereo gain curve
                val panAngle = (x + 1.0) * (Math.PI / 4.0) // 0 to PI/2
                val leftGain = (Math.cos(panAngle) * sqrt2 * rearShadow).toFloat()
                val rightGain = (Math.sin(panAngle) * sqrt2 * rearShadow).toFloat()

                // Interaural Time Delay (ITD): sound reaches contralateral ear slightly delayed
                // Max delay ~0.65ms (approx 29 samples at 44.1kHz)
                val maxItdDelay = (sampleRate * 0.00065f).coerceAtLeast(1f)
                val itdOffset = (Math.abs(x).toFloat() * maxItdDelay).toInt().coerceIn(0, itdLen - 1)

                val rPos = reflectionPos
                val iPos = itdPos

                // Store current dry samples into delay lines
                reflectionBufferLeft[rPos] = rawLeft
                reflectionBufferRight[rPos] = rawRight
                itdBufferLeft[iPos] = rawLeft
                itdBufferRight[iPos] = rawRight

                // Calculate delayed sample indices
                val readItdIdx = (iPos - itdOffset + itdLen) % itdLen
                val delayedItdLeft = itdBufferLeft[readItdIdx]
                val delayedItdRight = itdBufferRight[readItdIdx]

                // Acoustic reflection from environment (spatial room feeling)
                val refLeft = reflectionBufferLeft[(rPos + 1) % reflectionLen]
                val refRight = reflectionBufferRight[(rPos + 1) % reflectionLen]

                // Apply ITD and gains:
                // If sound is to the right (x > 0), left ear hears delayed signal;
                // If sound is to the left (x < 0), right ear hears delayed signal.
                val earL = if (x > 0) {
                    (rawLeft * (1f - x.toFloat() * 0.5f) + delayedItdLeft * (x.toFloat() * 0.5f)) * leftGain
                } else {
                    rawLeft * leftGain
                }

                val earR = if (x < 0) {
                    val absX = -x.toFloat()
                    (rawRight * (1f - absX * 0.5f) + delayedItdRight * (absX * 0.5f)) * rightGain
                } else {
                    rawRight * rightGain
                }

                // Add subtle crossfeed acoustic reflections (~12%) for room immersion
                val finalL = (earL + refRight * 0.12f).coerceIn(-32768f, 32767f)
                val finalR = (earR + refLeft * 0.12f).coerceIn(-32768f, 32767f)

                reflectionPos = (rPos + 1) % reflectionLen
                itdPos = (iPos + 1) % itdLen

                out.putShort(finalL.toInt().toShort())
                out.putShort(finalR.toInt().toShort())
            }
        }

        out.flip()
    }

    private fun processFloatPcm(
        inputBuffer: ByteBuffer,
        remaining: Int,
        channelCount: Int,
        sampleRate: Int
    ) {
        val bytesPerFrame = channelCount * 4
        val frames = remaining / bytesPerFrame
        val outputBytes = frames * 8 // 2 channels * 4 bytes float = 8 bytes

        val out = replaceOutputBuffer(outputBytes)
        val is8DActive = enabled
        val radiansPerSample = (2.0 * Math.PI * speed.toDouble()) / sampleRate.toDouble()
        val reflectionLen = reflectionBufferLeft.size
        val itdLen = itdBufferLeft.size
        val sqrt2 = 1.4142135623730951

        for (i in 0 until frames) {
            val rawLeft = inputBuffer.float
            val rawRight = if (channelCount >= 2) inputBuffer.float else rawLeft
            for (c in 2 until channelCount) {
                inputBuffer.float
            }

            if (!is8DActive) {
                out.putFloat(rawLeft)
                out.putFloat(rawRight)
            } else {
                val theta = currentPhase
                currentPhase += radiansPerSample
                if (currentPhase >= 2.0 * Math.PI) {
                    currentPhase -= 2.0 * Math.PI
                }

                val depthFactor = (depth.toDouble() / 100.0).coerceIn(0.0, 1.0)
                val x = Math.sin(theta) * depthFactor
                val y = Math.cos(theta)
                val rearShadow = 1.0 - 0.15 * ((1.0 - y) / 2.0)

                val panAngle = (x + 1.0) * (Math.PI / 4.0)
                val leftGain = (Math.cos(panAngle) * sqrt2 * rearShadow).toFloat()
                val rightGain = (Math.sin(panAngle) * sqrt2 * rearShadow).toFloat()

                val maxItdDelay = (sampleRate * 0.00065f).coerceAtLeast(1f)
                val itdOffset = (Math.abs(x).toFloat() * maxItdDelay).toInt().coerceIn(0, itdLen - 1)

                val rPos = reflectionPos
                val iPos = itdPos

                reflectionBufferLeft[rPos] = rawLeft
                reflectionBufferRight[rPos] = rawRight
                itdBufferLeft[iPos] = rawLeft
                itdBufferRight[iPos] = rawRight

                val readItdIdx = (iPos - itdOffset + itdLen) % itdLen
                val delayedItdLeft = itdBufferLeft[readItdIdx]
                val delayedItdRight = itdBufferRight[readItdIdx]

                val refLeft = reflectionBufferLeft[(rPos + 1) % reflectionLen]
                val refRight = reflectionBufferRight[(rPos + 1) % reflectionLen]

                val earL = if (x > 0) {
                    (rawLeft * (1f - x.toFloat() * 0.5f) + delayedItdLeft * (x.toFloat() * 0.5f)) * leftGain
                } else {
                    rawLeft * leftGain
                }

                val earR = if (x < 0) {
                    val absX = -x.toFloat()
                    (rawRight * (1f - absX * 0.5f) + delayedItdRight * (absX * 0.5f)) * rightGain
                } else {
                    rawRight * rightGain
                }

                val finalL = (earL + refRight * 0.12f).coerceIn(-1.0f, 1.0f)
                val finalR = (earR + refLeft * 0.12f).coerceIn(-1.0f, 1.0f)

                reflectionPos = (rPos + 1) % reflectionLen
                itdPos = (iPos + 1) % itdLen

                out.putFloat(finalL)
                out.putFloat(finalR)
            }
        }

        out.flip()
    }

    override fun onFlush() {
        reflectionPos = 0
        itdPos = 0
    }

    override fun onReset() {
        reflectionPos = 0
        itdPos = 0
        currentPhase = 0.0
    }
}

