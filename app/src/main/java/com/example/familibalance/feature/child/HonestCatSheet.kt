package com.example.familibalance.feature.child

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*

/** Шторка «Честного признания»: кошечка, поле текста и кнопка отправки папе. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HonestCatSheet(
    admissionText: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.honest_title),
                    color = AccentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Стеклянная зона с кошечкой
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassStroke, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🐱",
                                fontSize = 48.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = AccentColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AccentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.honest_bonus),
                                color = AccentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.honest_text),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Поле ввода — в стиле диалогов «Вывести наличные» / «Цель-мечта»
                OutlinedTextField(
                    value = admissionText,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.honest_input_label)) },
                    placeholder = { Text(stringResource(R.string.honest_placeholder), color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LocalAppPalette.current.primaryNeon,
                        focusedBorderColor = LocalAppPalette.current.primaryNeon,
                        unfocusedBorderColor = GlassStroke,
                        focusedContainerColor = CardBgDark.copy(alpha = 0.8f),
                        unfocusedContainerColor = CardBgDark.copy(alpha = 0.8f)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Нижняя бирюзовая кнопка
                Button(
                    onClick = onSendClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = AccentOnColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = stringResource(R.string.honest_submit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
