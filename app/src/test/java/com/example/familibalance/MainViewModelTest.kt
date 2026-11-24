package com.example.familibalance

import com.example.familibalance.data.model.*
import com.example.familibalance.data.repository.FamilyRepository
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
        assertEquals(180, updated.balance)
        assertEquals(QuestStatus.CLAIMED, updated.diaryStatus)
        assertEquals(0, updated.diaryCount5)
        assertEquals(0, updated.diaryCount4)
        // Только записи по оценкам — без сводной «Награда получена: Итоги недели»
        assertTrue(updated.historyItems.none { it.title.contains("Итоги недели") })
        assertTrue(updated.historyItems.none { it.title.startsWith("Награда получена") })
        assertEquals(2, updated.historyItems.count { it.category == "Дневник" })
        assertTrue(updated.historyItems.any { it.title == "Оценка 5" && it.amount == "+50 ₽" })
        assertTrue(updated.historyItems.any { it.title == "Оценка 4" && it.amount == "+30 ₽" })
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
        // 100 + 80 (награда) - 20 (оценка 3) - 50 (оценка 2) = 110
        assertEquals(110, updated.balance)
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
        // И явно: 100 + 80 - 20 - 50 = 110, дельта = +10
        assertEquals(10, balanceDelta)
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
}
