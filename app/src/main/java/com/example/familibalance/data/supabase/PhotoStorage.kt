package com.example.familibalance.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Загрузка/удаление фото квестов в Supabase Storage (бакет quest-photos). */
internal object PhotoStorage {

    /** Максимальная сторона фото после сжатия — хватает для проверки родителем. */
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80

    suspend fun uploadPhotoToStorage(context: Context, userId: String, uri: Uri): String? {
        val supabase = SupabaseManager.client ?: return null
        return try {
            val bytes = withContext(Dispatchers.IO) { readCompressedBytes(context, uri) }
                ?: return null
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

    /**
     * Читает фото и сжимает: длинная сторона ≤ [MAX_DIMENSION], JPEG [JPEG_QUALITY].
     * Большие фото из галереи (12+ МБ) не влезали в 30-секундный таймаут
     * uploadIfLocal — push отменялся, и родитель получал только пуш без квеста.
     * Если декодирование не удалось (HEIC и т.п.) — отдаём исходные байты.
     */
    private fun readCompressedBytes(context: Context, uri: Uri): ByteArray? {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return raw

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, decodeOpts)
            ?: return raw

        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        val maxSide = maxOf(width, height)
        var sample = 1
        while (maxSide / (sample * 2) >= MAX_DIMENSION) sample *= 2
        return sample
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
