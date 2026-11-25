package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.ParentGiftItem
import com.example.familibalance.data.model.parentGiftTypeOfOrNull
import com.example.familibalance.ui.theme.*

/**
 * Тёплый баннер «Подарки от ребёнка» на главном экране админки:
 * список купонов, отправленных ребёнком за монеты, с кнопкой
 * «Принять / Использовать» (удаляет купон из pendingParentGifts).
 */
@Composable
fun ParentGiftsCard(
    gifts: List<ParentGiftItem>,
    actionEnabled: Boolean,
    onUseGift: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(),
        shape = GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground),
        border = BorderStroke(1.dp, StatusYellow.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.admin_gifts_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            gifts.forEach { gift ->
                val type = parentGiftTypeOfOrNull(gift.giftTypeId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = type?.let { stringResource(it.titleRes) }
                                ?: gift.giftTypeId,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (gift.createdAt.isNotBlank()) {
                            Text(
                                text = gift.createdAt,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onUseGift(gift.id) },
                        enabled = actionEnabled,
                        border = BorderStroke(1.dp, StatusGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                        shape = AccentButtonShape,
                        modifier = Modifier.bounceClick(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.admin_gift_use),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
