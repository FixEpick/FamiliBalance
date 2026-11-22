package com.example.familibalance.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import kotlin.system.exitProcess

/**
 * Гарантированно завершает приложение.
 *
 * finishAffinity() закрывает все Activity задачи, но на части прошивок
 * (и при launchMode="singleTask") задача/процесс остаётся жить —
 * приложение визуально «не выходит». Поэтому после старта завершения
 * Activity добиваем процесс через exitProcess (штатный System.exit,
 * не SIGKILL). Задержка даёт Activity пройти onPause/onStop/onDestroy,
 * чтобы не ловить WIN DEATH.
 */
fun Activity.finishApp() {
    finishAffinity()
    // Handler на главном Looper, а не View.postDelayed: после finishAffinity
    // decorView отсоединяется, и postDelayed на отсоединённом View никогда
    // не выполнится — приложение оставалось живым («Выйти» не работало).
    Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 400)
}
