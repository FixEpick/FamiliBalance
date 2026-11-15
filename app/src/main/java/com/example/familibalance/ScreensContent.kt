package com.example.familibalance

import android.content.Context
import android.net.Uri
import com.example.familibalance.data.SecurePrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

@Serializable
enum class QuestStatus {
    AVAILABLE,
    PENDING,
    READY_TO_CLAIM,
    CLAIMED,
    APPROVED
}

@Serializable
data class BonusQuest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val reward: Int,
    val description: String = "",
    val status: QuestStatus = QuestStatus.AVAILABLE,
    val createdDate: String = "",
    val rejectReason: String? = null,
    val photoUrl: String? = null,
    val comment: String = ""
)
@Serializable
enum class QuestFrequency {
    ONCE,
    DAILY,
    WEEKLY
}

@Serializable
data class CustomQuest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val reward: Int = 0,
    val description: String = "",
    val frequency: QuestFrequency = QuestFrequency.ONCE,
    val isVisible: Boolean = true,
    val status: QuestStatus = QuestStatus.AVAILABLE,
    val photoUrl: String? = null,
    val rejectReason: String? = null,
    val lastResetDate: String = "",
    val comment: String = ""
)


@Serializable
data class HistoryData(
    val title: String,
    val time: String,
    val date: String,
    val amount: String,
    val isIncome: Boolean,
    val category: String
)

enum class CashoutStatus {
    PENDING,
    PROCESSED,
    REJECTED
}

@Serializable
data class CashoutRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val amount: Int,
    val comment: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PendingQuestData(
    val title: String,
    val reward: String,
    val description: String,
    val photoUrl: String? = null,
    @Transient val photoUri: Uri? = null
)


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

private const val MAX_HISTORY_ITEMS = 200

