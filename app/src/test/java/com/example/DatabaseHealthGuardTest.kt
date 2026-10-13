package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseHealthGuard
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseHealthGuardTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `verifyAndRepairDatabase initializes clean profile when empty`() = runBlocking {
        val dao = db.finMoneyDao()
        
        // Initially empty
        var debitsCount = dao.getDebitsCount()
        assertTrue(debitsCount == 0)

        // Run Health Guard
        DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = false)

        // Verify clean state without simulated fake data
        debitsCount = dao.getDebitsCount()
        val userProfile = dao.getUserProfileDirect()

        assertTrue("Health Guard should keep clean 0 debits count without simulated fake data", debitsCount == 0)
        assertNotNull("Health Guard should initialize default clean user profile", userProfile)
    }
}

