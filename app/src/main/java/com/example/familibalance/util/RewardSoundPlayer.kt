package com.example.familibalance.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.familibalance.R

/**
 * Синглтон SoundPool для коротких игровых звуков (звон монет и т.п.).
 *
 * SoundPool, а не MediaPlayer/ToneGenerator: сэмпл держится в памяти,
 * проигрывается мгновенно и позволяет накладывать до 4 потоков —
 * быстрые повторные нажатия «Забрать награду» не захлёбываются.
 *
 * Инициализируется один раз из MainActivity.onCreate, чтобы первый
 * клик не попал в ещё не загруженный сэмпл. Если [playCoin] вызван
 * до окончания загрузки — воспроизведение откладывается до onLoadComplete.
 */
object RewardSoundPlayer {

    private var soundPool: SoundPool? = null
    private var coinSoundId = 0
    @Volatile
    private var coinLoaded = false
    @Volatile
    private var pendingCoinPlay = false

    fun init(context: Context) {
        if (soundPool != null) return
        val appContext = context.applicationContext
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { pool ->
                // Listener ДО load(): иначе быстрый onLoadComplete может
                // прийти до подписки и coinLoaded навсегда останется false.
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (sampleId == coinSoundId && status == 0) {
                        coinLoaded = true
                        if (pendingCoinPlay) {
                            pendingCoinPlay = false
                            playInternal(pool)
                        }
                    }
                }
                coinSoundId = pool.load(appContext, R.raw.coin_clink, 1)
            }
    }

    /** Звон монет. Лёгкий разброс pitch убирает эффект «роботизированного пулемёта». */
    fun playCoin() {
        val pool = soundPool ?: return
        if (!coinLoaded) {
            pendingCoinPlay = true
            return
        }
        playInternal(pool)
    }

    private fun playInternal(pool: SoundPool) {
        val pitch = (95..105).random() / 100f
        pool.play(coinSoundId, 1f, 1f, 1, 0, pitch)
    }
}
