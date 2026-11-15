package com.example.familibalance.data

import java.security.MessageDigest
import java.util.UUID

/**
 * Хэширование PIN-кодов (SHA-256 -> Hex).
 * В хранилище и Supabase сохраняется только хэш — открытый PIN не покидает устройство.
 * Поддерживает два формата:
 *  - legacy: чистый SHA-256 hex (без соли) или открытый текст;
 *  - salted: "salt:sha256(salt:pin)" — защищает от rainbow-таблиц.
 * [matches] обратно совместим со старыми профилями.
 */
object PinHasher {

    /** Случайная соль для нового PIN (UUID, 36 символов, без ':'). */
    fun generateSalt(): String = UUID.randomUUID().toString()

    /**
     * Хэш PIN. При пустой [salt] — legacy-формат (чистый hex).
     * При заданной соли — "salt:hex".
     */
    fun hash(pin: String, salt: String = ""): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = if (salt.isEmpty()) pin else "$salt:$pin"
        val hex = digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return if (salt.isEmpty()) hex else "$salt:$hex"
    }

    /**
     * Проверка введённого PIN против сохранённого значения.
     * Формат "salt:hash" — сверка по salted-хэшу; иначе legacy:
     * чистый SHA-256 или открытый текст.
     */
    fun matches(input: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        if (stored == input) return true // legacy: открытый текст
        return if (stored.contains(':')) {
            stored == hash(input, stored.substringBefore(':'))
        } else {
            stored == hash(input)
        }
    }
}
