package com.example.familibalance

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.familibalance.data.SecurePrefs
import java.util.Locale

/**
 * Глобальное состояние языка интерфейса.
 * Хранится в SharedPreferences ("app_prefs" / "app_locale"),
 * tag — BCP-47 тег ("ru", "en").
 */
object AppLocaleState {
    private const val PREFS = "app_prefs"
    private const val KEY_LOCALE = "app_locale"

    var tag: String by mutableStateOf("ru")
        private set

    fun init(context: Context) {
        tag = SecurePrefs.get(context, PREFS)
            .getString(KEY_LOCALE, "ru") ?: "ru"
    }

    fun set(context: Context, newTag: String) {
        if (newTag == tag) return
        tag = newTag
        SecurePrefs.get(context, PREFS)
            .edit().putString(KEY_LOCALE, newTag).apply()
    }
}

/**
 * Оборачивает контент в Configuration с локалью [AppLocaleState.tag],
 * чтобы stringResource() возвращал строки выбранного языка без AppCompat.
 */
@Composable
fun LocalizedAppContent(content: @Composable () -> Unit) {
    val localeTag = AppLocaleState.tag
    val baseConfig = LocalConfiguration.current
    val context = LocalContext.current
    val localizedContext = remember(localeTag, baseConfig) {
        val config = Configuration(baseConfig).apply {
            setLocale(Locale.forLanguageTag(localeTag))
        }
        context.createConfigurationContext(config)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content
    )
}
