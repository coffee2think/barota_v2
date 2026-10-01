package com.gilbit.barota.data.repository

import androidx.test.platform.app.InstrumentationRegistry
import com.gilbit.barota.data.model.StationUsageRole
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqliteStationUsageRepositoryTest {
    @Test fun historySurvivesReopeningAndConcurrentUpdatesDoNotLoseCounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "station-usage-test-${UUID.randomUUID()}.db"
        val database = StationUsageDatabase(context, name)
        val repository = SqliteStationUsageRepository.forDatabase(database)
        try {
            assertTrue(repository.observeUsage().first().isEmpty())
            (1..20).map { index -> async {
                repository.recordSelection("gangnam", StationUsageRole.ORIGIN, index.toLong())
            } }.awaitAll()
            repository.recordSelection("gangnam", StationUsageRole.DESTINATION, 100)
            repository.recordPair("gangnam", "seoul", 101)
            repository.recordPair("gangnam", "seoul", 102)
            repository.recordPair("seoul", "gangnam", 103)
            database.close()
            val reopened = StationUsageDatabase(context, name)
            try {
                val restored = SqliteStationUsageRepository.forDatabase(reopened)
                val usage = restored.observeUsage().first().getValue("gangnam")
                assertEquals(20L, usage.originCount)
                assertEquals(1L, usage.destinationCount)
                assertEquals(21L, usage.totalCount)
                assertEquals(100L, usage.lastUsedAt)
                assertEquals(2L, restored.getPairs("gangnam").single().count)
                assertEquals(102L, restored.getPairs("gangnam").single().lastUsedAt)
                assertEquals(1L, restored.getPairs("seoul").single().count)
            } finally {
                reopened.close()
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
