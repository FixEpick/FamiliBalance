package com.example.familibalance.feature.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.ui.theme.*

sealed class AdminScreenType(@StringRes val titleRes: Int, @StringRes val a11yRes: Int, val icon: String) {
    object Verification : AdminScreenType(R.string.nav_verify, R.string.a11y_nav_verify, "🛡️")
    object Progress : AdminScreenType(R.string.nav_progress, R.string.a11y_nav_progress, "📊")
    object History : AdminScreenType(R.string.nav_history, R.string.a11y_nav_history, "📜")
    object Settings : AdminScreenType(R.string.nav_settings, R.string.a11y_nav_settings, "⚙️")
}

data class AdminTab(
    val tab: AdminScreenType,
    val route: Any
)

/** Верхняя панель админки: заголовок, переключатель языка/профиля, выбор ребёнка. */
@Composable
fun AdminTopBar(
    onSwitchProfile: () -> Unit,
    visibleChildren: List<ChildProfileState>,
    selectedChildId: String,
    onSelectChild: (String) -> Unit
) {
    val parentPanelDesc = stringResource(R.string.a11y_parent_panel)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = StatusYellow.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, StatusYellow),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "👑",
                            fontSize = 20.sp,
                            modifier = Modifier.semantics {
                                contentDescription = parentPanelDesc
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.admin_panel_title), color = TextSecondary, fontSize = 11.sp)
                    Text(stringResource(R.string.admin_role), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguageSelectorButton()
                OutlinedButton(
                    onClick = onSwitchProfile,
                    border = BorderStroke(1.dp, GlassStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.admin_switch), fontSize = 12.sp)
                }
            }
        }

        // Переключатель пользователей
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleChildren.forEach { profile ->
                val isSelected = selectedChildId == profile.id
                Surface(
                    onClick = { onSelectChild(profile.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) AccentColor.copy(alpha = 0.25f) else GlassBackground,
                    border = BorderStroke(1.dp, if (isSelected) AccentColor else GlassStroke),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${profile.avatar} ${profile.name}",
                            color = if (isSelected) AccentColor else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/** Нижняя навигация админки по вкладкам. */
@Composable
fun AdminBottomBar(
    tabs: List<AdminTab>,
    selectedTabRoute: Any,
    onTabClick: (Any) -> Unit
) {
    NavigationBar(
        containerColor = GlassBackground,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { adminTab ->
            NavigationBarItem(
                selected = adminTab.route == selectedTabRoute,
                onClick = { onTabClick(adminTab.route) },
                icon = {
                    val tabA11y = stringResource(adminTab.tab.a11yRes)
                    Text(
                        adminTab.tab.icon,
                        fontSize = 20.sp,
                        modifier = Modifier.semantics {
                            contentDescription = tabA11y
                        }
                    )
                },
                label = { Text(stringResource(adminTab.tab.titleRes), color = if (adminTab.route == selectedTabRoute) AccentColor else TextSecondary) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = GlassStroke)
            )
        }
    }
}
