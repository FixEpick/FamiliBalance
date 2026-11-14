package com.example.familibalance

import android.app.Application
import com.example.familibalance.data.repository.FamilyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var familyRepository: FamilyRepository
    private lateinit var child: ChildProfileState
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        familyRepository = mockk(relaxed = true)
        child = ChildProfileState(
            id = "user_1",
            name = "Test",
            avatar = "🧒",
            initialBalance = 100
        )

        every { familyRepository.isSynced } returns MutableStateFlow(true)
        every { familyRepository.childrenSnapshot() } returns mapOf(child.id to child)
        every { familyRepository.selectedChildId } returns child.id
        every { familyRepository.getChild(child.id) } returns child
        every { familyRepository.getChild("unknown") } returns null
        every { familyRepository.getAllChildren() } returns listOf(child)

        viewModel = MainViewModel(application, familyRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buyScreenTime spends balance, adds minutes and history, then syncs`() {
        viewModel.buyScreenTime(child.id, cost = 50, minutes = 30)

        assertEquals(50, child.balance)
        assertEquals(30, child.boughtMinutesToday)
        assertEquals(1, child.historyItems.size)
        assertEquals("Экранное время", child.historyItems[0].category)
        verify(exactly = 1) { familyRepository.saveAndSync() }
    }

    @Test
    fun `submitHonestAdmission sets status to PENDING and syncs`() {
        viewModel.submitHonestAdmission(child.id)

        assertEquals(QuestStatus.PENDING, child.honestAdmissionStatus)
        verify(exactly = 1) { familyRepository.saveAndSync() }
    }

    @Test
    fun `buyScreenTime does nothing for unknown child`() {
        viewModel.buyScreenTime("unknown", cost = 50, minutes = 30)

        assertEquals(100, child.balance)
        verify(exactly = 0) { familyRepository.saveAndSync() }
    }
}
