package com.example.familibalance

import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
    // LocalContext подменяется на configuration-context (не Activity) —
    // rememberLauncherForActivityResult внутри NavHost-экранов терял
    // ActivityResultRegistryOwner и падал с IllegalStateException.
    // Пробрасываем owner явно, чтобы launchers камеры/галереи работали.
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalAppLocalizedContext provides localizedContext,
        content = {
            if (activityResultRegistryOwner != null) {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
                    content = content
                )
            } else {
                content()
            }
        }
    )
}

/**
 * Локализованный контекст приложения. Окна Dialog/ModalBottomSheet
 * переопределяют LocalContext контекстом своего окна (системная локаль),
 * поэтому stringResource() внутри шторок резолвится через этот локал.
 */
val LocalAppLocalizedContext = staticCompositionLocalOf<Context> {
    error("LocalAppLocalizedContext is not provided")
}

/**
 * Оборачивает контент шторки/диалога, чтобы stringResource() внутри него
 * использовал локаль приложения, а не системную локаль устройства.
 * Применять ВНУТРИ content-лямбды ModalBottomSheet/Dialog/AlertDialog.
 */
@Composable
fun AppLocaleContent(content: @Composable () -> Unit) {
    val localizedContext = LocalAppLocalizedContext.current
    CompositionLocalProvider(LocalContext provides localizedContext, content = content)
}
