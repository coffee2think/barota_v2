package com.gilbit.barota.domain

import com.gilbit.barota.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteShortcutsTest {
    private val stations = listOf("a", "b", "c", "d").map { Station(it, it, emptyList()) }
    @Test fun recommendationsExcludeSavedInvalidAndZeroCountAndRespectRankingAndLimit() {
        val history = listOf(
            StationPairUsage("a", "b", 100, 1), StationPairUsage("b", "a", 5, 2),
            StationPairUsage("c", "a", 5, 3), StationPairUsage("d", "a", 4, 4),
            StationPairUsage("a", "c", 4, 4), StationPairUsage("missing", "a", 200, 1),
            StationPairUsage("a", "a", 200, 1), StationPairUsage("a", "d", 0, 9),
        )
        val routes = recommendedRouteShortcuts(stations, history, listOf(SavedStationPair("a", "b", 1)))
        assertEquals(listOf("c" to "a", "b" to "a", "a" to "c"), routes.map { it.origin.id to it.destination.id })
    }
    @Test fun savedRoutesUseLatestSaveAndKeepDirectionsSeparate() {
        val routes = savedRouteShortcuts(stations, listOf(
            SavedStationPair("a", "b", 1), SavedStationPair("b", "a", 2),
            SavedStationPair("a", "a", 3), SavedStationPair("missing", "a", 4),
        ))
        assertEquals(listOf("b" to "a", "a" to "b"), routes.map { it.origin.id to it.destination.id })
    }
}
