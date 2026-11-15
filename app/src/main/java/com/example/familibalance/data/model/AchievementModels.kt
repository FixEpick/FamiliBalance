package com.example.familibalance.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AchievementBranch {
    DIARY,
    WARDROBE,
    ROOM
}

@Serializable
data class AchievementConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val targetCount: Int,
    val reward: Int,
    val branch: AchievementBranch,
    val isVisible: Boolean = true,
    val isClaimed: Boolean = false
)

fun defaultAchievements(): List<AchievementConfig> = listOf(
    AchievementConfig(
        title = "Первая кровь",
        description = "Нанес первый удар по скучной учебе. Дальше — больше.",
        targetCount = 1,
        reward = 20,
        branch = AchievementBranch.DIARY
    ),
    AchievementConfig(
        title = "Случайный Крит",
        description = "Выбил критический урон по школьной программе.",
        targetCount = 5,
        reward = 50,
        branch = AchievementBranch.DIARY
    ),
    AchievementConfig(
        title = "Босс Первой Парты",
        description = "Твоя аура отличника отпугивает двойки. Списать у тебя — платный DLC.",
        targetCount = 10,
        reward = 100,
        branch = AchievementBranch.DIARY
    ),
    AchievementConfig(
        title = "Чит-код на знания",
        description = "Учителя подозревают, что ты скачал взломанную версию учебника.",
        targetCount = 20,
        reward = 200,
        branch = AchievementBranch.DIARY
    ),
    AchievementConfig(
        title = "Абсолютный Баланс",
        description = "Разработчики школы пытались тебя понерфить, но ты всё равно тащишь катку.",
        targetCount = 30,
        reward = 300,
        branch = AchievementBranch.DIARY
    ),
    AchievementConfig(
        title = "Спидраннер четверти",
        description = "Проходишь сложные темы на уровне «Кошмар» с закрытыми глазами.",
        targetCount = 40,
        reward = 400,
        branch = AchievementBranch.DIARY
    ),
    AchievementConfig(
        title = "Легенда Сервера",
        description = "Твоё имя пора выбить золотыми буквами на доске почета. Главный киберспортсмен класса.",
        targetCount = 50,
        reward = 500,
        branch = AchievementBranch.DIARY
    ),
    AchievementConfig(
        title = "Первооткрыватель полок",
        description = "Нашел давно потерянный носок и навел порядок.",
        targetCount = 1,
        reward = 10,
        branch = AchievementBranch.WARDROBE
    ),
    AchievementConfig(
        title = "Победитель Монстров",
        description = "Заглянул в самые темные углы шкафа и не испугался.",
        targetCount = 5,
        reward = 30,
        branch = AchievementBranch.WARDROBE
    ),
    AchievementConfig(
        title = "Тетрис-Мастер",
        description = "Укладываешь футболки и джинсы так плотно, что позавидует любой грузчик.",
        targetCount = 10,
        reward = 60,
        branch = AchievementBranch.WARDROBE
    ),
    AchievementConfig(
        title = "Инвентарь 100 уровня",
        description = "В твоем лутбоксе с одеждой теперь идеальная сортировка по цвету и редкости.",
        targetCount = 20,
        reward = 120,
        branch = AchievementBranch.WARDROBE
    ),
    AchievementConfig(
        title = "Портал в Нарнию",
        description = "Навел такую чистоту, что за вешалками открылся проход в волшебную страну.",
        targetCount = 30,
        reward = 180,
        branch = AchievementBranch.WARDROBE
    ),
    AchievementConfig(
        title = "Хранитель Текстур",
        description = "Ни одна вещь не прогружается на полу. Всё строго на своих полках.",
        targetCount = 40,
        reward = 240,
        branch = AchievementBranch.WARDROBE
    ),
    AchievementConfig(
        title = "Бог Гардероба",
        description = "Твой шкаф выглядит как витрина дорогого магазина. Футболки лежат под линейку.",
        targetCount = 50,
        reward = 300,
        branch = AchievementBranch.WARDROBE
    ),
    AchievementConfig(
        title = "Стартовая Локация",
        description = "Зачистил базу от разбросанных игрушек и вражеских фантиков.",
        targetCount = 1,
        reward = 10,
        branch = AchievementBranch.ROOM
    ),
    AchievementConfig(
        title = "Гроза Пылевых Бурь",
        description = "Нанес сокрушительный удар по хаосу. Кровать заправлена без единой складки.",
        targetCount = 5,
        reward = 30,
        branch = AchievementBranch.ROOM
    ),
    AchievementConfig(
        title = "Архитектор Уюта",
        description = "Твоя комната прошла проверку санэпидемстанции и папиного строгого взгляда.",
        targetCount = 10,
        reward = 60,
        branch = AchievementBranch.ROOM
    ),
    AchievementConfig(
        title = "Анти-Хаос Щит",
        description = "Включил пассивный навык чистоты: вещи сами боятся падать мимо корзины.",
        targetCount = 20,
        reward = 120,
        branch = AchievementBranch.ROOM
    ),
    AchievementConfig(
        title = "Мастер Скрытности",
        description = "Убрал всё так быстро и чисто, будто здесь вообще никто не живет.",
        targetCount = 30,
        reward = 180,
        branch = AchievementBranch.ROOM
    ),
    AchievementConfig(
        title = "Генерал Чистоты",
        description = "Твоя комната сияет ярче, чем броня паладина. Враг в лице беспорядка разбит.",
        targetCount = 40,
        reward = 240,
        branch = AchievementBranch.ROOM
    ),
    AchievementConfig(
        title = "Властелин Квадратных Метров",
        description = "Абсолютный контроль территории. Эту комнату пора снимать для обложки журнала.",
        targetCount = 50,
        reward = 300,
        branch = AchievementBranch.ROOM
    )
)

