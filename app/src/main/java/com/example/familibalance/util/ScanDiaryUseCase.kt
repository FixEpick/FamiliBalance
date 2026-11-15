package com.example.familibalance.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScanDiaryUseCase(private val context: Context) {
    suspend operator fun invoke(photoUrl: String): Map<Int, Int> {
        val bitmap = withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(photoUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            (result.image?.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
        } ?: throw IllegalStateException("Не удалось загрузить фото")

        val visionText = withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                suspendCancellableCoroutine<Text> { cont ->
                    val task = recognizer.process(image)
                    task.addOnSuccessListener { result -> cont.resume(result) }
                    task.addOnFailureListener { e -> cont.resumeWithException(e) }
                    cont.invokeOnCancellation { /* Task не отменяемый */ }
                }
            } finally {
                recognizer.close()
            }
        }
        return parseGrades(visionText.text)
    }
}
