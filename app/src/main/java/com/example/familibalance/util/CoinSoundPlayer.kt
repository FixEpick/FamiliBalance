package com.example.familibalance.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Звук монет, синтезируемый на лету через AudioTrack — без res/raw,
 * 0 КБ к размеру APK. Классический аркадный «дзынь-дзынь»:
 * две короткие ноты B5 → E6 с металлическим обертоном и затуханием.
 * PCM-буфер генерируется один раз и кэшируется.
 */
object CoinSoundPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var cachedPcm: ShortArray? = null

    fun playCoin() {
        scope.launch {
            val pcm = cachedPcm ?: generateCoinPcm().also { cachedPcm = it }
            playPcm(pcm)
        }
    }

    private fun generateCoinPcm(): ShortArray {
        val sampleRate = 22050
        val note1Sec = 0.075 // первая короткая нота (Си 5 октавы)
        val note2Sec = 0.28  // вторая звонкая нота с эхом (Ми 6 октавы)
        val totalSamples = ((note1Sec + note2Sec) * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)

        val freq1 = 987.77   // B5
        val freq2 = 1318.51  // E6
        val splitSample = (note1Sec * sampleRate).toInt()

        for (i in 0 until totalSamples) {
            val (freq, localTime, decay) = if (i < splitSample) {
                Triple(freq1, i.toDouble() / sampleRate, 18.0)
            } else {
                Triple(freq2, (i - splitSample).toDouble() / sampleRate, 11.0)
            }

            // Основной тон + лёгкий металлический обертон для звонкости монеты
            val fundamental = sin(2.0 * PI * freq * localTime)
            val overtone = 0.35 * sin(2.0 * PI * (freq * 2.0) * localTime)
            val envelope = exp(-decay * localTime) // плавное затухание

            val sample = ((fundamental + overtone) * envelope * 0.55 * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun playPcm(pcm: ShortArray) {
        val sampleRate = 22050
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) {
                t.release()
            }
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
        track.play()
    }
}
