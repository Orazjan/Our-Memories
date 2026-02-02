package com.example.ourmemories

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.ourmemories.Repositories.AddMemoryRepository
import com.example.ourmemories.Utils.ImageHandler
import com.example.ourmemories.ViewModels.AddMemoryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMemoryViewModelTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockRepository = mockk<AddMemoryRepository>(relaxed = true)
    private val mockImageHandler = mockk<ImageHandler>(relaxed = true)
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)

    private val mockUri = mockk<Uri>()

    private lateinit var viewModel: AddMemoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { mockApplication.applicationContext } returns mockContext
        every { mockContext.getString(any()) } returns "Test String"
        every { mockContext.getString(any(), *anyVararg()) } returns "Test String with Args"

        viewModel =
            AddMemoryViewModel(mockApplication, mockRepository, mockImageHandler, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addImages extracts date on first load`() = runTest {
        val testDate = 123456789L
        coEvery { mockImageHandler.extractDateFromImage(any()) } returns testDate

        val uris = listOf(mockUri)

        viewModel.addImages(uris)

        assertEquals(testDate, viewModel.eventDate.value)
        assertEquals(1, viewModel.selectedUris.value?.size)

        coVerify { mockImageHandler.extractDateFromImage(mockUri) }
    }

    @Test
    fun `saveMemory uploads images and saves data`() = runTest {
        val uid = "test_user_id"
        every { mockRepository.getCurrentUserUid() } returns uid
        coEvery { mockImageHandler.compressImage(any()) } returns ByteArray(10)
        coEvery { mockRepository.uploadImageBytes(any(), any()) } returns "http://fake.url"

        viewModel.addImages(listOf(mockUri))

        viewModel.saveMemory("Title", "Desc")

        coVerify { mockRepository.uploadImageBytes(any(), uid) }
        coVerify { mockRepository.addMemory(any()) }
        verify { mockRepository.incrementUserPoints(uid, 5L) }

        assertEquals(true, viewModel.saveSuccess.value)
    }

    @Test
    fun `saveMemory fails validation if title empty`() = runTest {
        viewModel.addImages(listOf(mockUri))

        viewModel.saveMemory("", "Desc")

        coVerify(exactly = 0) { mockRepository.addMemory(any()) }
        assertEquals("Test String", viewModel.toastMessage.value)
    }
}