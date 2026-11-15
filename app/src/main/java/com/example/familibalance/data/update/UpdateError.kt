package com.example.familibalance.data.update

/**
 * Типизированные ошибки слоя обновлений.
 * UI маппит их в локализованные строки через stringResource —
 * data-слой не зависит от Android-ресурсов.
 */
sealed class UpdateError {
    /** Подпись скачанного APK не совпадает с установленным приложением. */
    data object SignatureMismatch : UpdateError()

    /** Скачивание не удалось (сеть, HTTP-ошибка, пустой файл, исключение). */
    data object DownloadFailed : UpdateError()

    /** Контрольная сумма SHA-256 не сошлась — файл повреждён или подменён. */
    data object ChecksumMismatch : UpdateError()

    /** В релизе нет привязанного APK-ассета. */
    data object NoApkInRelease : UpdateError()

    /** Нет разрешения на установку из неизвестных источников. */
    data object PermissionDenied : UpdateError()

    /** Непредвиденная ошибка; message — для логов, не для UI. */
    data class Unknown(val message: String? = null) : UpdateError()
}
