package com.example.ourmemories

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.ourmemories.Models.User
import com.example.ourmemories.ViewModels.MainViewModel
import com.example.ourmemories.ViewModels.TreeInfo
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @MockK
    lateinit var application: Application

    @MockK
    lateinit var firebaseAuth: FirebaseAuth

    @MockK
    lateinit var firestore: FirebaseFirestore

    private lateinit var viewModel: MainViewModel
    private val testUid = "test_user_123"

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(FirebaseFirestore::class)
        mockkStatic(FirebaseAuth::class)

        every { FirebaseFirestore.getInstance() } returns firestore
        every { FirebaseAuth.getInstance() } returns firebaseAuth

        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns testUid
        every { firebaseAuth.currentUser } returns mockFirebaseUser

        val mockCollection = mockk<CollectionReference>()
        val mockDocument = mockk<DocumentReference>()
        every { firestore.collection("users") } returns mockCollection
        every { mockCollection.document(any()) } returns mockDocument
        every { mockDocument.addSnapshotListener(any()) } returns mockk()

        viewModel = MainViewModel(application)
    }

    @Test
    fun `calculateDays returns correct count for 5 days ago`() {
        val fiveDaysAgo = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L)
        val result = viewModel.calculateDays(fiveDaysAgo)
        assertEquals(5L, result)
    }

    @Test
    fun `calculateDays returns 0 for future date`() {
        val tomorrow = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
        val result = viewModel.calculateDays(tomorrow)
        assertEquals(0L, result)
    }

    @Test
    fun `treeInfo updates reactively when user points change`() {
        val observer = mockk<Observer<TreeInfo?>>(relaxed = true)
        viewModel.treeInfo.observeForever(observer)

        val userWithPoints = User(uid = testUid, treePoints = 150)

        val field = viewModel.javaClass.getDeclaredField("_currentUser")
        field.isAccessible = true
        (field.get(viewModel) as androidx.lifecycle.MutableLiveData<User?>).value = userWithPoints

        verify {
            observer.onChanged(match {
                it?.levelName == "Крепкое Дерево" && it.maxPoints == 200
            })
        }
    }

    @Test
    fun `daysTogether updates reactively when relationshipDate changes`() {
        val observer = mockk<Observer<Long>>(relaxed = true)
        viewModel.daysTogether.observeForever(observer)

        val tenDaysAgo = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L)
        val user = User(uid = testUid, relationshipDate = tenDaysAgo)

        val field = viewModel.javaClass.getDeclaredField("_currentUser")
        field.isAccessible = true
        (field.get(viewModel) as androidx.lifecycle.MutableLiveData<User?>).value = user

        verify { observer.onChanged(10L) }
    }

    @Test
    fun `updateStatus calls firestore with correct parameters`() {
        val mockDocument = firestore.collection("users").document(testUid)
        val statusTask = mockk<Task<Void>>()
        every { mockDocument.update(any<Map<String, Any>>()) } returns statusTask
        every { statusTask.addOnFailureListener(any()) } returns statusTask

        viewModel.updateStatus("❤️")

        verify { mockDocument.update(match { it["status"] == "❤️" }) }
    }

    @Test
    fun `onToastShown clears the toast message`() {
        val observer = mockk<Observer<String?>>(relaxed = true)
        viewModel.toastMessage.observeForever(observer)
        viewModel.onToastShown()
        verify { observer.onChanged(null) }
    }
}
