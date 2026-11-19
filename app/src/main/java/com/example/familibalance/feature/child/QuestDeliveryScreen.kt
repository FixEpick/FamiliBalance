package com.example.familibalance.feature.child

import android.annotation.SuppressLint
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
import com.example.familibalance.data.push.rememberPushPermissionRequester
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.components.EmojiIcon
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.feature.child.components.CameraCaptureScreen

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
    // Встроенная камера CameraX: процесс не уходит в фон,
    // поэтому OS не убивает приложение при съёмке фото.
    var showCamera by remember { mutableStateOf(false) }
    // Блокирует «Назад»/повторную отправку, пока submitQuestForReview не вернётся:
    // иначе экран закрывался до завершения persist+push и пользователь видел
    // «выкидывание» на главный экран без подтверждения.
    var isSubmitting by remember { mutableStateOf(false) }
    // Разрешение на уведомления просим в момент первой отправки квеста —
    // ребёнок уже понял, зачем они нужны («узнать о проверке родителем»).
    val requestNotifPermission = rememberPushPermissionRequester()

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
    BackHandler { if (showCamera) showCamera = false else handleBack() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCamera = true
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
            showCamera = true
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
                    GameIcon(
                        icon = GameIconType.ArrowBack,
                        size = 20.dp,
                        tint = TextPrimary
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
                        EmojiIcon(emoji = "📷", size = 54.dp)
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
                requestNotifPermission()
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

    // Встроенная камера CameraX — полноэкранный оверлей внутри нашего
    // процесса: OS не уводит приложение в фон и не убивает его.
    if (showCamera) {
        CameraCaptureScreen(
            onImageCaptured = { file ->
                // Пересъёмка: предыдущий файл больше не нужен.
                capturedPhotoFile?.delete()
                capturedPhotoFile = file
                capturedImageUriString = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                ).toString()
                showCamera = false
            },
            onClose = { showCamera = false }
        )
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
