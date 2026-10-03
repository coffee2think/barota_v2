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
    @Test fun versionOneMigrationPreservesHistoryAndSavedPairsSurviveReopening() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val legacy = context.openOrCreateDatabase(name, 0, null)
        legacy.execSQL("CREATE TABLE station_usage (station_id TEXT PRIMARY KEY, origin_count INTEGER, destination_count INTEGER, last_used_at INTEGER)")
        legacy.execSQL("CREATE TABLE station_pair_usage (origin_id TEXT, destination_id TEXT, usage_count INTEGER, last_used_at INTEGER, PRIMARY KEY(origin_id, destination_id))")
        legacy.execSQL("INSERT INTO station_usage VALUES ('a', 2, 3, 10)")
        legacy.execSQL("INSERT INTO station_pair_usage VALUES ('a', 'b', 4, 20)")
        legacy.version = 1
        legacy.close()
        val db = StationUsageDatabase(context, name)
        try {
            val repo = SqliteStationUsageRepository.forDatabase(db)
            assertEquals(5L, repo.observeUsage().first().getValue("a").totalCount)
            assertEquals(4L, repo.observePairs().first().single().count)
            repo.savePair("a", "b", 30)
            repo.savePair("a", "b", 40)
            assertEquals(30L, repo.observeSavedPairs().first().single().savedAt)
            repo.removeSavedPair("a", "b")
            assertTrue(repo.observeSavedPairs().first().isEmpty())
            repo.savePair("a", "b", 50)
            repo.savePair("b", "a", 60)
            db.close()
            val reopened = StationUsageDatabase(context, name)
            try {
                val restored = SqliteStationUsageRepository.forDatabase(reopened)
                assertEquals(2, restored.observeSavedPairs().first().size)
                assertEquals(4L, restored.getPairs("a").single().count)
            } finally { reopened.close() }
        } finally { db.close(); context.deleteDatabase(name) }
    }
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
