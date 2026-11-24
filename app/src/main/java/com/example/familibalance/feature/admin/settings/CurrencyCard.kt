package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.RewardCurrency
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.GlassCardShape
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.ui.theme.glassCard
import com.example.familibalance.util.currencyName

/** Карточка выбора валюты наград: сворачиваемый список всех поддерживаемых валют. */
@Composable
fun CurrencyCard(
    currentCurrency: String,
    onSelect: (code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = GlassCardShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCurrency = !showCurrency }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.admin_set_currency),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = RewardCurrency.symbolOf(currentCurrency),
                    color = AccentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showCurrency) "▲" else "▼",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            if (showCurrency) {
                RewardCurrency.all.forEach { cur ->
                    val isSelected = cur.code == currentCurrency
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(cur.code)
                                showCurrency = false
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cur.symbol, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = cur.code,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currencyName(cur.code),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text("✓", color = AccentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.admin_set_currency_note),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }
    }
}
