package com.example.familibalance.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Единый набор векторных иконок «КвестЛаб».
 * Заменяет системные эмодзи там, где нужен единый игровой стиль
 * на любых устройствах. Рисуются Material Symbols — единый контурный стиль.
 */
enum class GameIconType(val image: ImageVector) {
    Rocket(Icons.Filled.RocketLaunch),
    List(Icons.AutoMirrored.Filled.List),
    Gamepad(Icons.Filled.SportsEsports),
    Family(Icons.Filled.FamilyRestroom),
    Coin(Icons.Filled.Paid),
    Trophy(Icons.Filled.EmojiEvents),
    Book(Icons.AutoMirrored.Filled.MenuBook),
    Gift(Icons.Filled.CardGiftcard),
    Phone(Icons.Filled.PhoneAndroid),
    Lock(Icons.Filled.Lock),
    Schedule(Icons.Filled.Schedule),
    ArrowBack(Icons.AutoMirrored.Filled.ArrowBack),
    ArrowForward(Icons.AutoMirrored.Filled.ArrowForward),
    Language(Icons.Filled.Language),
    Star(Icons.Filled.Star),
    Hourglass(Icons.Filled.HourglassEmpty),
    Person(Icons.Filled.Person),
    Settings(Icons.Filled.Settings),
    Eye(Icons.Filled.Visibility),
    EyeOff(Icons.Filled.VisibilityOff)
}

@Composable
fun GameIcon(
    icon: GameIconType,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp
) {
    Icon(
        imageVector = icon.image,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}