@Serializable
data class AchievementItemData(
    val title: String,
    val condition: String,
    val description: String,
    val reward: String,
    val progress: String,
    val isLocked: Boolean,
    val isCompleted: Boolean,
    val id: String = "",
    val rewardInt: Int = 0,
    val isReadyToClaim: Boolean = false
)

/**
 * Проверяет, заблокирована ли ачивка: она разблокирована, только если все
 * предыдущие (по targetCount) видимые ачивки той же ветки уже получены.
 */
fun isAchievementLocked(child: ChildProfileState, achievement: AchievementConfig): Boolean {
    val branchItems = child.achievements
        .filter { it.isVisible && it.branch == achievement.branch }
        .sortedBy { it.targetCount }
    val idx = branchItems.indexOfFirst { it.id == achievement.id }
    if (idx <= 0) return false
    return branchItems.subList(0, idx).any { !it.isClaimed }
}

fun getAchievements(child: ChildProfileState): List<AchievementItemData> {
    val visible = child.achievements.filter { it.isVisible }
    val branchCurrentCount = mapOf(
        AchievementBranch.DIARY to child.fivesCount,
        AchievementBranch.WARDROBE to child.wardrobeCount,
        AchievementBranch.ROOM to child.roomCount
    )
    val result = mutableListOf<AchievementItemData>()
    for ((branch, currentCount) in branchCurrentCount) {
        val branchItems = visible.filter { it.branch == branch }.sortedBy { it.targetCount }
        var prevClaimed: Boolean? = null
        branchItems.forEach { ach ->
            val isFirst = prevClaimed == null
            val isLocked = if (isFirst) false else !(prevClaimed == true)
            val condition = when (branch) {
                AchievementBranch.DIARY -> "Получить ${ach.targetCount} пятёрок"
                AchievementBranch.WARDROBE -> "Уборка в шкафу ${ach.targetCount} раз"
                AchievementBranch.ROOM -> "Уборка в комнате ${ach.targetCount} раз"
            }
            val progress = "${currentCount.coerceAtMost(ach.targetCount)}/${ach.targetCount}"
            val reward = "+${ach.reward} ₽"
            result.add(
                AchievementItemData(
                    title = ach.title,
                    condition = condition,
                    description = ach.description,
                    reward = reward,
                    progress = progress,
                    isLocked = isLocked,
                    isCompleted = ach.isClaimed,
                    id = ach.id,
                    rewardInt = ach.reward,
                    isReadyToClaim = !ach.isClaimed && !isLocked && currentCount >= ach.targetCount
                )
            )
            prevClaimed = ach.isClaimed
        }
    }
    return result
}
