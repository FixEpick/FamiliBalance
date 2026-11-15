package com.example.familibalance.data.supabase

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException

/** Загрузка/удаление фото квестов в Supabase Storage (бакет quest-photos). */
internal object PhotoStorage {

    suspend fun uploadPhotoToStorage(context: Context, userId: String, uri: Uri): String? {
        val supabase = SupabaseManager.client ?: return null
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8)
            val fileName = "$userId/quest_${System.currentTimeMillis()}_$uniqueId.jpg"
            val bucket = supabase.storage.from("quest-photos")
            bucket.upload(fileName, bytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "Ошибка загрузки фото в Storage: ${e.message}")
            null
        }
    }

    suspend fun deleteQuestPhoto(photoUrl: String): Boolean {
        if (photoUrl.isBlank()) {
            return true
        }

        val supabase = SupabaseManager.client ?: return true

        // Извлекаем путь относительно бакета quest-photos, убирая query-параметры
        val fileName = photoUrl
            .substringAfter("/quest-photos/", "")
            .substringBefore("?")
            .substringBefore("#")

        if (fileName.isBlank()) {
            Log.w("SupabaseManager", "Не удалось извлечь путь к файлу из URL: $photoUrl")
            return false
        }

        return try {
            supabase.storage.from("quest-photos").delete(fileName)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "Ошибка удаления фото из Storage: ${e.message}")
            false
        }
    }
}
