package com.example.familibalance.feature.child.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.familibalance.R
import com.example.familibalance.ui.theme.TextPrimary
import java.io.File

/**
 * Встроенная камера на CameraX — замена системному Intent TakePicture.
 *
 * Процесс не уходит в фон: превью и захват живут внутри нашего Activity,
 * поэтому OS не убивает приложение и не происходит рестарта на главном экране.
 *
 * Фото сохраняется в filesDir/quest_photos/quest_<timestamp>.jpg и
 * даунскейлится до 1080px JPEG — та же логика, что была в колбэке
 * TakePicture в QuestDeliveryScreen.
 */
@Composable
fun CameraCaptureScreen(
    onImageCaptured: (File) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing by remember { mutableStateOf(false) }

    // Привязка камеры к жизненному циклу composable: при смене
    // lensFacing эффект пересоздаётся (unbindAll → bind), при уходе
    // с экрана onDispose отвязывает камеру — PreviewView не утекает.
    DisposableEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        }, mainExecutor)
        onDispose {
            cameraProviderFuture.addListener({
                runCatching { cameraProviderFuture.get().unbindAll() }
            }, mainExecutor)
        }
    }

    val capturePhoto = lit@{
        if (isCapturing) return@lit
        isCapturing = true
        val photosDir = File(context.filesDir, "quest_photos").apply { mkdirs() }
        val photoFile = File(photosDir, "quest_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                downscaleQuestPhoto(photoFile)
                isCapturing = false
                onImageCaptured(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                isCapturing = false
                Toast.makeText(
                    context,
                    context.getString(R.string.delivery_error_photo, exception.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Верхняя панель: закрыть + переключить камеру
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .semantics { contentDescription = context.getString(R.string.camera_capture_close) }
            ) {
                Text("✕", color = TextPrimary, fontSize = 20.sp)
            }
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .semantics { contentDescription = context.getString(R.string.camera_capture_switch) }
            ) {
                Text("🔄", fontSize = 20.sp)
            }
        }

        // Кнопка съёмки
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(4.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                .clickable(enabled = !isCapturing, onClick = capturePhoto)
                .semantics { contentDescription = context.getString(R.string.camera_capture_shoot) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(if (isCapturing) Color.Gray else Color.White)
            )
        }
    }
}

/**
 * Даунскейл фото до 1080px с перезаписью файла — логика перенесена
 * из колбэка TakePicture в QuestDeliveryScreen без изменений.
 */
private fun downscaleQuestPhoto(photoFile: File) {
    try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        photoFile.inputStream().use { BitmapFactory.decodeStream(it, null, options) }

        val maxDimension = 1080
        var sampleSize = 1
        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = photoFile.inputStream().use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return // оригинал камеры оставляем как есть

        photoFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        bitmap.recycle()
    } catch (e: Exception) {
        e.printStackTrace()
        // Оставляем оригинальный файл — сжатие не критично для отправки.
    }
}
