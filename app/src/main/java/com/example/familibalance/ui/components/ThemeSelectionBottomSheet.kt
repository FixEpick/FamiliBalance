package com.example.familibalance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*

/** Локализованное отображаемое имя темы по её id. */
@Composable
private fun themeDisplayName(theme: AppThemePalette): String = when (theme.id) {
    "COSMIC_BLUE" -> stringResource(R.string.theme_name_cosmic_blue)
    "CYBER_PINK" -> stringResource(R.string.theme_name_cyber_pink)
    "TOXIC_GREEN" -> stringResource(R.string.theme_name_toxic_green)
    "SOLAR_GOLD" -> stringResource(R.string.theme_name_solar_gold)
    "INFERNO_RED" -> stringResource(R.string.theme_name_inferno_red)
    "GALAXY_PURPLE" -> stringResource(R.string.theme_name_galaxy_purple)
    "ARCTIC_ICE" -> stringResource(R.string.theme_name_arctic_ice)
    "SUNSET_ORANGE" -> stringResource(R.string.theme_name_sunset_orange)
    else -> theme.id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionBottomSheet(
    currentThemeId: String,
    onThemeSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = GlassStroke)
        }
    ) {
        AppLocaleContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_shop_title),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.theme_shop_subtitle),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 64.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(AppThemes, key = { it.id }) { theme ->
                    val isSelected = theme.id == currentThemeId
                    Card(
                        onClick = {
                            onThemeSelected(theme.id)
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) theme.primaryNeon else GlassStroke,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                theme.primaryNeon.copy(alpha = 0.12f)
                            else GlassBackground.copy(alpha = 1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Мини-макет карточки: строка баланса + залитая акцентная кнопка
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpaceBgStart)
                                    .border(1.dp, GlassStroke, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .width(30.dp)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(TextPrimary.copy(alpha = 0.75f))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(11.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(theme.primaryNeon)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(theme.secondaryNeon.copy(alpha = 0.8f))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = themeDisplayName(theme),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                minLines = 2,
                                maxLines = 2
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.theme_selected),
                                    color = theme.primaryNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = SpaceBgStart
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.theme_done), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        }
    }
}
