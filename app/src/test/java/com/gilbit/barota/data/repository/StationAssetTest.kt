package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.Station
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationAssetTest {
    private val stations: List<Station> = Json.decodeFromString(
        File("src/main/assets/stations.json").readText(Charsets.UTF_8),
    )

    @Test
    fun stationAssetHasUniqueIdsAndNonEmptyFields() {
        assertEquals(655, stations.size)
        assertEquals(stations.size, stations.map { it.id }.distinct().size)
        assertTrue(stations.all { it.id.isNotBlank() && it.name.isNotBlank() && it.lines.isNotEmpty() })
        assertTrue(stations.all { it.lines.size == it.lines.distinct().size })
        assertEquals(24, stations.flatMap { it.lines }.distinct().size)
    }

    @Test
    fun missingStationsAndInterchangeLinesAreIncluded() {
        assertEquals(listOf("1호선"), stations.single { it.name == "독산" }.lines)
        assertEquals("seoul", stations.single { it.name == "서울역" }.id)
        assertTrue(stations.single { it.name == "서울역" }.lines.contains("GTX-A"))
        assertEquals(
            setOf("1호선", "2호선", "우이신설경전철"),
            stations.single { it.name == "신설동" }.lines.toSet(),
        )
        assertEquals(
            setOf("4호선", "7호선"),
            stations.single { it.name == "총신대입구(이수)" }.lines.toSet(),
        )
    }

    @Test
    fun unrelatedStationsWithSameNameAreNotMerged() {
        for ((name, lines) in mapOf(
            "신촌" to setOf("2호선", "경의중앙선"),
            "양평" to setOf("5호선", "경의중앙선"),
        )) {
            val matches = stations.filter { it.name == name }
            assertEquals(2, matches.size)
            assertTrue(matches.all { it.lines.size == 1 })
            assertEquals(lines, matches.flatMap { it.lines }.toSet())
        }
    }
}
