package com.example.familibalance.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.GlassStroke
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val emoji: String
)

private val slides = listOf(
    OnboardingSlide(R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc, "🚀"),
    OnboardingSlide(R.string.onboarding_quests_title, R.string.onboarding_quests_desc, "📋"),
    OnboardingSlide(R.string.onboarding_coins_title, R.string.onboarding_coins_desc, "🪙"),
    OnboardingSlide(R.string.onboarding_achievements_title, R.string.onboarding_achievements_desc, "🏆"),
    OnboardingSlide(R.string.onboarding_diary_title, R.string.onboarding_diary_desc, "📓"),
    OnboardingSlide(R.string.onboarding_rewards_title, R.string.onboarding_rewards_desc, "🎁"),
    OnboardingSlide(R.string.onboarding_screentime_title, R.string.onboarding_screentime_desc, "📱")
)

private const val ROLE_PAGE_INDEX = 7

/**
 * Онбординг для нового пользователя: HorizontalPager со слайдами
 * возможностей и финальным выбором роли (родитель / ребёнок).
 * Завершение обрабатывает вызывающий код (MainActivity):
 * он сохраняет флаг onboarding_done и ведёт в нужный flow.
 */
@Composable
fun OnboardingScreen(
    onParentClick: () -> Unit,
    onChildClick: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { slides.size + 1 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == ROLE_PAGE_INDEX

    Box(modifier = modifier.fillMaxSize()) {
        CyberBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 28.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                if (page == ROLE_PAGE_INDEX) {
                    RolePage(
                        onParentClick = onParentClick,
                        onChildClick = onChildClick,
                        onSkip = onSkip
                    )
                } else {
                    SlidePage(slide = slides[page])
                }
            }

            if (isLastPage) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                PageIndicator(
                    pageCount = slides.size + 1,
                    currentPage = pagerState.currentPage
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_back),
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    GameButton(
                        text = stringResource(R.string.onboarding_next),
                        emoji = "➡️",
                        variant = GameButtonVariant.Lime,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )
                }
            }
        }

        LanguageSelectorButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 16.dp)
        )
    }
}

@Composable
private fun SlidePage(slide: OnboardingSlide) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = slide.emoji, fontSize = 72.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(slide.titleRes),
            color = AccentColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(slide.descRes),
            color = TextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun RolePage(
    onParentClick: () -> Unit,
    onChildClick: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "👨‍👩‍👧", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_role_title),
            color = AccentColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_role_desc),
            color = TextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        GameButton(
            text = stringResource(R.string.onboarding_role_parent),
            emoji = "👨‍👩‍👧",
            variant = GameButtonVariant.Electric,
            onClick = onParentClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = stringResource(R.string.onboarding_role_child),
            emoji = "🎮",
            variant = GameButtonVariant.Lime,
            onClick = onChildClick
        )
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (isSelected) 20.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentColor else GlassStroke)
            )
        }
    }
}