package com.example.familibalance.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.ExitConfirmDialog
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.GlassStroke
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class OnboardingRole { Parent, Child }

private data class OnboardingSlide(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: GameIconType,
    val accent: Color
)

private val parentSlides = listOf(
    OnboardingSlide(R.string.onboarding_p_welcome_title, R.string.onboarding_p_welcome_desc, GameIconType.Rocket, Color(0xFF5CE1E6)),
    OnboardingSlide(R.string.onboarding_p_quests_achievements_title, R.string.onboarding_p_quests_achievements_desc, GameIconType.Trophy, Color(0xFFFF5252)),
    OnboardingSlide(R.string.onboarding_p_coins_rewards_title, R.string.onboarding_p_coins_rewards_desc, GameIconType.Coin, Color(0xFFFFD700)),
    OnboardingSlide(R.string.onboarding_p_diary_screentime_title, R.string.onboarding_p_diary_screentime_desc, GameIconType.Book, Color(0xFFB79CFF))
)

private val childSlides = listOf(
    OnboardingSlide(R.string.onboarding_c_missions_title, R.string.onboarding_c_missions_desc, GameIconType.Rocket, Color(0xFF5CE1E6)),
    OnboardingSlide(R.string.onboarding_c_coins_title, R.string.onboarding_c_coins_desc, GameIconType.Coin, Color(0xFFFFD700)),
    OnboardingSlide(R.string.onboarding_c_diary_time_title, R.string.onboarding_c_diary_time_desc, GameIconType.Book, Color(0xFFB79CFF)),
    OnboardingSlide(R.string.onboarding_c_rewards_title, R.string.onboarding_c_rewards_desc, GameIconType.Gift, Color(0xFFFF8C00))
)

/**
 * Онбординг: сначала выбор роли («Кто за пультом?»), затем слайды
 * возможностей под выбранную роль и финальная кнопка «Начать».
 * Завершение обрабатывает вызывающий код (MainActivity).
 */
@Composable
fun OnboardingScreen(
    onParentClick: () -> Unit,
    onChildClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var role by remember { mutableStateOf<OnboardingRole?>(null) }
    val activity = LocalActivity.current
    var showExitDialog by remember { mutableStateOf(false) }

    // Системная «Назад» на экране выбора роли — диалог выхода.
    // На слайдах обработкой занимается SlidePager (шаг назад по страницам).
    BackHandler(enabled = role == null) { showExitDialog = true }

    Box(modifier = modifier.fillMaxSize()) {
        CyberBackground()

        when (role) {
            null -> RoleSelectPage(
                onParentClick = { role = OnboardingRole.Parent },
                onChildClick = { role = OnboardingRole.Child }
            )
            else -> {
                val slides = if (role == OnboardingRole.Parent) parentSlides else childSlides
                val onFinish = if (role == OnboardingRole.Parent) onParentClick else onChildClick
                SlidePager(
                    slides = slides,
                    onFinish = onFinish,
                    onFirstPageBack = { role = null }
                )
            }
        }

        if (showExitDialog) {
            ExitConfirmDialog(
                title = stringResource(R.string.exit_dialog_title),
                message = stringResource(R.string.exit_dialog_message),
                confirmText = stringResource(R.string.exit_dialog_confirm),
                cancelText = stringResource(R.string.action_cancel),
                onConfirm = {
                    showExitDialog = false
                    activity?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
                onDismiss = { showExitDialog = false }
            )
        }
    }
}

@Composable
private fun RoleSelectPage(
    onParentClick: () -> Unit,
    onChildClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .shadow(24.dp, CircleShape, ambientColor = AccentColor, spotColor = AccentColor)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentColor.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .border(2.dp, AccentColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GameIcon(icon = GameIconType.Family, tint = AccentColor, size = 64.dp)
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.onboarding_role_title),
            color = AccentColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.onboarding_role_desc),
            color = TextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(36.dp))
        GameButton(
            text = stringResource(R.string.onboarding_role_parent),
            icon = GameIconType.Family,
            variant = GameButtonVariant.Electric,
            onClick = onParentClick,
            modifier = Modifier.shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0xFF3B82F6),
                spotColor = Color(0xFF3B82F6)
            )
        )
        Spacer(modifier = Modifier.height(14.dp))
        GameButton(
            text = stringResource(R.string.onboarding_role_child),
            icon = GameIconType.Gamepad,
            variant = GameButtonVariant.Lime,
            onClick = onChildClick,
            modifier = Modifier.shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0xFFA3E635),
                spotColor = Color(0xFFA3E635)
            )
        )
    }

        LanguageSelectorButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 16.dp)
        )
    }
}

@Composable
private fun SlidePager(
    slides: List<OnboardingSlide>,
    onFinish: () -> Unit,
    onFirstPageBack: (() -> Unit)? = null
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    var showExitDialog by remember { mutableStateOf(false) }
    val isLastPage = pagerState.currentPage == slides.lastIndex

    // Системная «Назад»: листаем слайды назад, на первом — выход к выбору роли
    // (или диалог выхода, если callback не задан).
    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else if (onFirstPageBack != null) {
            onFirstPageBack()
        } else {
            showExitDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ProgressBar(
                    pageCount = slides.size,
                    currentPage = pagerState.currentPage,
                    pageOffsetFraction = pagerState.currentPageOffsetFraction
                )
            }
            TextButton(onClick = onFinish) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)
            val alpha = 1f - pageOffset
            val scale = 1f - pageOffset * 0.12f

            SlidePage(
                slide = slides[page],
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, GlassStroke, RoundedCornerShape(10.dp))
                    .clickable {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        } else if (onFirstPageBack != null) {
                            onFirstPageBack()
                        } else {
                            showExitDialog = true
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_back),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            GameButton(
                text = stringResource(
                    if (isLastPage) R.string.onboarding_start else R.string.onboarding_next
                ),
                icon = if (isLastPage) GameIconType.Rocket else GameIconType.ArrowForward,
                variant = GameButtonVariant.Lime,
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                height = 52.dp
            )
        }
    }

        if (showExitDialog) {
            ExitConfirmDialog(
                title = stringResource(R.string.exit_dialog_title),
                message = stringResource(R.string.exit_dialog_message),
                confirmText = stringResource(R.string.exit_dialog_confirm),
                cancelText = stringResource(R.string.action_cancel),
                onConfirm = {
                    showExitDialog = false
                    activity?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
                onDismiss = { showExitDialog = false }
            )
        }
    }
}

@Composable
private fun ProgressBar(pageCount: Int, currentPage: Int, pageOffsetFraction: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(pageCount) { index ->
            val progress = when {
                index < currentPage -> 1f
                index == currentPage -> 1f - pageOffsetFraction.coerceIn(0f, 1f)
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(GlassStroke)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AccentColor)
                )
            }
        }
    }
}

@Composable
private fun SlidePage(modifier: Modifier = Modifier, slide: OnboardingSlide) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .shadow(24.dp, CircleShape, ambientColor = slide.accent, spotColor = slide.accent)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            slide.accent.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
                .border(2.dp, slide.accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GameIcon(
                icon = slide.icon,
                tint = slide.accent,
                size = 64.dp,
                modifier = Modifier.offset { IntOffset(0, floatOffset.dp.roundToPx()) }
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(slide.titleRes),
            color = slide.accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(slide.descRes),
            color = TextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
