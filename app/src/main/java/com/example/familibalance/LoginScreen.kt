package com.example.familibalance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ui.theme.*

enum class AppRole {
    CHILD, DAD
}

@Composable
fun LoginScreen(
    onSelectRole: (AppRole, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.space_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AdminOverlayBg.copy(alpha = 0.25f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = NeonCyan.copy(alpha = 0.2f),
                border = BorderStroke(2.dp, NeonCyan),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🚀", fontSize = 40.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Семейный Баланс",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Выбери профиль для входа",
                color = TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            ProfileLoginCard(
                avatar = "🐰",
                accentColor = NeonCyan,
                title = "Пользователь 1",
                subtitle = "Выполняй задания, копи рубли и прокачивай ранги",
                onClick = { onSelectRole(AppRole.CHILD, "user_1") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileLoginCard(
                avatar = "🦊",
                accentColor = StatusYellow,
                title = "Пользователь 2",
                subtitle = "Выполняй задания, копи рубли и прокачивай ранги",
                onClick = { onSelectRole(AppRole.CHILD, "user_2") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileLoginCard(
                avatar = "👑",
                accentColor = StatusYellow,
                title = "Папа (Админ)",
                subtitle = "Проверка фотоотчётов, дневника и управление балансом",
                onClick = { onSelectRole(AppRole.DAD, "") }
            )
        }
    }
}

@Composable
private fun ProfileLoginCard(
    avatar: String,
    accentColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, accentColor),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(avatar, fontSize = 26.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
