package com.example.familibalance

import com.example.familibalance.data.model.*
import com.example.familibalance.data.repository.FamilyRepository
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.util.GamificationEngine
import com.example.familibalance.util.getCurrentDateString
import com.example.familibalance.viewmodel.actions.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Unit-тесты [MainViewModel] на Robolectric.
 *
 * [FamilyRepository] подменяется fake-реализацией: ViewModel не трогает
 * SharedPreferences/Supabase, а результат проверяется по профилю,
 * сохранённому в fake-репозитории через [FamilyRepository.updateChildProfile].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelTest {

    private class FakeFamilyRepository : FamilyRepository {
        val children = mutableMapOf<String, ChildProfileState>()
        var checkResetsCalled = false

        override val isSynced: StateFlow<Boolean> = MutableStateFlow(true)
        override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false)
        override val onRemoteStateChanged: SharedFlow<Unit> = MutableSharedFlow()
        override var selectedChildId: String = "user_1"

        override fun childrenSnapshot(): Map<String, ChildProfileState> = children.toMap()
        override fun getChild(userId: String): ChildProfileState? = children[userId]
        override fun getAllChildren(): List<ChildProfileState> = children.values.toList()
        override fun selectChild(childId: String, pushRemote: Boolean) {
            selectedChildId = childId
        }
        override fun loadState() {}
        override fun saveLocal() {}
        override fun saveAndSync(onlyChildId: String?) {}
        override fun checkResets() { checkResetsCalled = true }
        override fun pushChildProfile(child: ChildProfileState) {}
        override fun syncNow(childId: String?) {}
        override fun updateChildProfile(profile: ChildProfileState) {
            children[profile.id] = profile
        }
        override fun addAdminNotification(message: String) {}
    }

    private lateinit var repository: FakeFamilyRepository
    private lateinit var viewModel: MainViewModel

    private fun createChild(id: String = "user_1") = ChildProfileState(
        id = id,
        name = "Test $id",
        avatar = "🧒",
        initialBalance = 100
    ).copy(lastDailyResetDate = LocalDate.now().toString())

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        repository = FakeFamilyRepository()
        viewModel = MainViewModel(app, repository)
        // init() запускает checkResets()/resetDailyQuestsIfNeeded() на
        // Dispatchers.IO — без ожидания они могут выполниться уже ПОСЛЕ
        // заполнения repository.children в тесте и сбросить фикстуру
        // (флаки: diary READY_TO_CLAIM + пустая diaryResetDate → AVAILABLE).
        runBlocking {
            withTimeoutOrNull(5_000) {
                viewModel.isInitializing.first { !it }
            }
        }
        // Бонусный сундук по умолчанию выключен: 15% случайный бросок
        // делал бы существующие тесты недетерминированными. Тесты сундука
        // переопределяют roller сами.
        viewModel.bonusChestRoller = { _ -> null }
    }

    @Test
    fun `buyScreenTime deducts balance adds minutes and writes history`() {
        repository.children["user_1"] = createChild()

        viewModel.buyScreenTime("user_1", cost = 30, minutes = 15)

        val updated = repository.children["user_1"]!!
        assertEquals(70, updated.balance)
        assertEquals(15, updated.boughtMinutesToday)
        assertEquals(1, updated.historyItems.size)
        val entry = updated.historyItems.first()
        assertEquals("-30 ₽", entry.amount)
        assertFalse(entry.isIncome)
        assertEquals("Экранное время", entry.category)
    }

    @Test
    fun `submitHonestAdmission sets status to PENDING via copy`() {
        repository.children["user_1"] = createChild()

        viewModel.submitHonestAdmission("user_1", "Тест")

        assertEquals(
            QuestStatus.PENDING,
            repository.children["user_1"]!!.honestAdmissionStatus
        )
    }

    @Test
    fun `checkResets resets daily quests and custom quests via copy`() {
        val staleChild = createChild().copy(
            lastDailyResetDate = LocalDate.now().minusDays(1).toString(),
            closetResetDate = LocalDate.now().minusDays(1).toString(),
            roomResetDate = LocalDate.now().minusDays(1).toString(),
            diaryResetDate = LocalDate.now().minusDays(1).toString(),
            closetStatus = QuestStatus.CLAIMED,
            closetRejectReason = "старая причина",
            roomStatus = QuestStatus.READY_TO_CLAIM,
            customQuests = listOf(
                CustomQuest(
                    title = "Ежедневный квест",
                    reward = 10,
                    frequency = QuestFrequency.DAILY,
                    status = QuestStatus.CLAIMED,
                    lastResetDate = LocalDate.now().minusDays(2).toString()
                )
            )
        )
        repository.children["user_1"] = staleChild

        viewModel.checkResets()

        assertTrue(repository.checkResetsCalled)
        val updated = repository.children["user_1"]!!
        assertEquals(LocalDate.now().toString(), updated.lastDailyResetDate)
        assertEquals(QuestStatus.AVAILABLE, updated.closetStatus)
        assertEquals(null, updated.closetRejectReason)
        assertEquals(QuestStatus.AVAILABLE, updated.roomStatus)
        assertEquals(QuestStatus.AVAILABLE, updated.customQuests.first().status)
        assertEquals(LocalDate.now().toString(), updated.customQuests.first().lastResetDate)
    }

    @Test
    fun `checkResets unblocks CLAIMED built-in quest with empty reset date`() {
        // BUG-02: пустая дата сброса + CLAIMED/READY_TO_CLAIM → квест зависал навсегда.
        val stuckChild = createChild().copy(
            closetResetDate = "",
            roomResetDate = "",
            diaryResetDate = "",
            closetStatus = QuestStatus.CLAIMED,
            roomStatus = QuestStatus.READY_TO_CLAIM,
            diaryStatus = QuestStatus.AVAILABLE
        )
        repository.children["user_1"] = stuckChild

        viewModel.checkResets()

        val updated = repository.children["user_1"]!!
        assertEquals(QuestStatus.AVAILABLE, updated.closetStatus)
        assertEquals(QuestStatus.AVAILABLE, updated.roomStatus)
        assertEquals(QuestStatus.AVAILABLE, updated.diaryStatus)
        assertEquals(LocalDate.now().toString(), updated.closetResetDate)
        assertEquals(LocalDate.now().toString(), updated.roomResetDate)
        // AVAILABLE без даты — статус не трогаем, дату не ставим.
        assertEquals("", updated.diaryResetDate)
    }

    @Test
    fun `checkResets decays streak by one flame per missed day`() {
        val today = LocalDate.now()
        // Последний чек-ин позавчера → сгорает 2 дня → 5 - 2 = 3
        repository.children["user_1"] = createChild().copy(
            streakDays = 5,
            lastStreakDate = today.minusDays(2).toString()
        )
        // Стрик жив: чек-ин был вчера — серия продолжается
        repository.children["user_2"] = createChild("user_2").copy(
            streakDays = 3,
            lastStreakDate = today.minusDays(1).toString()
        )
        // Длительный пропуск: стрик не уходит в минус
        repository.children["user_3"] = createChild("user_3").copy(
            streakDays = 2,
            lastStreakDate = today.minusDays(10).toString()
        )

        viewModel.checkResets()

        val decayed = repository.children["user_1"]!!
        assertEquals(3, decayed.streakDays)
        assertEquals("", decayed.lastStreakDate)

        val alive = repository.children["user_2"]!!
        assertEquals(3, alive.streakDays)
        assertEquals(today.minusDays(1).toString(), alive.lastStreakDate)

        val burnedOut = repository.children["user_3"]!!
        assertEquals(0, burnedOut.streakDays)
        assertEquals("", burnedOut.lastStreakDate)
    }

    @Test
    fun `applyFine halves penalty when honest admission is APPROVED and consumes it`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            streakDays = 4,
            lastStreakDate = LocalDate.now().toString(),
            honestAdmissionStatus = QuestStatus.APPROVED
        )

        viewModel.applyFine("user_1", amount = 40, reason = "Разбил вазу")

        val updated = repository.children["user_1"]!!
        assertEquals(80, updated.balance) // 40 / 2 = 20
        assertEquals(QuestStatus.CLAIMED, updated.honestAdmissionStatus)
        assertEquals(0, updated.streakDays)
        assertEquals("", updated.lastStreakDate)
        assertTrue(updated.historyItems.first().title.contains("Скидка 50%"))

        // Повторный штраф — уже без скидки
        viewModel.applyFine("user_1", amount = 40, reason = "Ещё раз")
        assertEquals(40, repository.children["user_1"]!!.balance)
    }

    @Test
    fun `applyFine charges full amount without approved admission`() {
        repository.children["user_1"] = createChild().copy(balance = 100, streakDays = 4)

        viewModel.applyFine("user_1", amount = 30, reason = "Штраф")

        val updated = repository.children["user_1"]!!
        assertEquals(70, updated.balance)
        assertEquals(QuestStatus.AVAILABLE, updated.honestAdmissionStatus)
        assertEquals(0, updated.streakDays)
    }

    @Test
    fun `adminRejectQuest with cheat penalty deducts 100 resets streak and keeps invariant`() {
        repository.children["user_1"] = createChild().copy(
            balance = 200,
            streakDays = 5,
            lastStreakDate = LocalDate.now().toString(),
            closetStatus = QuestStatus.PENDING
        )

        // Пустая причина → дефолтная «задание сделано нечестно».
        viewModel.adminRejectQuest("user_1", "closet", "", withCheatPenalty = true)

        val updated = repository.children["user_1"]!!
        // Штраф за обман: -100 ₽.
        assertEquals(100, updated.balance)
        // Серия без штрафов сброшена.
        assertEquals(0, updated.streakDays)
        assertEquals("", updated.lastStreakDate)
        // Статус отклонён, причина — дефолтная для нечестного выполнения.
        assertEquals(QuestStatus.AVAILABLE, updated.closetStatus)
        val expectedReason = RuntimeEnvironment.getApplication()
            .getString(R.string.admin_reject_reason_cheat)
        assertEquals(expectedReason, updated.closetRejectReason)
        // Инвариант: дельта баланса (-100) = сумма записей истории.
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(-100, historySum)
        assertTrue(updated.historyItems.any { it.type == HistoryType.FINE && it.amountValue == -100 })
    }

    @Test
    fun `adminRejectQuest without cheat penalty keeps streak and balance`() {
        repository.children["user_1"] = createChild().copy(
            balance = 200,
            streakDays = 5,
            lastStreakDate = LocalDate.now().toString(),
            closetStatus = QuestStatus.PENDING
        )

        viewModel.adminRejectQuest("user_1", "closet", "Пересними фото")

        val updated = repository.children["user_1"]!!
        assertEquals(200, updated.balance)
        assertEquals(5, updated.streakDays)
        assertEquals("Пересними фото", updated.closetRejectReason)
        assertTrue(updated.historyItems.isEmpty())
    }

    @Test
    fun `applyDiaryGrades sets READY_TO_CLAIM and computes approved reward`() {
        repository.children["user_1"] = createChild().copy(
            diaryStatus = QuestStatus.PENDING,
            diaryPhotoUrl = null
        )

        viewModel.applyDiaryGrades("user_1", count5 = 2, count4 = 1, count3 = 1, count2 = 0)

        val updated = repository.children["user_1"]!!
        assertEquals(QuestStatus.READY_TO_CLAIM, updated.diaryStatus)
        // 2×50 + 1×30 = 130 — только положительные оценки
        assertEquals(130, updated.diaryApprovedReward)
        assertEquals(2, updated.diaryCount5)
        assertEquals(1, updated.diaryCount4)
        assertEquals(1, updated.diaryCount3)
        assertEquals(0, updated.diaryCount2)
    }

    @Test
    fun `claimQuestReward diary credits balance without duplicate summary entry`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryApprovedReward = 80, // 1×5 (50) + 1×4 (30)
            diaryCount5 = 1,
            diaryCount4 = 1,
            diaryCount3 = 0,
            diaryCount2 = 0
        )

        viewModel.claimQuestReward("user_1", "diary")

        val updated = repository.children["user_1"]!!
        // 180 + 56 — бонус уровня (XP 115 ≥ 109 → level 2).
        assertEquals(236, updated.balance)
        assertEquals(QuestStatus.CLAIMED, updated.diaryStatus)
        assertEquals(0, updated.diaryCount5)
        assertEquals(0, updated.diaryCount4)
        // Записи по оценкам + сводная расшифровка формулы — без дубля «Награда получена»
        assertTrue(updated.historyItems.none { it.title.contains("Итоги недели") })
        assertTrue(updated.historyItems.none { it.title.startsWith("Награда получена") })
        assertEquals(3, updated.historyItems.count { it.category == "Дневник" })
        assertTrue(updated.historyItems.any { it.title == "Оценка 5" && it.amount == "+50 ₽" })
        assertTrue(updated.historyItems.any { it.title == "Оценка 4" && it.amount == "+30 ₽" })
        // Сводная запись хранит формулу расчёта и не влияет на сумму истории
        val summary = updated.historyItems.firstOrNull { it.type == HistoryType.DIARY_SUMMARY }
        assertNotNull(summary)
        assertEquals("5×1 (+50), 4×1 (+30)", summary!!.arg)
        assertNull(summary.amountValue)
    }

    @Test
    fun `claimQuestReward diary applies grade 3 and 2 fines after claim`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            streakDays = 4,
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryApprovedReward = 80,
            diaryCount5 = 1,
            diaryCount4 = 1,
            diaryCount3 = 1,
            diaryCount2 = 1
        )

        viewModel.claimQuestReward("user_1", "diary")

        val updated = repository.children["user_1"]!!
        // 100 + 88 (80 × 1.1 за серию 4 дня) + 56 (бонус уровня:
        // XP 127 ≥ 109 → level 2) - 20 (оценка 3) - 50 (оценка 2) = 174
        assertEquals(174, updated.balance)
        assertEquals(QuestStatus.CLAIMED, updated.diaryStatus)
        // Штрафы записаны в историю с причиной «дневник»
        assertTrue(updated.historyItems.any { it.title.contains("Оценка 3 (дневник)") && it.amount == "-20 ₽" })
        assertTrue(updated.historyItems.any { it.title.contains("Оценка 2 (дневник)") && it.amount == "-50 ₽" })
        // Стрик сброшен штрафами
        assertEquals(0, updated.streakDays)
    }

    @Test
    fun `claimQuestReward diary keeps balance delta equal to sum of new history entries`() {
        // Инвариант сверки: balance — хранимое поле профиля (не леджер),
        // historyItems — аудит-лог. Для дневника сумма новых записей
        // (оценки +50/+30, штрафы -20/-50) обязана равняться дельте баланса,
        // иначе сверка в БД разойдётся.
        val before = createChild().copy(
            balance = 100,
            streakDays = 4,
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryApprovedReward = 80,
            diaryCount5 = 1,
            diaryCount4 = 1,
            diaryCount3 = 1,
            diaryCount2 = 1
        )
        repository.children["user_1"] = before

        viewModel.claimQuestReward("user_1", "diary")

        val after = repository.children["user_1"]!!
        val balanceDelta = after.balance - before.balance
        // Инвариант валиден только для baseline с пустой историей:
        // before.historyItems пуст, значит все записи after — новые.
        // Если фикстуру расширят историей, фильтровать по time/date.
        val historySum = after.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(balanceDelta, historySum)
        // И явно: 100 + 88 (80 × 1.1) + 56 (бонус уровня) - 20 - 50 = 174, дельта = +74
        assertEquals(74, balanceDelta)
    }

    @Test
    fun `claimQuestReward diary does nothing when not READY_TO_CLAIM`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            diaryStatus = QuestStatus.PENDING,
            diaryApprovedReward = 80
        )

        viewModel.claimQuestReward("user_1", "diary")

        val updated = repository.children["user_1"]!!
        assertEquals(100, updated.balance)
        assertEquals(QuestStatus.PENDING, updated.diaryStatus)
        assertTrue(updated.historyItems.isEmpty())
    }

    @Test
    fun `claimQuestReward diary grants xp per grade 5 and 4 only`() {
        // 2 пятёрки (+70 XP) + 1 четвёрка (+45 XP) = 185 XP; 3 и 2 — без XP.
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryApprovedReward = 130, // 2×50 + 1×30
            diaryCount5 = 2,
            diaryCount4 = 1,
            diaryCount3 = 1,
            diaryCount2 = 1,
            // level 4 + multipliers off → XP 185 без level-up (до 214 XP).
            level = 4,
            multipliersEnabled = false,
            xp = 0,
            streakDays = 0
        )

        viewModel.claimQuestReward("user_1", "diary")

        val updated = repository.children["user_1"]!!
        assertEquals(185, updated.xp)
    }

    @Test
    fun `claimQuestReward diary xp is scaled by streak multiplier`() {
        // level 4 (×1.045) × streak 7 (×1.20) → ×1.254: (70 + 45) × 1.254 ≈ 144.
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryApprovedReward = 80, // 1×5 + 1×4
            diaryCount5 = 1,
            diaryCount4 = 1,
            diaryCount3 = 0,
            diaryCount2 = 0,
            level = 4,
            xp = 0,
            streakDays = 7
        )

        viewModel.claimQuestReward("user_1", "diary")

        val updated = repository.children["user_1"]!!
        assertEquals(144, updated.xp)
    }

    @Test
    fun `submitHonestAdmission respects 7 day cooldown`() {
        val today = LocalDate.now()
        repository.children["user_1"] = createChild().copy(
            lastHonestAdmissionDate = today.minusDays(3).toString()
        )

        viewModel.submitHonestAdmission("user_1", "Я разбил вазу")
        assertEquals(QuestStatus.AVAILABLE, repository.children["user_1"]!!.honestAdmissionStatus)

        repository.children["user_1"] = repository.children["user_1"]!!.copy(
            lastHonestAdmissionDate = today.minusDays(8).toString()
        )
        viewModel.submitHonestAdmission("user_1", "Я разбил вазу")
        val updated = repository.children["user_1"]!!
        assertEquals(QuestStatus.PENDING, updated.honestAdmissionStatus)
        assertEquals(today.toString(), updated.lastHonestAdmissionDate)
        assertEquals("Я разбил вазу", updated.honestAdmissionText)
    }

    // ---------- Аватары: покупка и авто-открытие ----------

    @Test
    fun `purchaseAvatar deducts price unlocks and selects avatar`() {
        repository.children["user_1"] = createChild().copy(balance = 100)

        val ok = viewModel.purchaseAvatar("user_1", "ROBOT")

        assertTrue(ok)
        val updated = repository.children["user_1"]!!
        assertEquals(0, updated.balance)
        assertTrue("ROBOT" in updated.unlockedAvatars)
        assertEquals("ROBOT", updated.avatar)
        val entry = updated.historyItems.first()
        assertEquals(HistoryType.AVATAR_PURCHASE, entry.type)
        assertEquals(-100, entry.amountValue)
        assertEquals("ROBOT", entry.arg)
    }

    @Test
    fun `purchaseAvatar fails without enough balance`() {
        repository.children["user_1"] = createChild().copy(balance = 50)

        val ok = viewModel.purchaseAvatar("user_1", "ROBOT")

        assertFalse(ok)
        val updated = repository.children["user_1"]!!
        assertEquals(50, updated.balance)
        assertFalse("ROBOT" in updated.unlockedAvatars)
        assertTrue(updated.historyItems.isEmpty())
    }

    @Test
    fun `purchaseAvatar on already unlocked avatar only selects it`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            unlockedAvatars = listOf("ROBOT")
        )

        val ok = viewModel.purchaseAvatar("user_1", "ROBOT")

        assertTrue(ok)
        val updated = repository.children["user_1"]!!
        assertEquals(100, updated.balance) // баланс не тронут
        assertEquals("ROBOT", updated.avatar)
        assertTrue(updated.historyItems.isEmpty())
    }

    @Test
    fun `purchaseAvatar rejects free and unknown avatars`() {
        repository.children["user_1"] = createChild().copy(balance = 100)

        assertFalse(viewModel.purchaseAvatar("user_1", "CAT")) // Free — не покупается
        assertFalse(viewModel.purchaseAvatar("user_1", "NOPE")) // неизвестный id

        val updated = repository.children["user_1"]!!
        assertEquals(100, updated.balance)
        assertTrue(updated.historyItems.isEmpty())
    }

    @Test
    fun `updateAvatar sets avatar string without touching balance`() {
        repository.children["user_1"] = createChild().copy(balance = 100)

        viewModel.updateAvatar("user_1", "PANDA")

        val updated = repository.children["user_1"]!!
        assertEquals("PANDA", updated.avatar)
        assertEquals(100, updated.balance)
    }

    @Test
    fun `syncAchievementThemesOnLoad unlocks achievement-bound avatars`() {
        val claimed = AchievementConfig(
            title = "Победитель Монстров",
            description = "",
            targetCount = 5,
            reward = 30,
            branch = AchievementBranch.WARDROBE,
            isClaimed = true
        )
        // syncAchievementThemesOnLoad мутирует AppSharedState.children,
        // а не repository — заполняем оба, чтобы не зависеть от порядка тестов.
        val child = createChild().copy(achievements = listOf(claimed))
        repository.children["user_1"] = child
        AppSharedState.children["user_1"] = child
        try {
            viewModel.syncAchievementThemesOnLoad()

            val updated = AppSharedState.children["user_1"]!!
            assertTrue("ASTRONAUT" in updated.unlockedAvatars)
            // Бесплатные аватары не должны пропасть
            assertTrue("CAT" in updated.unlockedAvatars)
            assertTrue("PANDA" in updated.unlockedAvatars)
        } finally {
            AppSharedState.children.remove("user_1")
        }
    }

    @Test
    fun `new child profile has four free avatars unlocked by default`() {
        val child = createChild()
        assertEquals(
            setOf("CAT", "PANDA", "ALIEN", "UNICORN"),
            child.unlockedAvatars.toSet()
        )
    }

    // ---------- Геймификация: XP, уровни, множители ----------

    @Test
    fun `claimQuestReward closet grants xp and level up bonus`() {
        // level 1, xp=100 (до 109 не хватает 9) + 25 XP за шкаф → level 2.
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            wardrobeReward = 50,
            level = 1,
            xp = 100
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        // coins=50 (mult 1.0) + level-up bonus 56 (40 + 2*8) = +106 → 206
        assertEquals(206, updated.balance)
        assertEquals(2, updated.level)
        assertEquals(16, updated.xp) // 100+25-109
        assertEquals(QuestStatus.CLAIMED, updated.closetStatus)
        // Инвариант: дельта баланса = сумма historyItems
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(106, historySum)
        assertTrue(updated.historyItems.any { it.type == HistoryType.LEVEL_UP && it.amountValue == 56 })
        assertTrue(updated.historyItems.any { it.type == HistoryType.REWARD_MULTIPLIER })
    }

    @Test
    fun `claimQuestReward closet applies level and streak multiplier`() {
        // level 10 (×1.135) × streak 7 (×1.20) → mult 1.362.
        // coins = round(50*1.362)=68, xp = round(25*1.362)=34.
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            wardrobeReward = 50,
            level = 10,
            xp = 0,
            streakDays = 7
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        assertEquals(168, updated.balance) // 100 + 68, level-up нет (475 XP нужно)
        assertEquals(34, updated.xp)
        assertEquals(10, updated.level)
        val reward = updated.historyItems.first { it.type == HistoryType.QUEST_REWARD }
        assertEquals(68, reward.amountValue)
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(68, historySum)
    }

    @Test
    fun `claimBonusQuestReward keeps balance delta equal to history sum with gamification`() {
        val quest = BonusQuest(
            title = "Помочь",
            reward = 40,
            status = QuestStatus.READY_TO_CLAIM
        )
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            bonusQuests = listOf(quest),
            level = 6, // ×1.075
            streakDays = 3 // ×1.10 → mult 1.1825
        )

        viewModel.claimBonusQuestReward("user_1", quest.id)

        val updated = repository.children["user_1"]!!
        // coins = round(40*1.1825)=47, xp = round(30*1.1825)=35
        assertEquals(147, updated.balance)
        assertEquals(35, updated.xp)
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(47, historySum)
    }

    // ---------- Бонусный сундук ----------

    @Test
    fun `claimQuestReward closet with bonus chest coins keeps balance delta equal to history sum`() {
        viewModel.bonusChestRoller = { _ -> GamificationEngine.BonusChestReward.Coins(30) }
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            wardrobeReward = 50,
            level = 1,
            xp = 0
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        // 100 + 50 (квест) + 30 (сундук) = 180; XP 25 — без level-up.
        assertEquals(180, updated.balance)
        assertEquals(25, updated.xp)
        assertEquals(1, updated.level)
        val chestEntry = updated.historyItems.first { it.type == HistoryType.BONUS_CHEST }
        assertEquals(30, chestEntry.amountValue)
        assertEquals("+30 ₽", chestEntry.amount)
        // Инвариант: дельта баланса = сумма historyItems
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(80, historySum)
        assertEquals(
            GamificationEngine.BonusChestReward.Coins(30),
            viewModel.bonusChestEvent.value
        )
    }

    @Test
    fun `claimQuestReward closet with bonus chest xp triggers level up and keeps invariant`() {
        viewModel.bonusChestRoller = { _ -> GamificationEngine.BonusChestReward.Xp(15) }
        // level 1, xp=80: квест +25 XP → 105 < 109 (level-up нет).
        // Сундук +15 XP → 120 ≥ 109 → level 2, остаток 11, coinBonus 56 (40+2*8).
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            wardrobeReward = 50,
            level = 1,
            xp = 80
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        // 100 + 50 (квест) + 56 (level-up от XP сундука) = 206
        assertEquals(206, updated.balance)
        assertEquals(2, updated.level)
        assertEquals(11, updated.xp) // 80 + 25 + 15 - 109
        val chestEntry = updated.historyItems.first { it.type == HistoryType.BONUS_CHEST }
        assertNull(chestEntry.amountValue) // XP-запись не денежная
        assertEquals("15", chestEntry.arg)
        assertTrue(updated.historyItems.any { it.type == HistoryType.LEVEL_UP && it.amountValue == 56 })
        // Инвариант: дельта баланса = сумма historyItems
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(106, historySum)
        assertEquals(
            GamificationEngine.BonusChestReward.Xp(15),
            viewModel.bonusChestEvent.value
        )
        // Level-up от XP сундука тоже уходит в UI-событие.
        assertEquals(2, viewModel.levelUpEvent.value)
    }

    @Test
    fun `claimBonusQuestReward with bonus chest coins keeps invariant`() {
        viewModel.bonusChestRoller = { _ -> GamificationEngine.BonusChestReward.Coins(20) }
        val quest = BonusQuest(
            title = "Помочь",
            reward = 40,
            status = QuestStatus.READY_TO_CLAIM
        )
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            bonusQuests = listOf(quest),
            level = 1,
            xp = 0
        )

        viewModel.claimBonusQuestReward("user_1", quest.id)

        val updated = repository.children["user_1"]!!
        // 100 + 40 (квест) + 20 (сундук) = 160
        assertEquals(160, updated.balance)
        assertTrue(updated.historyItems.any { it.type == HistoryType.BONUS_CHEST && it.amountValue == 20 })
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(60, historySum)
    }

    // ---------- Идеальный день ----------

    private fun perfectDayChild(id: String = "user_1") = createChild(id).copy(
        closetStatus = QuestStatus.READY_TO_CLAIM,
        roomStatus = QuestStatus.READY_TO_CLAIM,
        closetResetDate = LocalDate.now().toString(),
        roomResetDate = LocalDate.now().toString(),
        wardrobeReward = 50,
        roomReward = 50,
        level = 1,
        xp = 0,
        streakDays = 0
    )

    @Test
    fun `claiming closet and room same day awards perfect day once with invariant`() {
        repository.children["user_1"] = perfectDayChild()

        viewModel.claimQuestReward("user_1", "closet")
        // После первого квеста идеального дня ещё нет — комната не заклеймлена.
        assertEquals(0, repository.children["user_1"]!!.perfectDaysStreak)

        viewModel.claimQuestReward("user_1", "room")

        val updated = repository.children["user_1"]!!
        // 100 + 50 (шкаф) + 50 (комната) + 25 (идеальный день) = 225
        assertEquals(225, updated.balance)
        assertEquals(1, updated.perfectDaysStreak)
        assertEquals(LocalDate.now().toString(), updated.lastPerfectDayDate)
        // XP: 25 + 25 (квесты) + 15 (идеальный день) = 65, level-up нет.
        assertEquals(65, updated.xp)
        assertEquals(1, updated.level)
        val perfectEntry = updated.historyItems.first { it.type == HistoryType.PERFECT_DAY }
        assertEquals(25, perfectEntry.amountValue)
        // Инвариант: дельта баланса = сумма historyItems
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(125, historySum)
    }

    @Test
    fun `perfect day is not awarded twice on the same day`() {
        repository.children["user_1"] = perfectDayChild().copy(
            lastPerfectDayDate = LocalDate.now().toString(),
            perfectDaysStreak = 1
        )

        viewModel.claimQuestReward("user_1", "closet")
        viewModel.claimQuestReward("user_1", "room")

        val updated = repository.children["user_1"]!!
        // 100 + 50 + 50 = 200 — без повторного +25.
        assertEquals(200, updated.balance)
        assertEquals(1, updated.perfectDaysStreak)
        assertFalse(updated.historyItems.any { it.type == HistoryType.PERFECT_DAY })
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(100, historySum)
    }

    @Test
    fun `perfect day streak grows on consecutive days and resets after a miss`() {
        val today = LocalDate.now()
        // Вчера был идеальный день → серия 2 → 3.
        repository.children["user_1"] = perfectDayChild().copy(
            lastPerfectDayDate = today.minusDays(1).toString(),
            perfectDaysStreak = 2
        )
        viewModel.claimQuestReward("user_1", "closet")
        viewModel.claimQuestReward("user_1", "room")
        assertEquals(3, repository.children["user_1"]!!.perfectDaysStreak)

        // Пропуск дня: lastPerfectDayDate позавчера → серия сбрасывается в 1.
        repository.children["user_2"] = perfectDayChild("user_2").copy(
            lastPerfectDayDate = today.minusDays(2).toString(),
            perfectDaysStreak = 5
        )
        viewModel.claimQuestReward("user_2", "closet")
        viewModel.claimQuestReward("user_2", "room")
        val missed = repository.children["user_2"]!!
        assertEquals(1, missed.perfectDaysStreak)
        assertEquals(today.toString(), missed.lastPerfectDayDate)
    }

    @Test
    fun `perfect day streak boosts quest xp but not coins`() {
        // Серия 3 → +10% XP: базовые 25 XP → round(25*1.1)=28, монеты без изменений.
        repository.children["user_1"] = perfectDayChild().copy(
            perfectDaysStreak = 3,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            roomStatus = QuestStatus.AVAILABLE
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        assertEquals(150, updated.balance) // 100 + 50, монеты не раздуваются
        assertEquals(28, updated.xp)
        assertEquals(3, updated.perfectDaysStreak) // комната не выполнена — серия не меняется
    }

    // ---------- Рамки аватара ----------

    @Test
    fun `purchaseFrame deducts balance unlocks and selects frame with history invariant`() {
        repository.children["user_1"] = createChild().copy(balance = 200)

        val ok = viewModel.purchaseFrame("user_1", "NEON")

        assertTrue(ok)
        val updated = repository.children["user_1"]!!
        assertEquals(80, updated.balance) // 200 - 120
        assertTrue("NEON" in updated.unlockedFrames)
        assertEquals("NEON", updated.selectedFrameId)
        val entry = updated.historyItems.first { it.type == HistoryType.FRAME_PURCHASE }
        assertEquals(-120, entry.amountValue)
        assertEquals("NEON", entry.arg)
        // Инвариант: дельта баланса = сумма historyItems
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(-120, historySum)
    }

    @Test
    fun `purchaseFrame fails without enough balance`() {
        repository.children["user_1"] = createChild().copy(balance = 100)

        assertFalse(viewModel.purchaseFrame("user_1", "NEON")) // 120 > 100

        val updated = repository.children["user_1"]!!
        assertEquals(100, updated.balance)
        assertEquals("NONE", updated.selectedFrameId)
        assertFalse(updated.historyItems.any { it.type == HistoryType.FRAME_PURCHASE })
    }

    @Test
    fun `level frames unlock by level and cannot be purchased`() {
        // GUARDIAN открывается с 10 уровня.
        repository.children["user_1"] = createChild().copy(level = 10, balance = 500)
        viewModel.selectFrame("user_1", "GUARDIAN")
        assertEquals("GUARDIAN", repository.children["user_1"]!!.selectedFrameId)

        // На 9 уровне — нет.
        repository.children["user_2"] = createChild("user_2").copy(level = 9, balance = 500)
        viewModel.selectFrame("user_2", "GUARDIAN")
        assertEquals("NONE", repository.children["user_2"]!!.selectedFrameId)

        // Рамки за уровень не покупаются за монеты.
        assertFalse(viewModel.purchaseFrame("user_1", "ROYAL_GOLD"))
        assertFalse(viewModel.purchaseFrame("user_1", "GUARDIAN"))
        assertEquals(500, repository.children["user_1"]!!.balance)
    }

    // ---------- Настройки экономики ----------

    @Test
    fun `updateEconomySettings mirrors base rewards into quest rewards`() {
        repository.children["user_1"] = createChild()

        viewModel.updateEconomySettings(
            userId = "user_1",
            baseClosetReward = 80,
            baseRoomReward = 60,
            baseDiaryFiveReward = 70,
            multipliersEnabled = false,
            bonusChestChancePercent = 10,
            weeklyEarnLimit = 0
        )

        val updated = repository.children["user_1"]!!
        assertEquals(80, updated.baseClosetReward)
        assertEquals(80, updated.wardrobeReward) // зеркало для клейма/карточек
        assertEquals(60, updated.baseRoomReward)
        assertEquals(60, updated.roomReward)
        assertEquals(70, updated.baseDiaryFiveReward)
        assertFalse(updated.multipliersEnabled)
        assertEquals(10, updated.bonusChestChancePercent)
        assertEquals(0, updated.weeklyEarnLimit)
    }

    @Test
    fun `economy base reward and disabled multipliers give flat quest payout`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            wardrobeReward = 80,
            baseClosetReward = 80,
            level = 20,
            streakDays = 30,
            multipliersEnabled = false,
            xp = 0
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        // Множитель 1.0: ровно базовая награда, без бонусов уровня/серии.
        assertEquals(180, updated.balance)
        val entry = updated.historyItems.first { it.type == HistoryType.QUEST_REWARD }
        assertEquals(80, entry.amountValue)
        // XP начисляется как обычно (без perfectDay-бонуса).
        assertEquals(25, updated.xp)
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(80, historySum)
    }

    @Test
    fun `bonusChestChancePercent zero disables chest even if roller would pay`() {
        viewModel.bonusChestRoller = { _ -> GamificationEngine.BonusChestReward.Coins(30) }
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            wardrobeReward = 50,
            bonusChestChancePercent = 0,
            level = 1,
            xp = 0
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        assertEquals(150, updated.balance) // только награда квеста
        assertFalse(updated.historyItems.any { it.type == HistoryType.BONUS_CHEST })
        assertNull(viewModel.bonusChestEvent.value)
    }

    @Test
    fun `weekly earn limit caps coins and keeps balance delta equal to history sum`() {
        val priorEarning = HistoryData(
            title = "Награда получена: Ранее",
            time = "10:00",
            date = getCurrentDateString(),
            amount = "+80 ₽",
            isIncome = true,
            category = "Квесты",
            type = HistoryType.QUEST_REWARD,
            amountValue = 80
        )
        repository.children["user_1"] = createChild().copy(
            balance = 200,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            wardrobeReward = 50,
            weeklyEarnLimit = 100,
            historyItems = listOf(priorEarning),
            level = 1,
            xp = 0
        )

        viewModel.claimQuestReward("user_1", "closet")

        val updated = repository.children["user_1"]!!
        // Уже заработано 80 из 100 → начисляется только 20, XP — полностью.
        assertEquals(220, updated.balance)
        assertEquals(25, updated.xp)
        val rewardEntry = updated.historyItems.first { it.type == HistoryType.QUEST_REWARD }
        assertEquals(20, rewardEntry.amountValue)
        assertTrue(updated.historyItems.any { it.type == HistoryType.WEEKLY_LIMIT })
        // Инвариант: дельта баланса за клейм (20) = сумма новых записей
        // (80 — заработок до клейма, уже был в балансе 200).
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(20, historySum - priorEarning.amountValue!!)
    }

    // ---------- Жетоны честности и подарки родителю ----------

    @Test
    fun `submitHonestAdmission grants token and 15 XP with level-up invariant`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            xp = 100,
            level = 1,
            honestyTokens = 0
        )

        viewModel.submitHonestAdmission("user_1", "Я разбил вазу")

        val updated = repository.children["user_1"]!!
        assertEquals(1, updated.honestyTokens)
        assertEquals(QuestStatus.PENDING, updated.honestAdmissionStatus)
        // xp 100 + 15 = 115 ≥ 109 → level 2, остаток 6, coinBonus 56 (40+2*8).
        assertEquals(2, updated.level)
        assertEquals(6L, updated.xp)
        assertEquals(156, updated.balance) // 100 + 56 coinBonus
        val levelUpEntry = updated.historyItems.first { it.type == HistoryType.LEVEL_UP }
        assertEquals(56, levelUpEntry.amountValue)
        // Инвариант: дельта баланса (56) = сумма записей истории.
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(56, historySum)
    }

    @Test
    fun `submitHonestAdmission without level-up keeps balance and grants 15 XP`() {
        repository.children["user_1"] = createChild().copy(
            balance = 100,
            xp = 0,
            level = 1
        )

        viewModel.submitHonestAdmission("user_1", "Признание")

        val updated = repository.children["user_1"]!!
        assertEquals(1, updated.honestyTokens)
        assertEquals(15L, updated.xp)
        assertEquals(1, updated.level)
        assertEquals(100, updated.balance) // без level-up баланс не меняется
        assertFalse(updated.historyItems.any { it.type == HistoryType.LEVEL_UP })
    }

    @Test
    fun `sendParentGift deducts coins adds pending gift and keeps invariant`() {
        repository.children["user_1"] = createChild().copy(balance = 100)

        val ok = viewModel.sendParentGift("user_1", "HUGS")

        assertTrue(ok)
        val updated = repository.children["user_1"]!!
        assertEquals(50, updated.balance) // 100 - 50 (HUGS)
        assertEquals(1, updated.pendingParentGifts.size)
        assertEquals("HUGS", updated.pendingParentGifts[0].giftTypeId)
        val entry = updated.historyItems.first { it.type == HistoryType.PARENT_GIFT }
        assertEquals(-50, entry.amountValue)
        // Инвариант: дельта баланса (-50) = сумма записей истории.
        val historySum = updated.historyItems.sumOf { it.amountValue ?: 0 }
        assertEquals(-50, historySum)
        assertEquals("HUGS", viewModel.parentGiftEvent.value)
    }

    @Test
    fun `sendParentGift rejected when balance is insufficient`() {
        repository.children["user_1"] = createChild().copy(balance = 30)

        val ok = viewModel.sendParentGift("user_1", "MOVIE_NIGHT") // 120 ₽

        assertFalse(ok)
        val updated = repository.children["user_1"]!!
        assertEquals(30, updated.balance)
        assertTrue(updated.pendingParentGifts.isEmpty())
        assertFalse(updated.historyItems.any { it.type == HistoryType.PARENT_GIFT })
        assertNull(viewModel.parentGiftEvent.value)
    }

    @Test
    fun `useParentGift removes gift from pending list`() {
        repository.children["user_1"] = createChild().copy(balance = 100)
        viewModel.sendParentGift("user_1", "THANK_YOU")
        val giftId = repository.children["user_1"]!!.pendingParentGifts.single().id

        viewModel.useParentGift("user_1", giftId)

        assertTrue(repository.children["user_1"]!!.pendingParentGifts.isEmpty())
    }
}
