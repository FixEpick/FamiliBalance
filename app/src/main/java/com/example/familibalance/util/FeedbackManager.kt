package com.example.familibalance.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.familibalance.data.SecurePrefs
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Чиптюн-звуки и тактильный отклик детского раздела.
 *
 * PCM-буферы синтезируются программно (квадратная волна + огибающая)
 * и проигрываются через статические AudioTrack (MODE_STATIC,
 * 22050 Hz, mono, 16-bit) — без res/raw и внешних файлов.
 * Три предзагруженных звука: COIN (монетка), LASER (смена темы),
 * LEVEL_UP (фанфара). Перед проигрыванием проверяется
 * AudioManager.ringerMode — в беззвучном режиме и вибрации
 * звук не звучит, тактильный отклик работает всегда.
 * Все вызовы обёрнуты в try-catch: безопасно на эмуляторах
 * и устройствах без вибромотора/аудио.
 */
object FeedbackManager {
    private const val TAG = "FeedbackManager"
    private const val SAMPLE_RATE = 22050
    private const val PREFS = "app_prefs"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_VIBRATION = "vibration_enabled"

    @Volatile
    private var coinTrack: AudioTrack? = null
    @Volatile
    private var laserTrack: AudioTrack? = null
    @Volatile
    private var levelUpTrack: AudioTrack? = null

    // ---------- PCM-синтез ----------

    /**
     * Рендерит 8-битный чиптюн-сигнал: квадратная волна с огибающей
     * (быстрая атака ~5 мс + экспоненциальное затухание внутри сегмента),
     * чтобы не было щелчков на стыках.
     *
     * @param segments список (длительность мс, частота Гц или null для sweep)
     * @param sweepTo если задан — частота линейно спадает от freq к sweepTo
     *   внутри каждого сегмента (используется для LASER).
     */
    private fun renderChiptune(
        segments: List<Pair<Int, Double>>,
        sweepTo: Double? = null
    ): ShortArray {
        val totalMs = segments.sumOf { it.first }
        val totalSamples = totalMs * SAMPLE_RATE / 1000
        val out = ShortArray(totalSamples)
        var sampleIdx = 0

        for ((durMs, freq) in segments) {
            val segSamples = durMs * SAMPLE_RATE / 1000
            for (i in 0 until segSamples) {
                if (sampleIdx >= totalSamples) break
                val tSec = i.toDouble() / SAMPLE_RATE
                // Линейный sweep внутри сегмента (для LASER), иначе постоянная частота.
                val f = if (sweepTo != null) {
                    freq + (sweepTo - freq) * (i.toDouble() / segSamples)
                } else freq
                // Квадратная волна: +1/-1 по знаку синуса.
                val square = if (sin(2.0 * PI * f * tSec) >= 0) 1.0 else -1.0
                // Огибающая: атака 5 мс, затем экспоненциальное затухание к концу сегмента.
                val attack = (i.toDouble() / (0.005 * SAMPLE_RATE)).coerceAtMost(1.0)
                val decay = exp(-4.0 * i.toDouble() / segSamples)
                val env = attack * decay
                val sample = (square * env * 0.45 * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[sampleIdx++] = sample.toShort()
            }
        }
        return out
    }

    private fun renderCoin(): ShortArray = renderChiptune(
        listOf(60 to 988.0, 140 to 1319.0)
    )

    private fun renderLaser(): ShortArray = renderChiptune(
        listOf(90 to 1100.0),
        sweepTo = 280.0
    )

    private fun renderLevelUp(): ShortArray = renderChiptune(
        listOf(
            75 to 523.0,   // C5
            75 to 659.0,   // E5
            75 to 784.0,   // G5
            75 to 1046.0   // C6
        )
    )

    // ---------- AudioTrack ----------

    private fun buildTrack(pcm: ShortArray): AudioTrack? = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { track ->
                track.write(pcm, 0, pcm.size)
            }
    } catch (e: Throwable) {
        Log.w(TAG, "buildTrack failed: ${e.message}")
        null
    }

    @Synchronized
    private fun ensureTracks() {
        if (coinTrack != null) return
        coinTrack = buildTrack(renderCoin())
        laserTrack = buildTrack(renderLaser())
        levelUpTrack = buildTrack(renderLevelUp())
    }

    private fun isSoundAllowed(context: Context): Boolean = try {
        val prefs = SecurePrefs.get(context, PREFS)
        if (!prefs.getBoolean(KEY_SOUND, true)) return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.ringerMode == AudioManager.RINGER_MODE_NORMAL
    } catch (e: Throwable) {
        false
    }

    private fun isVibrationAllowed(context: Context): Boolean = try {
        SecurePrefs.get(context, PREFS).getBoolean(KEY_VIBRATION, true)
    } catch (e: Throwable) {
        true
    }

    private fun playTrack(context: Context, track: AudioTrack?) {
        if (!isSoundAllowed(context) || track == null) return
        try {
            // stop()+reloadStaticData() обязательны: без этого быстрые
            // повторные клики могут кидать IllegalStateException.
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: Throwable) {
            Log.w(TAG, "play failed: ${e.message}")
        }
    }

    // ---------- Публичный API ----------

    /** Смена темы: «лазер» (sweep 1100→280 Гц) + лёгкий TextHandleMove. */
    fun playLaser(context: Context, haptic: HapticFeedback) {
        hapticSafe(context, haptic, HapticFeedbackType.TextHandleMove)
        ensureTracks()
        playTrack(context, laserTrack)
    }

    /** Выполнение задания / награда / серия без штрафов: «монетка» + LongPress. */
    fun playCoin(context: Context, haptic: HapticFeedback) {
        hapticSafe(context, haptic, HapticFeedbackType.LongPress)
        ensureTracks()
        playTrack(context, coinTrack)
    }

    /** Достижение/цель-мечта/закрытие серии: фанфара LEVEL_UP + двойная вибрация. */
    fun playLevelUp(context: Context, haptic: HapticFeedback) {
        hapticSafe(context, haptic, HapticFeedbackType.LongPress)
        ensureTracks()
        playTrack(context, levelUpTrack)
    }

    /** Обычный клик: только лёгкая тактильная отдача, без звука. */
    fun playClick(context: Context, haptic: HapticFeedback) {
        hapticSafe(context, haptic, HapticFeedbackType.TextHandleMove)
    }

    private fun hapticSafe(context: Context, haptic: HapticFeedback, type: HapticFeedbackType) {
        if (!isVibrationAllowed(context)) return
        try {
            haptic.performHapticFeedback(type)
        } catch (e: Throwable) {
            Log.w(TAG, "haptic failed: ${e.message}")
        }
    }

    /** Освобождает AudioTrack-ресурсы. Вызывать при уничтожении приложения. */
    @Synchronized
    fun release() {
        listOf(coinTrack, laserTrack, levelUpTrack).forEach { track ->
            try {
                track?.let {
                    it.stop()
                    it.release()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "release failed: ${e.message}")
            }
        }
        coinTrack = null
        laserTrack = null
        levelUpTrack = null
    }
}
