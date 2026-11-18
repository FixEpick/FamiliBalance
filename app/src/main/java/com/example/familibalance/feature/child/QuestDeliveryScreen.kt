package com.example.familibalance.feature.child

import android.annotation.SuppressLint
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistryOwner
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*

@Composable
fun QuestDeliveryScreen(
    questName: String,
    rewardText: String,
    onSendClick: (comment: String, photoUri: Uri?) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Страховка от IllegalStateException "No ActivityResultRegistryOwner":
    // LocalContext может быть переопределён (LocalizedAppContent, окна Dialog),
    // поэтому owner берём из реальной Activity через LocalActivity.
    val activity = LocalActivity.current
    val registryOwner = activity as? ActivityResultRegistryOwner
    if (registryOwner != null) {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
            QuestDeliveryScreenContent(
                questName = questName,
                rewardText = rewardText,
                onSendClick = onSendClick,
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
    } else {
        QuestDeliveryScreenContent(
            questName = questName,
            rewardText = rewardText,
            onSendClick = onSendClick,
            onBackClick = onBackClick,
            modifier = modifier
        )
    }
}

// Toast'ы в колбэках камеры (ActivityResult) — не composable-контекст,
// поэтому stringResource там недоступен; context.getString здесь оправдан.
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun QuestDeliveryScreenContent(
    questName: String,
    rewardText: String,
    onSendClick: (comment: String, photoUri: Uri?) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var commentText by rememberSaveable { mutableStateOf("") }
    // URI храним строкой: rememberSaveable + String гарантированно
    // переживают process death (камера — отдельная Activity).
    var capturedImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val capturedImageUri = capturedImageUriString?.let {
        runCatching { Uri.parse(it) }.getOrNull()
    }
    // Файл фото в filesDir/quest_photos — единственная копия. Удаляем его
    // только при явной отмене (стрелка «Назад»/системный back) или при
    // пересъёмке. Переключение вкладок и process death файл НЕ трогают —
    // иначе фото «слетало» бы при возврате на экран.
    var capturedPhotoFile by remember { mutableStateOf<java.io.File?>(null) }
    // URI и имя файла камеры храним строками: переживают process death
    // без кастомного Saver'а. Камера пишет сразу в filesDir/quest_photos/ —
    // файл не зависит от очистки cacheDir.
    var tempCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var tempPhotoName by rememberSaveable { mutableStateOf<String?>(null) }
    // Блокирует «Назад»/повторную отправку, пока submitQuestForReview не вернётся:
    // иначе экран закрывался до завершения persist+push и пользователь видел
    // «выкидывание» на главный экран без подтверждения.
    var isSubmitting by remember { mutableStateOf(false) }

    // Единая логика «Назад»: удаляем файл фото (квест не отправлен) и
    // закрываем экран. Во время отправки игнорируем — как disabled-кнопка.
    val handleBack = {
        if (!isSubmitting) {
            capturedPhotoFile?.delete()
            capturedPhotoFile = null
            onBackClick()
        }
    }
    // Системный «Назад» = стрелка «Назад»: без BackHandler он уводил бы
    // на предыдущий destination, а delivery-экран «воскресал» при возврате.
    BackHandler { handleBack() }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            try {
                // Process death при долгой работе камеры мог сбросить
                // rememberSaveable-строки — восстанавливаем последний
                // файл из quest_photos, чтобы фото не «слетало».
                val photosDir = java.io.File(context.filesDir, "quest_photos")
                val resolvedFileName = tempPhotoName
                    ?: photosDir.listFiles()
                        ?.filter { it.extension.equals("jpg", ignoreCase = true) }
                        ?.maxByOrNull { it.lastModified() }
                        ?.name
                val resolvedUriString = tempCameraUriString
                    ?: resolvedFileName?.let { name ->
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            java.io.File(photosDir, name)
                        ).toString()
                    }
                if (resolvedUriString != null && resolvedFileName != null) {
                    // Файл уже лежит в filesDir/quest_photos — камера писала
                    // сразу в постоянное хранилище. Здесь только даунскейл
                    // до 1080px JPEG перезаписью того же файла.
                    val photoFile = java.io.File(photosDir, resolvedFileName)
                    if (!photoFile.exists()) return@rememberLauncherForActivityResult
                    val uri = Uri.parse(resolvedUriString)

                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, options)
                    }

                    val maxDimension = 1080
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                    inputStream?.close()

                    if (bitmap != null) {
                        photoFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        bitmap.recycle()
                    }
                    // bitmap == null — оставляем оригинал камеры как есть.

                    capturedImageUriString = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    ).toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.delivery_error_photo, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val launchCamera = {
        try {
            // Целевой файл создаётся в filesDir/quest_photos ДО вызова камеры:
            // системная камера пишет сразу в постоянное хранилище, и фото
            // не зависит от очистки cacheDir или смерти процесса.
            val photosDir = java.io.File(context.filesDir, "quest_photos").apply { mkdirs() }
            // Пересъёмка: старый файл больше не нужен — удаляем.
            capturedPhotoFile?.delete()
            val fileName = "quest_${System.currentTimeMillis()}.jpg"
            val photoFile = java.io.File(photosDir, fileName)
            capturedPhotoFile = photoFile
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUriString = uri.toString()
            tempPhotoName = fileName
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.delivery_error_camera, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, context.getString(R.string.delivery_camera_permission), Toast.LENGTH_SHORT).show()
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

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .imePadding()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Шапка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = handleBack,
                enabled = !isSubmitting,
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
        val cameraDesc = stringResource(R.string.a11y_camera_hint)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                .semantics { contentDescription = cameraDesc }
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
                        contentDescription = stringResource(R.string.delivery_photo_desc),
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
                            text = stringResource(R.string.delivery_camera_hint),
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
                Text(stringResource(R.string.delivery_retake))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода комментария
        OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = {
                Text(
                    stringResource(R.string.delivery_comment_hint),
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
            onClick = {
                isSubmitting = true
                onSendClick(commentText, capturedImageUri)
            },
            enabled = capturedImageUri != null && !isSubmitting,
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
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = SpaceBgStart,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.delivery_submit),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Полноэкранный оверлей загрузки: блокирует все касания до завершения submit
    if (isSubmitting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpaceBgStart.copy(alpha = 0.9f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentColor)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.delivery_uploading),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    }
}
