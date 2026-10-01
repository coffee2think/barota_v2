package com.gilbit.barota.domain

import com.gilbit.barota.data.model.Station
import com.gilbit.barota.data.model.StationUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class StationOrderingTest {
    private fun station(id: String, name: String = id, line: String = "2호선") = Station(id, name, listOf(line))

    @Test fun noHistoryKeepsAlphabeticalOrderAndHasNoShortcuts() {
        val stations = listOf(station("서울역"), station("가락시장"), station("강남"))
        assertEquals(listOf("가락시장", "강남", "서울역"), orderStationResults(stations, "  ", emptyMap()).map { it.name })
        assertEquals(emptyList<Station>(), frequentStations(stations, emptyMap()))
    }

    @Test fun shortcutsUseTotalCountThenRecencyThenNameAndHaveFiveItemLimit() {
        val stations = listOf("가", "나", "다", "라", "마", "바", "사", "미사용").map { station(it) }
        val usage = mapOf(
            "가" to StationUsage("가", 1, 2, 100),
            "나" to StationUsage("나", 3, 0, 100),
            "다" to StationUsage("다", 1, 2, 200),
            "라" to StationUsage("라", 4, 0, 0),
            "마" to StationUsage("마", 1, 0, 50),
            "바" to StationUsage("바", 1, 0, 30),
            "사" to StationUsage("사", 1, 0, null),
            "deleted" to StationUsage("deleted", 100, 0, 900),
        )
        assertEquals(listOf("라", "다", "가", "나", "마"), frequentStations(stations, usage).map { it.id })
        assertEquals(stations.sortedBy { it.name }, orderStationResults(stations.reversed(), "", usage))
    }

    @Test fun searchRelevanceAlwaysPrecedesPersonalization() {
        val stations = listOf(
            station("line", "서울역", "강남선"), station("partial", "신강남"),
            station("prefix", "강남구청"), station("exact", "강남"),
        )
        val usage = mapOf("line" to StationUsage("line", 100, 0, 100), "partial" to StationUsage("partial", 50, 0, 100))
        assertEquals(listOf("exact", "prefix", "partial", "line"), orderStationResults(stations, " 강남 ", usage).map { it.id })
    }

    @Test fun sameMatchLevelUsesHistoryThenNameAndStableId() {
        val stations = listOf(station("z", "강남나"), station("a", "강남나"), station("old", "강남다"),
            station("recent", "강남라"), station("frequent", "강남마"), station("unused", "강남가"))
        val usage = listOf(StationUsage("z", 1, 0, 100), StationUsage("a", 1, 0, 100),
            StationUsage("old", 1, 0, 100), StationUsage("recent", 1, 0, 200), StationUsage("frequent", 2, 0, 0)).associateBy { it.stationId }
        val expected = listOf("frequent", "recent", "a", "z", "old", "unused")
        assertEquals(expected, orderStationResults(stations, "강남", usage).map { it.id })
        assertEquals(expected, orderStationResults(stations.reversed(), "강남", usage).map { it.id })
    }

    @Test fun lineSearchAndCaseInsensitiveNamesRemainSupported() {
        val stations = listOf(station("a", "ABC"), station("b", "Abcd"), station("c", "Xabc"), station("d", "다", "1호선"))
        assertEquals(listOf("a", "b", "c"), orderStationResults(stations, "abc", emptyMap()).map { it.id })
        assertEquals(listOf("d"), orderStationResults(stations, "1호선", emptyMap()).map { it.id })
        assertEquals(emptyList<Station>(), orderStationResults(stations, "없는역", emptyMap()))
    }
}
