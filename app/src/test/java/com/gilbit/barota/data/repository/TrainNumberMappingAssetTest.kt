package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.TrainNumberMappingAsset
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainNumberMappingAssetTest {
    private val content = File("src/main/assets/train_number_mappings.json").readText(Charsets.UTF_8)
    private val json = Json { ignoreUnknownKeys = true }
    private val asset = json.decodeFromString<TrainNumberMappingAsset>(content)

    @Test
    fun generatedAssetHasUniqueCompleteKeysAndAllTargetLines() {
        assertEquals(1, asset.version)
        assertTrue(asset.generatedAt.isNotBlank())
        assertEquals(setOf("weekday", "weekend"), asset.sourceDates.keys)
        assertTrue(asset.mappings.size > 7_000)
        assertEquals(asset.mappings.size, asset.mappings.map { it.key() }.distinct().size)
        assertEquals(
            (1..9).map { "${it}호선" }.toSet(),
            asset.mappings.map { it.timetableLineName }.toSet(),
        )
        assertEquals(setOf("평일", "주말"), asset.mappings.map { it.dayType }.toSet())
    }

    @Test
    fun assetContainsObservedTrainNumberFormats() {
        assertTrue(asset.mappings.any { it.timetableLineName == "1호선" && it.timetableTrainNumber.startsWith("K") })
        assertTrue(asset.mappings.any { it.timetableLineName == "2호선" && it.timetableTrainNumber.all(Char::isDigit) })
        assertTrue(asset.mappings.any { it.timetableLineName == "3호선" && it.timetableTrainNumber.endsWith("K") })
        assertTrue(asset.mappings.any { it.timetableLineName == "4호선" && it.timetableTrainNumber.startsWith("K") })
        assertTrue(asset.mappings.any { it.timetableLineName == "9호선" && it.timetableTrainNumber.startsWith("C") })
        assertTrue(asset.mappings.any { it.timetableLineName == "9호선" && it.timetableTrainNumber.startsWith("E") })
    }

    @Test
    fun secondLineObservedRealtimeAliasesAreMaterializedAsRows() {
        for ((realtimeNumber, timetableNumber) in mapOf(
            "8415" to "2415",
            "7419" to "2419",
            "8434" to "2434",
            "6420" to "2420",
        )) {
            assertTrue(
                asset.mappings.any {
                    it.timetableLineName == "2호선" &&
                        it.dayType == "주말" &&
                        it.realtimeTrainNumber == realtimeNumber &&
                        it.timetableTrainNumber == timetableNumber
                },
            )
        }
    }

    @Test
    fun assetStoreFindsEverySerializedEntryByItsCompositeKey() = runTest {
        val store = AssetTrainNumberMappingStore(json, content)
        for (mapping in asset.mappings) {
            assertEquals(mapping.timetableTrainNumber, store.find(mapping.key()))
        }
        assertNotNull(asset.mappings.firstOrNull())
    }

    @Test
    fun terminalFallbackIsUsedOnlyWhenBaseKeyHasOneTimetableNumber() = runTest {
        val store = AssetTrainNumberMappingStore(json, content)
        val groups = asset.mappings.groupBy {
            listOf(it.timetableLineName, it.realtimeTrainNumber, it.direction, it.trainType, it.dayType)
        }
        val unambiguous = groups.values.first { group ->
            group.map { it.timetableTrainNumber }.distinct().size == 1
        }.first()
        assertEquals(
            unambiguous.timetableTrainNumber,
            store.find(unambiguous.key().copy(terminalStation = "시간표와다른종착역")),
        )

        val ambiguous = groups.values.firstOrNull { group ->
            group.map { it.timetableTrainNumber }.distinct().size > 1
        }
        if (ambiguous != null) {
            assertEquals(
                null,
                store.find(ambiguous.first().key().copy(terminalStation = "일치하지않는종착역")),
            )
        }
    }
}