@Serializable
data class ChildProfileState(
    val id: String,
    val name: String,
    val avatar: String,
    val balance: Int = 40,
    val boughtMinutesToday: Int = 0,
    val honestAdmissionStatus: QuestStatus = QuestStatus.AVAILABLE,
    val lastHonestAdmissionDate: String = "",
    val honestAdmissionText: String = "",
    val closetStatus: QuestStatus = QuestStatus.AVAILABLE,
    val closetRejectReason: String? = null,
    val closetPhotoUrl: String? = null,
    val closetComment: String = "",
    val roomStatus: QuestStatus = QuestStatus.AVAILABLE,
    val roomRejectReason: String? = null,
    val roomPhotoUrl: String? = null,
    val roomComment: String = "",
    val diaryStatus: QuestStatus = QuestStatus.AVAILABLE,
    val diaryRejectReason: String? = null,
    val diaryPhotoUrl: String? = null,
    val diaryComment: String = "",
    val diaryApprovedReward: Int = 100,
    val wardrobeFrequency: QuestFrequency = QuestFrequency.DAILY,
    val roomFrequency: QuestFrequency = QuestFrequency.DAILY,
    val diaryFrequency: QuestFrequency = QuestFrequency.WEEKLY,
    val closetResetDate: String = "",
    val roomResetDate: String = "",
    val diaryResetDate: String = "",
    val lastDailyResetDate: String = "",
    val dreamTitle: String = "",
    val dreamTarget: Int = 0,
    val streakDays: Int = 0,
    val lastStreakDate: String = "",
    val streakBonusMessage: String = "",
    val fivesCount: Int = 0,
    val wardrobeCount: Int = 0,
    val roomCount: Int = 0,
    val achievements: List<AchievementConfig> = defaultAchievements(),
    val cashoutRequests: List<CashoutRequest> = emptyList(),
    val pendingQuests: List<PendingQuestData> = emptyList(),
    val bonusQuests: List<BonusQuest> = emptyList(),
    val customQuests: List<CustomQuest> = emptyList(),
    val historyItems: List<HistoryData> = emptyList(),
    val selectedThemeId: String = "COSMIC_BLUE",
    val unlockedThemes: List<String> = listOf("COSMIC_BLUE"),
    val isWardrobeVisible: Boolean = true,
    val isRoomVisible: Boolean = true,
    val isDiaryVisible: Boolean = true,
    val isWardrobeDeleted: Boolean = false,
    val isRoomDeleted: Boolean = false,
    val isDiaryDeleted: Boolean = false,
    val wardrobeTitle: String = "Фото шкафа",
    val wardrobeDesc: String = "Наведи идеальный порядок на полках и сфотографируй результат для папы",
    val wardrobeReward: Int = 50,
    val roomTitle: String = "Фото комнаты",
    val roomDesc: String = "Заправь кровать без складок, собери игрушки и пришли фото",
    val roomReward: Int = 50,
    val diaryTitle: String = "📚 Дневник",
    val diaryDesc: String = "Квест выполняется раз в неделю. Папа проверяет все оценки в дневнике за прошедшую неделю.",
    val parentUserId: String = "",
    val authUserId: String = "",
    /** Хэш PIN (salt:hex). Пустая строка — PIN ещё не установлен. */
    val pinCode: String = "",
    val parentEmail: String = "",
    /** "active" — полноценный член семьи; "pending" — ждёт подтверждения родителя. */
    val status: String = "active",
    /** ANDROID_ID устройства, с которого ребёнок последний раз входил (для будущих пушей). */
    val deviceId: String = ""
) {
    constructor(
        id: String,
        name: String,
        avatar: String,
        initialBalance: Int = 40,
        initialMinutes: Int = 0
    ) : this(
        id = id,
        name = name,
        avatar = avatar,
        balance = initialBalance,
        boughtMinutesToday = initialMinutes
    )

    companion object {
        fun fromDto(dto: ChildProfileDto): ChildProfileState = ChildProfileState(
            id = dto.id,
            name = dto.name.ifBlank { "Ребёнок ${dto.id}" },
            avatar = dto.avatar.ifBlank { "🧒" },
            balance = dto.balance,
            boughtMinutesToday = dto.boughtMinutesToday,
            honestAdmissionStatus = dto.honestAdmissionStatus,
            lastHonestAdmissionDate = dto.lastHonestAdmissionDate,
            honestAdmissionText = dto.honestAdmissionText,
            closetStatus = dto.closetStatus,
            closetRejectReason = dto.closetRejectReason,
            closetPhotoUrl = dto.closetPhotoUrl,
            closetComment = dto.closetComment,
            roomStatus = dto.roomStatus,
            roomRejectReason = dto.roomRejectReason,
            roomPhotoUrl = dto.roomPhotoUrl,
            roomComment = dto.roomComment,
            diaryStatus = dto.diaryStatus,
            diaryRejectReason = dto.diaryRejectReason,
            diaryPhotoUrl = dto.diaryPhotoUrl,
            diaryComment = dto.diaryComment,
            diaryApprovedReward = dto.diaryApprovedReward,
            wardrobeFrequency = dto.wardrobeFrequency,
            roomFrequency = dto.roomFrequency,
            diaryFrequency = dto.diaryFrequency,
            closetResetDate = dto.closetResetDate,
            roomResetDate = dto.roomResetDate,
            diaryResetDate = dto.diaryResetDate,
            lastDailyResetDate = dto.lastDailyResetDate,
            dreamTitle = dto.dreamTitle,
            dreamTarget = dto.dreamTarget,
            streakDays = dto.streakDays,
            lastStreakDate = dto.lastStreakDate,
            streakBonusMessage = dto.streakBonusMessage,
            fivesCount = dto.fivesCount,
            wardrobeCount = dto.wardrobeCount,
            roomCount = dto.roomCount,
            achievements = dto.achievements,
            cashoutRequests = dto.cashoutRequests,
            pendingQuests = dto.pendingQuests,
            bonusQuests = dto.bonusQuests,
            historyItems = dto.historyItems.takeLast(MAX_HISTORY_ITEMS),
            selectedThemeId = dto.selectedThemeId,
            unlockedThemes = dto.unlockedThemes,
            customQuests = dto.customQuests,
            isWardrobeVisible = dto.isWardrobeVisible,
            isRoomVisible = dto.isRoomVisible,
            isDiaryVisible = dto.isDiaryVisible,
            isWardrobeDeleted = dto.isWardrobeDeleted,
            isRoomDeleted = dto.isRoomDeleted,
            isDiaryDeleted = dto.isDiaryDeleted,
            wardrobeTitle = dto.wardrobeTitle,
            wardrobeDesc = dto.wardrobeDesc,
            wardrobeReward = dto.wardrobeReward,
            roomTitle = dto.roomTitle,
            roomDesc = dto.roomDesc,
            roomReward = dto.roomReward,
            diaryTitle = dto.diaryTitle,
            diaryDesc = dto.diaryDesc,
            parentUserId = dto.parentUserId,
            authUserId = dto.authUserId,
            pinCode = dto.pinCode,
            parentEmail = dto.parentEmail,
            status = dto.status,
            deviceId = dto.deviceId
        )
    }
}

