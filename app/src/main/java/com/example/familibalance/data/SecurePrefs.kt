package com.example.familibalance.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Фабрика зашифрованных SharedPreferences (SEC-07).
 *
 * При первом запуске после обновления старый незашифрованный файл
 * не читается: probe-доступ `prefs.all` бросает исключение при попытке
 * расшифровать plaintext. В этом случае файл удаляется и создаётся
 * чистое зашифрованное хранилище — приложение работает как при первой
 * установке (пустые значения), но последующие записи сохраняются.
 * Если и это не удалось (например, нет AndroidKeyStore) — возвращается
 * in-memory заглушка, чтобы приложение не падало.
 */
object SecurePrefs {

    // Один экземпляр на файл: EncryptedSharedPreferences.create() при каждом
    // вызове возвращает новый wrapper, а InMemoryPrefs-fallback вообще
    // не персистит — кэш гарантирует, что все читатели/писатели делят
    // одно хранилище.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, SharedPreferences>()

    fun get(context: Context, fileName: String): SharedPreferences {
        cache[fileName]?.let { return it }
        val appContext = context.applicationContext
        val prefs = try {
            val created = create(appContext, fileName)
            created.all // probe: принудительная дешифровка, бросает на legacy-файле
            created
        } catch (e: Exception) {
            try {
                appContext.deleteSharedPreferences(fileName)
                val created = create(appContext, fileName)
                created.all
                created
            } catch (e2: Exception) {
                InMemoryPrefs()
            }
        }
        return cache.putIfAbsent(fileName, prefs) ?: prefs
    }

    @Suppress("DEPRECATION")
    private fun create(context: Context, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Пустое in-memory хранилище: приложение не падает, но данные не персистятся. */
    private class InMemoryPrefs : SharedPreferences {
        private val map = HashMap<String, Any?>()
        private val listeners = HashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?): String? =
            map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) {
            listeners += listener
        }
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) {
            listeners -= listener
        }

        private inner class EditorImpl : SharedPreferences.Editor {
            private val changes = HashMap<String, Any?>()
            private val toRemove = HashSet<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                apply { changes[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
                apply { changes[key] = values }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor =
                apply { changes[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor =
                apply { changes[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
                apply { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
                apply { changes[key] = value }
            override fun remove(key: String): SharedPreferences.Editor =
                apply { toRemove += key }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() {
                applyChanges()
            }

            private fun applyChanges() {
                val touched = HashSet<String>()
                if (clearAll) {
                    touched += map.keys
                    map.clear()
                }
                toRemove.forEach { map.remove(it); touched += it }
                changes.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                    touched += k
                }
                touched.forEach { key ->
                    listeners.forEach { it.onSharedPreferenceChanged(this@InMemoryPrefs, key) }
                }
            }
        }
    }
}
