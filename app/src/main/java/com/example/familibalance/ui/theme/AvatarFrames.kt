package com.example.familibalance.ui.theme

import androidx.annotation.StringRes
import com.example.familibalance.R

/**
 * Рамки аватара в «Магазине стиля» (вкладка «Рамки»).
 *
 * В профиль сохраняется [AvatarFrameType.name] ("NONE", "NEON", ...).
 * NONE — бесплатная и открыта по умолчанию; NEON/FIRE покупаются за
 * монеты; GUARDIAN/ROYAL_GOLD/STAR_LEGEND открываются автоматически
 * при достижении уровня (см. [isFrameUnlocked]).
 */
enum class AvatarFrameType(
    @StringRes val titleRes: Int,
    val unlockRule: ThemeUnlockRule = ThemeUnlockRule.Free
) {
    NONE(R.string.frame_none),
    NEON(R.string.frame_neon, ThemeUnlockRule.Paid(price = 120)),
    FIRE(R.string.frame_fire, ThemeUnlockRule.Paid(price = 180)),
    GUARDIAN(
        R.string.frame_guardian,
        ThemeUnlockRule.Level(minLevel = 10)
    ),
    ROYAL_GOLD(
        R.string.frame_royal_gold,
        ThemeUnlockRule.Level(minLevel = 25)
    ),
    STAR_LEGEND(
        R.string.frame_star_legend,
        ThemeUnlockRule.Level(minLevel = 40)
    )
}

/** ID рамок, доступных бесплатно по умолчанию. */
val DefaultUnlockedFrameIds: List<String> =
    AvatarFrameType.entries.filter { it.unlockRule is ThemeUnlockRule.Free }.map { it.name }

/**
 * Доступна ли рамка профилю: бесплатная, уже купленная
 * (name в unlockedFrames) или достигнут нужный уровень
 * (ThemeUnlockRule.Level — считается разблокированной динамически,
 * без записи в unlockedFrames).
 */
fun AvatarFrameType.isFrameUnlocked(
    unlockedFrames: List<String>,
    level: Int
): Boolean {
    if (unlockRule is ThemeUnlockRule.Free) return true
    if (name in unlockedFrames) return true
    val lvl = unlockRule as? ThemeUnlockRule.Level ?: return false
    return level >= lvl.minLevel
}

/** Маппинг сохранённого frameId на тип рамки; неизвестное → NONE. */
fun avatarFrameTypeOf(frameId: String?): AvatarFrameType {
    val id = frameId?.trim().orEmpty()
    if (id.isEmpty()) return AvatarFrameType.NONE
    return AvatarFrameType.entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
        ?: AvatarFrameType.NONE
}