@Serializable
data class ChildProfileDto(
    val id: String,
    val name: String,
    val avatar: String,
    val balance: Int = 40,
    val boughtMinutesToday: Int = 0,
    val honestAdmissionStatus: QuestStatus = QuestStatus.AVAILABLE,
    val lastHonestAdmissionDate: String = "",
    val honestAdmissionText: String = "",
    val closetStatus: QuestStatus = QuestStatus.AVAILABLE,
    val closetRejectReason: String? = null,
    val closetPhotoUrl: String? = null,
    val closetComment: String = "",
    val roomStatus: QuestStatus = QuestStatus.AVAILABLE,
    val roomRejectReason: String? = null,
    val roomPhotoUrl: String? = null,
    val roomComment: String = "",
    val diaryStatus: QuestStatus = QuestStatus.AVAILABLE,
    val diaryRejectReason: String? = null,
    val diaryPhotoUrl: String? = null,
    val diaryComment: String = "",
    val diaryApprovedReward: Int = 100,
    val wardrobeFrequency: QuestFrequency = QuestFrequency.DAILY,
    val roomFrequency: QuestFrequency = QuestFrequency.DAILY,
    val diaryFrequency: QuestFrequency = QuestFrequency.WEEKLY,
    val closetResetDate: String = "",
    val roomResetDate: String = "",
    val diaryResetDate: String = "",
    val lastDailyResetDate: String = "",
    val dreamTitle: String = "",
    val dreamTarget: Int = 0,
    val streakDays: Int = 0,
    val lastStreakDate: String = "",
    val streakBonusMessage: String = "",
    val fivesCount: Int = 0,
    val wardrobeCount: Int = 0,
    val roomCount: Int = 0,
    val achievements: List<AchievementConfig> = defaultAchievements(),
    val cashoutRequests: List<CashoutRequest> = emptyList(),
    val pendingQuests: List<PendingQuestData> = emptyList(),
    val bonusQuests: List<BonusQuest> = emptyList(),
    val historyItems: List<HistoryData> = emptyList(),
    val selectedThemeId: String = "COSMIC_BLUE",
    val unlockedThemes: List<String> = listOf("COSMIC_BLUE"),
    val customQuests: List<CustomQuest> = emptyList(),
    val isWardrobeVisible: Boolean = true,
    val isRoomVisible: Boolean = true,
    val isDiaryVisible: Boolean = true,
    val isWardrobeDeleted: Boolean = false,
    val isRoomDeleted: Boolean = false,
    val isDiaryDeleted: Boolean = false,
    val wardrobeTitle: String = "Фото шкафа",
    val wardrobeDesc: String = "Наведи идеальный порядок на полках и сфотографируй результат для папы",
    val wardrobeReward: Int = 50,
    val roomTitle: String = "Фото комнаты",
    val roomDesc: String = "Заправь кровать без складок, собери игрушки и пришли фото",
    val roomReward: Int = 50,
    val diaryTitle: String = "📚 Дневник",
    val diaryDesc: String = "Квест выполняется раз в неделю. Папа проверяет все оценки в дневнике за прошедшую неделю.",
    val parentUserId: String = "",
    val authUserId: String = "",
    val pinCode: String = "",
    val parentEmail: String = "",
    val status: String = "active",
    val deviceId: String = ""
)

