package com.example.familibalance.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.FeedbackManager
import com.example.familibalance.util.currencyString
import com.example.familibalance.ui.theme.*

/** Вкладки единой шторки «Магазин стиля». */
enum class StyleShopTab { THEMES, AVATARS, FRAMES }

/**
 * Шторка «Магазин стиля» с двумя вкладками: «Темы» и «Аватары».
 * Темы разбиты на тёмные/светлые секции. Бесплатные применяются
 * кликом, платные и темы за достижения — через live-preview.
 * Аватары: открытые применяются кликом, платные — через диалог
 * покупки, за достижения — подсказка-условие.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionBottomSheet(
    currentThemeId: String,
    unlockedThemes: List<String> = DefaultUnlockedThemeIds,
    claimedAchievementTitles: Set<String> = emptySet(),
    balance: Int = 0,
    previewThemeId: String? = null,
    onPreviewTheme: (String?) -> Unit = {},
    onThemeSelected: (String) -> Unit,
    onPurchaseTheme: (String) -> Boolean = { false },
    currentAvatar: String? = null,
    unlockedAvatars: List<String> = DefaultUnlockedAvatarIds,
    initialTab: StyleShopTab = StyleShopTab.THEMES,
    onAvatarSelected: (String) -> Unit = {},
    onPurchaseAvatar: (String) -> Boolean = { false },
    selectedFrameId: String = "NONE",
    unlockedFrames: List<String> = DefaultUnlockedFrameIds,
    childLevel: Int = 1,
    onFrameSelected: (String) -> Unit = {},
    onPurchaseFrame: (String) -> Boolean = { false },
    onDismissRequest: () -> Unit
) {
    val sheetTitle = stringResource(R.string.style_shop_title)
    val doneLabel = stringResource(R.string.theme_done)
    val darkLabel = stringResource(R.string.theme_kind_dark)
    val lightLabel = stringResource(R.string.theme_kind_light)
    val lockedLabel = stringResource(R.string.theme_locked)
    val themesTabLabel = stringResource(R.string.style_tab_themes)
    val avatarsTabLabel = stringResource(R.string.style_tab_avatars)
    val framesTabLabel = stringResource(R.string.style_tab_frames)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var confettiTrigger by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(initialTab) }
    var avatarPurchaseTarget by remember { mutableStateOf<CloudAvatarType?>(null) }
    var framePurchaseTarget by remember { mutableStateOf<AvatarFrameType?>(null) }

    val darkThemes = AllAppThemes.filter { it.isDark }
    val lightThemes = AllAppThemes.filter { !it.isDark }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CloudStyleIcon(type = CloudIconType.SHOP_THEMES, size = 30.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sheetTitle,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Переключатель вкладок «Темы / Аватары».
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassBackground)
                        .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StyleShopTabButton(
                        label = themesTabLabel,
                        selected = tab == StyleShopTab.THEMES,
                        modifier = Modifier.weight(1f),
                        onClick = { tab = StyleShopTab.THEMES }
                    )
                    StyleShopTabButton(
                        label = avatarsTabLabel,
                        selected = tab == StyleShopTab.AVATARS,
                        modifier = Modifier.weight(1f),
                        onClick = { tab = StyleShopTab.AVATARS }
                    )
                    StyleShopTabButton(
                        label = framesTabLabel,
                        selected = tab == StyleShopTab.FRAMES,
                        modifier = Modifier.weight(1f),
                        onClick = { tab = StyleShopTab.FRAMES }
                    )
                }

                if (tab == StyleShopTab.FRAMES) {
                    FrameShopContent(
                        currentAvatar = currentAvatar,
                        selectedFrameId = selectedFrameId,
                        unlockedFrames = unlockedFrames,
                        childLevel = childLevel,
                        balance = balance,
                        onFrameSelected = onFrameSelected,
                        onPurchaseRequest = { framePurchaseTarget = it },
                        onDone = onDismissRequest
                    )
                } else if (tab == StyleShopTab.AVATARS) {
                    AvatarShopContent(
                        currentAvatar = currentAvatar,
                        currentFrameId = selectedFrameId,
                        unlockedAvatars = unlockedAvatars,
                        claimedAchievementTitles = claimedAchievementTitles,
                        balance = balance,
                        onAvatarSelected = onAvatarSelected,
                        onPurchaseRequest = { avatarPurchaseTarget = it },
                        onDone = onDismissRequest
                    )
                } else {
                Text(
                    text = stringResource(R.string.theme_shop_subtitle),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    item(span = { GridItemSpan(2) }) {
                        ThemeSectionHeader(stringResource(R.string.theme_section_dark))
                    }
                    items(darkThemes, key = { it.id }) { theme ->
                        val unlocked = theme.isThemeUnlocked(unlockedThemes, claimedAchievementTitles)
                        ThemeShopCard(
                            theme = theme,
                            isSelected = theme.id == currentThemeId,
                            isUnlocked = unlocked,
                            isPreview = theme.id == previewThemeId,
                            kindLabel = darkLabel,
                            lockedLabel = lockedLabel,
                            onClick = {
                                if (unlocked) {
                                    FeedbackManager.playLaser(context, haptic)
                                    onPreviewTheme(null)
                                    onThemeSelected(theme.id)
                                } else {
                                    // Live-preview закрытой темы: фон и
                                    // карточки перекрашиваются в реальном времени.
                                    FeedbackManager.playLaser(context, haptic)
                                    onPreviewTheme(theme.id)
                                }
                            }
                        )
                    }
                    item(span = { GridItemSpan(2) }) {
                        ThemeSectionHeader(stringResource(R.string.theme_section_light))
                    }
                    items(lightThemes, key = { it.id }) { theme ->
                        val unlocked = theme.isThemeUnlocked(unlockedThemes, claimedAchievementTitles)
                        ThemeShopCard(
                            theme = theme,
                            isSelected = theme.id == currentThemeId,
                            isUnlocked = unlocked,
                            isPreview = theme.id == previewThemeId,
                            kindLabel = lightLabel,
                            lockedLabel = lockedLabel,
                            onClick = {
                                if (unlocked) {
                                    FeedbackManager.playLaser(context, haptic)
                                    onPreviewTheme(null)
                                    onThemeSelected(theme.id)
                                } else {
                                    FeedbackManager.playLaser(context, haptic)
                                    onPreviewTheme(theme.id)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val previewTheme = AllAppThemes.firstOrNull { it.id == previewThemeId }
                if (previewTheme != null) {
                    // Панель действий для предпросматриваемой закрытой темы.
                    when (val rule = previewTheme.unlockRule) {
                        is ThemeUnlockRule.Paid -> {
                            val canAfford = balance >= rule.price
                            val priceLabel = currencyString(R.string.theme_price, rule.price)
                            Button(
                                onClick = {
                                    if (onPurchaseTheme(previewTheme.id)) {
                                        FeedbackManager.playCoin(context, haptic)
                                        confettiTrigger++
                                        onPreviewTheme(null)
                                    }
                                },
                                enabled = canAfford,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .accentButtonChrome()
                                    .bounceClick(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = AccentOnColor,
                                    disabledContainerColor = Color.Transparent,
                                    disabledContentColor = TextSecondary
                                ),
                                shape = AccentButtonShape
                            ) {
                                Text(
                                    text = if (canAfford)
                                        stringResource(R.string.theme_buy_for, priceLabel)
                                    else
                                        stringResource(R.string.theme_buy_no_funds),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        is ThemeUnlockRule.Achievement -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GlassBackground)
                                    .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏆", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(
                                        R.string.theme_unlocks_for,
                                        stringResource(rule.titleRes)
                                    ),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        ThemeUnlockRule.Free, is ThemeUnlockRule.Level -> Unit
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onPreviewTheme(null) }) {
                            Text(
                                text = stringResource(R.string.theme_return_mine),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = onDismissRequest) {
                            EmojiIcon(
                                emoji = "👁",
                                size = 20.dp,
                                contentDescription = stringResource(R.string.theme_inspect)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentColor,
                            contentColor = AccentOnColor
                        ),
                        shape = AccentButtonShape
                    ) {
                        Text(doneLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                }
            }
            // Диалог подтверждения покупки аватара.
            avatarPurchaseTarget?.let { target ->
                val rule = target.unlockRule as? ThemeUnlockRule.Paid
                val price = rule?.price ?: 0
                val canAfford = balance >= price
                AppDialog(
                    onDismissRequest = { avatarPurchaseTarget = null },
                    title = {
                        Text(
                            text = stringResource(R.string.avatar_buy_title),
                            color = AccentColor,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = stringResource(
                                    R.string.avatar_buy_message,
                                    stringResource(target.titleRes),
                                    price
                                ),
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            if (!canAfford) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.theme_buy_no_funds),
                                    color = PenaltyRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = canAfford,
                            onClick = {
                                if (onPurchaseAvatar(target.name)) {
                                    FeedbackManager.playCoin(context, haptic)
                                    confettiTrigger++
                                }
                                avatarPurchaseTarget = null
                            },
                            modifier = Modifier.alpha(if (canAfford) 1f else 0.38f)
                        ) {
                            Text(
                                text = stringResource(R.string.theme_buy_confirm),
                                color = if (canAfford) AccentColor else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { avatarPurchaseTarget = null }) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = TextSecondary
                            )
                        }
                    }
                )
            }
            // Диалог подтверждения покупки рамки аватара.
            framePurchaseTarget?.let { target ->
                val rule = target.unlockRule as? ThemeUnlockRule.Paid
                val price = rule?.price ?: 0
                val canAfford = balance >= price
                AppDialog(
                    onDismissRequest = { framePurchaseTarget = null },
                    title = {
                        Text(
                            text = stringResource(R.string.frame_buy_title),
                            color = AccentColor,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = stringResource(
                                    R.string.frame_buy_message,
                                    stringResource(target.titleRes),
                                    price
                                ),
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            if (!canAfford) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.theme_buy_no_funds),
                                    color = PenaltyRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = canAfford,
                            onClick = {
                                if (onPurchaseFrame(target.name)) {
                                    FeedbackManager.playCoin(context, haptic)
                                    confettiTrigger++
                                }
                                framePurchaseTarget = null
                            },
                            modifier = Modifier.alpha(if (canAfford) 1f else 0.38f)
                        ) {
                            Text(
                                text = stringResource(R.string.theme_buy_confirm),
                                color = if (canAfford) AccentColor else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { framePurchaseTarget = null }) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = TextSecondary
                            )
                        }
                    }
                )
            }
            ConfettiOverlay(
                modifier = Modifier.matchParentSize(),
                trigger = confettiTrigger
            )
        }
    }
}

@Composable
private fun ThemeSectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ThemeShopCard(
    theme: AppThemeConfig,
    isSelected: Boolean,
    isUnlocked: Boolean,
    isPreview: Boolean = false,
    kindLabel: String,
    lockedLabel: String,
    onClick: () -> Unit
) {
    val title = if (theme.titleRes != 0) stringResource(theme.titleRes) else theme.title
    val rule = theme.unlockRule
    // Подпись под названием: тип темы / цена / условие достижения.
    val bottomLabel: String = when {
        isUnlocked -> kindLabel
        rule is ThemeUnlockRule.Paid -> currencyString(R.string.theme_price, rule.price)
        rule is ThemeUnlockRule.Achievement ->
            stringResource(R.string.theme_ach_prefix, stringResource(rule.titleRes))
        else -> lockedLabel
    }
    val bottomColor = when {
        isUnlocked -> theme.secondaryText
        rule is ThemeUnlockRule.Paid -> theme.accent
        else -> PenaltyRed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.cardBackground)
            .border(
                width = if (isSelected || isPreview) 2.dp else 1.dp,
                color = when {
                    isSelected -> theme.accent
                    isPreview -> theme.accent.copy(alpha = 0.7f)
                    else -> theme.cardBorder
                },
                shape = RoundedCornerShape(16.dp)
            )
            .bounceClick()
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Мини-превью: градиент фона темы + акцентная «капля».
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .alpha(if (isUnlocked) 1f else 0.45f)
                .background(Brush.verticalGradient(listOf(theme.background, theme.backgroundGradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(theme.accent)
            )
            if (isPreview) {
                // Бейдж «Предпросмотр» на карточке закрытой темы.
                Text(
                    text = stringResource(R.string.theme_preview_badge),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.accent)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(theme.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = theme.onAccentText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!isUnlocked) {
                // Замок для платных, кубок для тем за достижения.
                Text(
                    text = if (rule is ThemeUnlockRule.Achievement) "🏆" else "🔒",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = theme.primaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = bottomLabel,
            color = bottomColor,
            fontSize = 9.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StyleShopTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) AccentColor else Color.Transparent
    val fg = if (selected) AccentOnColor else TextSecondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .bounceClick()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Вкладка «Аватары»: сетка 4×2 + кнопка «Готово». */
