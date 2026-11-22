package com.example.familibalance.util

import android.app.Activity
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
    window.decorView.postDelayed({ exitProcess(0) }, 400)
}