fun ChildProfileState.toDto(): ChildProfileDto = ChildProfileDto(
    id = id,
    name = name,
    avatar = avatar,
    balance = balance,
    boughtMinutesToday = boughtMinutesToday,
    honestAdmissionStatus = honestAdmissionStatus,
    lastHonestAdmissionDate = lastHonestAdmissionDate,
    honestAdmissionText = honestAdmissionText,
    closetStatus = closetStatus,
    closetRejectReason = closetRejectReason,
    closetPhotoUrl = closetPhotoUrl,
    closetComment = closetComment,
    roomStatus = roomStatus,
    roomRejectReason = roomRejectReason,
    roomPhotoUrl = roomPhotoUrl,
    roomComment = roomComment,
    diaryStatus = diaryStatus,
    diaryRejectReason = diaryRejectReason,
    diaryPhotoUrl = diaryPhotoUrl,
    diaryComment = diaryComment,
    diaryApprovedReward = diaryApprovedReward,
    wardrobeFrequency = wardrobeFrequency,
    roomFrequency = roomFrequency,
    diaryFrequency = diaryFrequency,
    closetResetDate = closetResetDate,
    roomResetDate = roomResetDate,
    diaryResetDate = diaryResetDate,
    lastDailyResetDate = lastDailyResetDate,
    dreamTitle = dreamTitle,
    dreamTarget = dreamTarget,
    streakDays = streakDays,
    lastStreakDate = lastStreakDate,
    streakBonusMessage = streakBonusMessage,
    fivesCount = fivesCount,
    wardrobeCount = wardrobeCount,
    roomCount = roomCount,
    achievements = achievements.toList(),
    cashoutRequests = cashoutRequests,
    pendingQuests = pendingQuests.toList(),
    bonusQuests = bonusQuests.toList(),
    historyItems = historyItems.takeLast(MAX_HISTORY_ITEMS),
    selectedThemeId = selectedThemeId,
    unlockedThemes = unlockedThemes,
    customQuests = customQuests.toList(),
    isWardrobeVisible = isWardrobeVisible,
    isRoomVisible = isRoomVisible,
    isDiaryVisible = isDiaryVisible,
    isWardrobeDeleted = isWardrobeDeleted,
    isRoomDeleted = isRoomDeleted,
    isDiaryDeleted = isDiaryDeleted,
    wardrobeTitle = wardrobeTitle,
    wardrobeDesc = wardrobeDesc,
    wardrobeReward = wardrobeReward,
    roomTitle = roomTitle,
    roomDesc = roomDesc,
    roomReward = roomReward,
    diaryTitle = diaryTitle,
    diaryDesc = diaryDesc,
    parentUserId = parentUserId,
    authUserId = authUserId,
    pinCode = pinCode,
    parentEmail = parentEmail,
    status = status,
    deviceId = deviceId
)

object AppSharedState {
    var selectedChildId: String = "user_1"
    var tempImageUri: Uri? = null

    /** Событие: удалённое состояние применено к [children] (Realtime / первичная загрузка). */
    val remoteStateChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    // Профили загружаются из локального хранилища / Supabase.
    // Дефолтных «Пользователь 1/2» больше нет — первый запуск показывает пустой список.
    // ConcurrentHashMap: children мутируется из Realtime-потока (IO) и Main —
    // обычный mutableMapOf давал ConcurrentModificationException.
    val children = ConcurrentHashMap<String, ChildProfileState>()

    val currentChild: ChildProfileState
        get() = children[selectedChildId]
            ?: children["user_1"]
            ?: children.values.firstOrNull()
            ?: ChildProfileState(id = "user_1", name = "Игрок", avatar = "🧒")

    val adminNotificationLog = mutableListOf<String>()
    var adminNotificationMessage: String? = null
}