@Composable
private fun AvatarShopContent(
    currentAvatar: String?,
    currentFrameId: String,
    unlockedAvatars: List<String>,
    claimedAchievementTitles: Set<String>,
    balance: Int,
    onAvatarSelected: (String) -> Unit,
    onPurchaseRequest: (CloudAvatarType) -> Unit,
    onDone: () -> Unit
) {
    val doneLabel = stringResource(R.string.theme_done)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lockedHint = stringResource(R.string.avatar_locked_hint)

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(top = 10.dp)
            .heightIn(max = 420.dp)
    ) {
        items(CloudAvatarType.entries, key = { it.name }) { type ->
            val unlocked = type.isAvatarUnlocked(unlockedAvatars, claimedAchievementTitles)
            val isSelected = cloudAvatarTypeOf(currentAvatar) == type
            // Toast-подсказка резолвится здесь — onClick не composable-контекст.
            val achHint = (type.unlockRule as? ThemeUnlockRule.Achievement)
                ?.let { stringResource(R.string.avatar_locked_hint_ach, stringResource(it.titleRes)) }
                ?: lockedHint
            AvatarShopCard(
                type = type,
                frameId = currentFrameId,
                isSelected = isSelected,
                isUnlocked = unlocked,
                canAfford = balance >= ((type.unlockRule as? ThemeUnlockRule.Paid)?.price ?: 0),
                onClick = {
                    when {
                        unlocked -> {
                            FeedbackManager.playLaser(context, haptic)
                            onAvatarSelected(type.name)
                        }
                        type.unlockRule is ThemeUnlockRule.Paid -> onPurchaseRequest(type)
                        else -> Toast.makeText(context, achHint, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentColor,
            contentColor = AccentOnColor
        ),
        shape = AccentButtonShape
    ) {
        Text(doneLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun AvatarShopCard(
    type: CloudAvatarType,
    frameId: String,
    isSelected: Boolean,
    isUnlocked: Boolean,
    canAfford: Boolean,
    onClick: () -> Unit
) {
    val title = stringResource(type.titleRes)
    val rule = type.unlockRule
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AccentColor else GlassStroke,
                shape = RoundedCornerShape(16.dp)
            )
            .bounceClick()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            CloudAvatar(avatarId = type.name, size = 52.dp, frameId = frameId)
            if (!isUnlocked) {
                // Затемнение + замок/кубок поверх аватара.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (rule is ThemeUnlockRule.Achievement) "🏆" else "🔒",
                        fontSize = 20.sp
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(StatusGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = when {
                isUnlocked -> stringResource(R.string.avatar_unlocked_label)
                rule is ThemeUnlockRule.Paid -> currencyString(R.string.theme_price, rule.price)
                rule is ThemeUnlockRule.Achievement ->
                    stringResource(R.string.avatar_ach_short)
                else -> ""
            },
            color = when {
                isUnlocked -> StatusGreen
                rule is ThemeUnlockRule.Paid && !canAfford -> PenaltyRed
                else -> AccentColor
            },
            fontSize = 9.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/** Вкладка «Рамки»: крупное превью аватара в рамке + сетка 3×2. */
@Composable
private fun FrameShopContent(
    currentAvatar: String?,
    selectedFrameId: String,
    unlockedFrames: List<String>,
    childLevel: Int,
    balance: Int,
    onFrameSelected: (String) -> Unit,
    onPurchaseRequest: (AvatarFrameType) -> Unit,
    onDone: () -> Unit
) {
    val doneLabel = stringResource(R.string.theme_done)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Рамка, примеряемая в превью (по умолчанию — текущая выбранная).
    var previewFrameId by rememberSaveable { mutableStateOf(selectedFrameId) }

    val previewType = avatarFrameTypeOf(previewFrameId)
    val previewUnlocked = previewType.isFrameUnlocked(unlockedFrames, childLevel)
    val previewSelected = previewFrameId.equals(selectedFrameId, ignoreCase = true)
    val previewPaid = previewType.unlockRule as? ThemeUnlockRule.Paid
    val previewLevel = (previewType.unlockRule as? ThemeUnlockRule.Level)?.minLevel
    val previewCanAfford = balance >= (previewPaid?.price ?: 0)

    Column {
        // ===== Превью аватара в примеряемой рамке =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CloudAvatar(avatarId = currentAvatar, size = 96.dp, frameId = previewFrameId)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(previewType.titleRes),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                previewSelected -> FrameStatusBadge(
                    text = stringResource(R.string.frame_selected),
                    color = StatusGreen
                )
                previewUnlocked -> Button(
                    onClick = {
                        FeedbackManager.playLaser(context, haptic)
                        onFrameSelected(previewType.name)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .accentButtonChrome()
                        .bounceClick(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = AccentOnColor
                    ),
                    shape = AccentButtonShape
                ) {
                    Text(
                        text = stringResource(R.string.frame_apply),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                previewPaid != null -> Button(
                    onClick = { onPurchaseRequest(previewType) },
                    enabled = previewCanAfford,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .alpha(if (previewCanAfford) 1f else 0.38f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = AccentOnColor,
                        disabledContainerColor = AccentColor.copy(alpha = 0.3f),
                        disabledContentColor = AccentOnColor.copy(alpha = 0.6f)
                    ),
                    shape = AccentButtonShape
                ) {
                    Text(
                        text = stringResource(
                            R.string.theme_buy_for,
                            currencyString(R.string.theme_price, previewPaid.price)
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                previewLevel != null -> FrameStatusBadge(
                    text = stringResource(R.string.frame_unlocks_at, previewLevel),
                    color = AccentColor
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 10.dp)
                .heightIn(max = 420.dp)
        ) {
            items(AvatarFrameType.entries, key = { it.name }) { type ->
                val unlocked = type.isFrameUnlocked(unlockedFrames, childLevel)
                val isSelected = selectedFrameId.equals(type.name, ignoreCase = true)
                val isPreviewed = previewFrameId.equals(type.name, ignoreCase = true)
                FrameShopCard(
                    type = type,
                    avatarId = currentAvatar,
                    isSelected = isSelected,
                    isPreviewed = isPreviewed,
                    isUnlocked = unlocked,
                    canAfford = balance >= ((type.unlockRule as? ThemeUnlockRule.Paid)?.price ?: 0),
                    onClick = {
                        // Любая рамка примеряется в превью сверху;
                        // открытая — сразу применяется.
                        previewFrameId = type.name
                        if (unlocked) {
                            FeedbackManager.playLaser(context, haptic)
                            onFrameSelected(type.name)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = AccentOnColor
            ),
            shape = AccentButtonShape
        ) {
            Text(doneLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Компактный бейдж статуса рамки под превью (выбрана / откроется с уровня). */
@Composable
private fun FrameStatusBadge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun FrameShopCard(
    type: AvatarFrameType,
    avatarId: String?,
    isSelected: Boolean,
    isPreviewed: Boolean = false,
    isUnlocked: Boolean,
    canAfford: Boolean,
    onClick: () -> Unit
) {
    val title = stringResource(type.titleRes)
    val rule = type.unlockRule
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(
                width = if (isSelected || isPreviewed) 2.dp else 1.dp,
                color = when {
                    isSelected -> AccentColor
                    isPreviewed -> AccentColor.copy(alpha = 0.6f)
                    else -> GlassStroke
                },
                shape = RoundedCornerShape(16.dp)
            )
            .bounceClick()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            CloudAvatar(avatarId = avatarId, size = 52.dp, frameId = type.name)
            if (!isUnlocked) {
                // Затемнение + замок/звезда поверх превью рамки.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (rule is ThemeUnlockRule.Level) "⭐" else "🔒",
                        fontSize = 20.sp
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(StatusGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = when {
                isSelected -> stringResource(R.string.frame_selected)
                isUnlocked -> stringResource(R.string.frame_unlocked)
                rule is ThemeUnlockRule.Paid -> currencyString(R.string.theme_price, rule.price)
                rule is ThemeUnlockRule.Level ->
                    stringResource(R.string.frame_level_req, rule.minLevel)
                else -> ""
            },
            color = when {
                isSelected || isUnlocked -> StatusGreen
                rule is ThemeUnlockRule.Paid && !canAfford -> PenaltyRed
                else -> AccentColor
            },
            fontSize = 9.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
