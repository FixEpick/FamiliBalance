package com.example.familibalance.feature.child

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.screens.*

@Composable
fun QuestDeliveryScreen(
    questName: String,
    rewardText: String,
    onSendClick: (comment: String, photoUri: Uri?) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var commentText by remember { mutableStateOf("") }
    var capturedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            try {
                val currentTempUri = tempCameraUri
                if (currentTempUri != null) {
                    val permanentFile = java.io.File(context.filesDir, "quest_${System.currentTimeMillis()}.jpg")
                    
                    val inputStream = context.contentResolver.openInputStream(currentTempUri)
                    if (inputStream != null) {
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream.close()

                        val maxDimension = 1080
                        var sampleSize = 1
                        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                            sampleSize *= 2
                        }

                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        val secondInput = context.contentResolver.openInputStream(currentTempUri)
                        val bitmap = BitmapFactory.decodeStream(secondInput, null, decodeOptions)
                        secondInput?.close()

                        if (bitmap != null) {
                            permanentFile.outputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                            }
                            bitmap.recycle()
                        } else {
                            context.contentResolver.openInputStream(currentTempUri)?.use { input ->
                                permanentFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }

                    val permanentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        permanentFile
                    )
                    capturedImageUri = permanentUri

                    // Удаляем временный файл камеры из cacheDir — он больше не нужен
                    try {
                        if (currentTempUri.scheme == "file") {
                            currentTempUri.path?.let { java.io.File(it).delete() }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Ошибка обработки фото: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val launchCamera = {
        try {
            context.cacheDir.mkdirs()
            val photoFile = java.io.File.createTempFile("quest_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Не удалось запустить камеру: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Требуется разрешение на использование камеры", Toast.LENGTH_SHORT).show()
        }
    }

    val checkAndLaunchCamera = {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Шапка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, GlassStroke, CircleShape)
                    .clip(CircleShape)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "←",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = questName,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Зона камеры / предпросмотра фото
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                .clickable { checkAndLaunchCamera() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBackground)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (capturedImageUri != null) {
                    AsyncImage(
                        model = capturedImageUri,
                        contentDescription = "Фото результата",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = "📷", fontSize = 54.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Нажми, чтобы сфотографировать результат.\nНикаких ограничений по времени!",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (capturedImageUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { checkAndLaunchCamera() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor)
            ) {
                Text("🔄 Переснять фото")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода комментария
        OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = {
                Text(
                    "Напиши папе пару слов к фото, если хочешь (например: «Всё сложил по местам!»)",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GlassBackground,
                unfocusedContainerColor = GlassBackground,
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = GlassStroke,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        // Нижняя бирюзовая кнопка (активируется при наличии фото)
        Button(
            onClick = { onSendClick(commentText, capturedImageUri) },
            enabled = capturedImageUri != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = SpaceBgStart,
                disabledContainerColor = GlassBackground,
                disabledContentColor = TextSecondary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "Прикрепить фото и отправить на проверку",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