object AppStateStorage {
    private const val PREFS_NAME = "familibalance_prefs"
    private const val KEY_STATE = "app_state_json"
    private const val KEY_LAST_DAILY = "last_daily_reset"
    private const val KEY_LAST_WEEKLY = "last_weekly_reset"

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun saveLocalOnly(context: Context) {
        try {
            val prefs = SecurePrefs.get(context, PREFS_NAME)
            val rootObj = org.json.JSONObject()
            val childrenObj = org.json.JSONObject()

            AppSharedState.children.forEach { (id, child) ->
                childrenObj.put(id, org.json.JSONObject(serializeSingleChild(child)))
            }
            rootObj.put("children", childrenObj)
            rootObj.put("selectedChildId", AppSharedState.selectedChildId)

            prefs.edit()
                .putString(KEY_STATE, rootObj.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun serializeSingleChild(child: ChildProfileState): String {
        return jsonFormat.encodeToString(child.toDto())
    }

    fun applyRemoteChildJson(context: Context, userId: String, jsonStr: String, parentUserId: String? = null, status: String? = null, authUserId: String? = null) {
        if (com.example.familibalance.data.repository.SyncStatus.isChildDirty(userId)) return
        try {
            val dto = jsonFormat.decodeFromString<ChildProfileDto>(jsonStr)
            // Колонка parent_user_id в БД важнее значения внутри state_json
            val profile = ChildProfileState.fromDto(dto)
                .copy(
                    parentUserId = parentUserId?.takeIf { it.isNotBlank() } ?: dto.parentUserId,
                    authUserId = authUserId?.takeIf { it.isNotBlank() } ?: dto.authUserId,
                    status = status?.takeIf { it.isNotBlank() } ?: dto.status
                )
            AppSharedState.children[userId] = profile
            saveLocalOnly(context)
            AppSharedState.remoteStateChanged.tryEmit(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Suspend-вариант: парсит JSON на IO-потоке, применяет на Main.
     * Используется из SupabaseManager (Realtime/первичная загрузка), чтобы не фризить UI.
     */
    suspend fun applyRemoteChildJsonAsync(context: Context, userId: String, jsonStr: String, parentUserId: String? = null, status: String? = null, authUserId: String? = null) {
        if (com.example.familibalance.data.repository.SyncStatus.isChildDirty(userId)) return
        try {
            val dto = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                jsonFormat.decodeFromString<ChildProfileDto>(jsonStr)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val profile = ChildProfileState.fromDto(dto)
                    .copy(
                        parentUserId = parentUserId?.takeIf { it.isNotBlank() } ?: dto.parentUserId,
                        authUserId = authUserId?.takeIf { it.isNotBlank() } ?: dto.authUserId,
                        status = status?.takeIf { it.isNotBlank() } ?: dto.status
                    )
                AppSharedState.children[userId] = profile
                saveLocalOnly(context)
                AppSharedState.remoteStateChanged.tryEmit(Unit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadState(context: Context) {
        try {
            val prefs = SecurePrefs.get(context, PREFS_NAME)
            val jsonStr = prefs.getString(KEY_STATE, null) ?: return
            val rootObj = org.json.JSONObject(jsonStr)

            if (rootObj.has("selectedChildId")) {
                AppSharedState.selectedChildId = rootObj.getString("selectedChildId")
            }

            if (rootObj.has("children")) {
                val childrenObj = rootObj.getJSONObject("children")
                childrenObj.keys().forEach { id ->
                    if (childrenObj.has(id)) {
                        val childObj = childrenObj.getJSONObject(id)
                        try {
                            val dto = jsonFormat.decodeFromString<ChildProfileDto>(childObj.toString())
                            AppSharedState.children[id] = ChildProfileState.fromDto(dto).copy(id = id)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Дневной сброс boughtMinutesToday/bonusQuests.
     * Возвращает список изменённых профилей — репозиторий сам пометит их
     * dirty и отправит в Supabase (MVI-01: без прямого push в обход SyncStatus).
     */
    fun checkResets(context: Context): List<ChildProfileState> {
        val changedChildren = mutableListOf<ChildProfileState>()
        try {
            val prefs = SecurePrefs.get(context, PREFS_NAME)
            val calendar = Calendar.getInstance()
            val todayStr = getCurrentDateString()
            val currentWeekStr = "${calendar.get(Calendar.YEAR)}-W${calendar.get(Calendar.WEEK_OF_YEAR)}"

            val savedDaily = prefs.getString(KEY_LAST_DAILY, null)
            val savedWeekly = prefs.getString(KEY_LAST_WEEKLY, null)

            if (savedDaily == null || savedWeekly == null) {
                prefs.edit()
                    .putString(KEY_LAST_DAILY, todayStr)
                    .putString(KEY_LAST_WEEKLY, currentWeekStr)
                    .apply()
                return changedChildren
            }

            var changed = false

            // Хронологическое сравнение: сбрасываем только если сегодня
            // строго позже сохранённой даты. При откате времени/часового
            // пояса (сегодня «раньше» вчерашнего) сброс не срабатывает.
            val savedDailyDate = runCatching {
                java.time.LocalDate.parse(
                    savedDaily,
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                )
            }.getOrNull()
            val todayDate = java.time.LocalDate.now()
            val isNewDay = savedDailyDate == null || todayDate.isAfter(savedDailyDate)

            if (isNewDay) {
                // Сбросы встроенных квестов (шкаф/комната/дневник) выполняет
                // MainViewModel.resetDailyQuestsIfNeeded с учётом frequency каждого квеста.
                AppSharedState.children.replaceAll { _, child ->
                    val reset = child.copy(
                        boughtMinutesToday = 0,
                        bonusQuests = child.bonusQuests.filter { it.status != QuestStatus.CLAIMED }
                    )
                    if (reset != child) changedChildren += reset
                    reset
                }
                prefs.edit().putString(KEY_LAST_DAILY, todayStr).apply()
                changed = true
            }

            if (savedWeekly != currentWeekStr) {
                prefs.edit().putString(KEY_LAST_WEEKLY, currentWeekStr).apply()
            }

            if (changed) {
                saveLocalOnly(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return changedChildren
    }
}

fun getCurrentDateString(): String {
    return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

fun getCurrentTimeString(): String {
    return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}

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
