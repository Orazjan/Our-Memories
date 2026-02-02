package com.example.ourmemories

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.ourmemories.Models.User
import com.example.ourmemories.Repositories.MainRepository
import com.example.ourmemories.ViewModels.MainViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class MainViewModelTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val mockRepository = mockk<MainRepository>(relaxed = true)
    private val mockApplication = mockk<Application>()
    private val mockContext = mockk<Context>(relaxed = true)

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        every { mockApplication.applicationContext } returns mockContext

        every { mockContext.getString(any(), any()) } returns "Bonus Toast"
        every { mockContext.getString(any()) } returns "String Resource"

        viewModel = MainViewModel(mockApplication, mockRepository)
    }

    @Test
    fun `checkDailyBonus adds points if bonus was not collected today`() {

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        val user = User(
            uid = "test_user_123", lastDailyDate = yesterday, treePoints = 100
        )

        viewModel.checkDailyBonus(user)


        verify(exactly = 1) {
            mockRepository.updateTreePoints(
                uid = "test_user_123",
                pointsToAdd = 10L,
                lastDailyDate = any()
            )
        }

        assertEquals("Bonus Toast", viewModel.toastMessage.value)
    }

    @Test
    fun `checkDailyBonus does NOT add points if collected today`() {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val user = User(
            uid = "test_user_123", lastDailyDate = todayStart
        )

        viewModel.checkDailyBonus(user)

        verify(exactly = 0) {
            mockRepository.updateTreePoints(any(), any(), any())
        }
    }

    @Test
    fun `calculateDays returns zero for 0 timestamp`() {
        val result = viewModel.calculateDays(0)
        assertEquals(0, result)
    }

    @Test
    fun `calculateDays returns correct days count`() {
        val now = System.currentTimeMillis()
        val fiveDaysAgo = now - (5L * 24 * 60 * 60 * 1000)

        val result = viewModel.calculateDays(fiveDaysAgo)

        assert(result >= 4)
    }
}