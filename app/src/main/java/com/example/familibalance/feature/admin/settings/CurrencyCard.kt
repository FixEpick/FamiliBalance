package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.RewardCurrency
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.util.currencyName

/** Карточка выбора валюты наград: аккордеон со списком валют, свёрнута по умолчанию. */
@Composable
fun CurrencyCard(
    currentCurrency: String,
    onSelect: (code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsAccordionCard(
        title = stringResource(R.string.admin_set_currency),
        modifier = modifier,
        trailing = {
            Text(
                text = RewardCurrency.symbolOf(currentCurrency),
                color = AccentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    ) {
        RewardCurrency.all.forEach { cur ->
            val isSelected = cur.code == currentCurrency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(cur.code) }
                    .padding(vertical = 10.dp),
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
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
