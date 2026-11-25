package com.example.familibalance.feature.child.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.ParentGiftType
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.theme.*
import com.example.familibalance.util.rewardCurrencySymbol

/**
 * Компактная карточка «Подарок родителю»: 4 купона-открытки
 * в стиле «Мягкое стекло». Клик по доступному подарку открывает
 * диалог подтверждения; списание монет — в ViewModel после «Подарить».
 */
@Composable
fun ParentGiftCard(
    balance: Int,
    onSendGift: (ParentGiftType) -> Unit,
    onInsufficientFunds: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = rewardCurrencySymbol()
    var pendingGift by remember { mutableStateOf<ParentGiftType?>(null) }
    Surface(
        shape = GlassCardShape,
        color = GlassBackground,
        border = BorderStroke(1.dp, GlassStroke),
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.child_parent_gift_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.child_parent_gift_hint),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            ParentGiftType.entries.chunked(2).forEach { rowGifts ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowGifts.forEach { gift ->
                        val affordable = balance >= gift.price
                        OutlinedButton(
                            onClick = {
                                if (affordable) {
                                    pendingGift = gift
                                } else {
                                    onInsufficientFunds()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .bounceClick(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (affordable) AccentColor.copy(alpha = 0.6f) else GlassStroke
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (affordable) {
                                    AccentColor.copy(alpha = 0.10f)
                                } else {
                                    GlassBackground.copy(alpha = 0.4f)
                                },
                                contentColor = if (affordable) TextPrimary else TextSecondary,
                                disabledContentColor = TextSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = giftEmoji(gift),
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = stringResource(gift.titleRes),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp
                                )
                                Text(
                                    text = "-${gift.price} $currency",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (affordable) AccentColor else TextSecondary
                                )
                            }
                        }
                    }
                    // Нечётный ряд — пустой вес для выравнивания.
                    if (rowGifts.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Диалог подтверждения покупки подарка.
    pendingGift?.let { gift ->
        AppDialog(
            onDismissRequest = { pendingGift = null },
            title = {
                Text(
                    text = stringResource(R.string.child_parent_gift_title),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.child_parent_gift_confirm_text,
                        stringResource(gift.titleRes),
                        gift.price,
                        currency
                    ),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSendGift(gift)
                        pendingGift = null
                    }
                ) {
                    Text(stringResource(R.string.child_parent_gift_confirm_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingGift = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun giftEmoji(gift: ParentGiftType): String = when (gift) {
    ParentGiftType.THANK_YOU -> "💌"
    ParentGiftType.HUGS -> "🤗"
    ParentGiftType.HELP_CHORES -> "🧹"
    ParentGiftType.MOVIE_NIGHT -> "🎬"
}
