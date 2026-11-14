package com.example.familibalance

import com.example.familibalance.data.repository.FamilyRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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

        viewModel.submitHonestAdmission("user_1")

        assertEquals(
            QuestStatus.PENDING,
            repository.children["user_1"]!!.honestAdmissionStatus
        )
    }

    @Test
    fun `checkResets resets daily quests and custom quests via copy`() {
        val staleChild = createChild().copy(
            lastDailyResetDate = LocalDate.now().minusDays(1).toString(),
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
}
