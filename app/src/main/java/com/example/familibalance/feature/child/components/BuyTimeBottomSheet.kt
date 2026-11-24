package com.example.familibalance.feature.child.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyTimeBottomSheet(
    currentBalance: Int,
    boughtMinutesToday: Int,
    dailyLimit: Int,
    isProcessing: Boolean = false,
    onBuyTime: (cost: Int, minutes: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val minutesLeftToday = (dailyLimit - boughtMinutesToday).coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = GlassStroke
            )
        }
    ) {
        AppLocaleContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                CloudBadge(type = CloudIconType.SCREEN_TIME, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.child_buytime_title),
                    color = AccentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.child_buytime_limit, dailyLimit, minutesLeftToday),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            val tariffs = listOf(
                TariffData(pluralStringResource(R.plurals.child_tariff_minutes, 30, 30), 100, 30),
                TariffData(pluralStringResource(R.plurals.child_tariff_minutes, 60, 60), 200, 60),
                TariffData(pluralStringResource(R.plurals.child_tariff_minutes, 90, 90), 300, 90)
            )

            tariffs.forEach { tariff ->
                val isAffordable = !isProcessing && (currentBalance >= tariff.cost) && (boughtMinutesToday + tariff.minutes <= dailyLimit)
                Card(
                    onClick = {
                        if (isAffordable) {
                            onBuyTime(tariff.cost, tariff.minutes)
                        }
                    },
                    enabled = isAffordable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .glassCard()
                        .bounceClick(),
                    shape = GlassCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAffordable) GlassBackground else GlassBackground.copy(alpha = 0.15f),
                        disabledContainerColor = GlassBackground.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tariff.title,
                            color = if (isAffordable) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (isAffordable) AccentColor.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isAffordable) AccentColor else TextSecondary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${tariff.cost} ${rewardCurrencySymbol()}",
                                color = if (isAffordable) AccentColor else TextSecondary.copy(alpha = 0.4f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

private data class TariffData(
    val title: String,
    val cost: Int,
    val minutes: Int
)
