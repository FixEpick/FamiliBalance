package com.example.familibalance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ui.theme.*

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "🎨 Магазин тем",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Выбери палитру, которая нравится больше всего",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.heightIn(max = 420.dp)
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
                            else GlassBackground
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Превью: два цветных кружка палитры
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(theme.primaryNeon)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(theme.secondaryNeon)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = theme.name,
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
                                    text = "✓ Выбрана",
                                    color = theme.primaryNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
