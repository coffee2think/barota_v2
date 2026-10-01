package com.gilbit.barota.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gilbit.barota.data.model.StationPairUsage
import com.gilbit.barota.data.model.StationUsage
import com.gilbit.barota.data.model.StationUsageRole
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class StationUsageDatabase(context: Context, name: String = "station-usage.db") :
    SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE station_usage (
                station_id TEXT PRIMARY KEY NOT NULL,
                origin_count INTEGER NOT NULL DEFAULT 0,
                destination_count INTEGER NOT NULL DEFAULT 0,
                last_used_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE station_pair_usage (
                origin_id TEXT NOT NULL,
                destination_id TEXT NOT NULL,
                usage_count INTEGER NOT NULL DEFAULT 0,
                last_used_at INTEGER NOT NULL,
                PRIMARY KEY (origin_id, destination_id)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema versions must add explicit, non-destructive migrations here.
        error("Missing station usage migration: $oldVersion -> $newVersion")
    }
}

class SqliteStationUsageRepository private constructor(
    private val database: StationUsageDatabase,
) : StationUsageRepository {
    @Inject constructor(@ApplicationContext context: Context) : this(StationUsageDatabase(context))

    companion object {
        fun forDatabase(database: StationUsageDatabase) = SqliteStationUsageRepository(database)
    }

    private val mutex = Mutex()
    private val usage = MutableStateFlow<Map<String, StationUsage>>(emptyMap())

    override fun observeUsage() = flow {
        withContext(Dispatchers.IO) { mutex.withLock { usage.value = readUsage() } }
        emitAll(usage)
    }

    override suspend fun recordSelection(stationId: String, role: StationUsageRole, usedAt: Long) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = database.writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL(
                        "INSERT OR IGNORE INTO station_usage VALUES (?, 0, 0, ?)",
                        arrayOf<Any>(stationId, usedAt),
                    )
                    val column = if (role == StationUsageRole.ORIGIN) "origin_count" else "destination_count"
                    db.execSQL(
                        "UPDATE station_usage SET $column = $column + 1, last_used_at = MAX(last_used_at, ?) WHERE station_id = ?",
                        arrayOf<Any>(usedAt, stationId),
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                usage.value = readUsage()
            }
        }
    }

    override suspend fun recordPair(originId: String, destinationId: String, usedAt: Long) {
        require(originId != destinationId)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = database.writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL(
                        "INSERT OR IGNORE INTO station_pair_usage VALUES (?, ?, 0, ?)",
                        arrayOf<Any>(originId, destinationId, usedAt),
                    )
                    db.execSQL(
                        "UPDATE station_pair_usage SET usage_count = usage_count + 1, last_used_at = MAX(last_used_at, ?) WHERE origin_id = ? AND destination_id = ?",
                        arrayOf<Any>(usedAt, originId, destinationId),
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    override suspend fun getPairs(originId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.readableDatabase.rawQuery(
                "SELECT origin_id, destination_id, usage_count, last_used_at FROM station_pair_usage WHERE origin_id = ? ORDER BY usage_count DESC, last_used_at DESC, destination_id ASC",
                arrayOf(originId),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(StationPairUsage(
                        cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3),
                    ))
                }
            }
        }
    }

    private fun readUsage(): Map<String, StationUsage> = database.readableDatabase.rawQuery(
        "SELECT station_id, origin_count, destination_count, last_used_at FROM station_usage", null,
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) {
                val item = StationUsage(cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3))
                put(item.stationId, item)
            }
        }
    }
}
