package com.gilbit.barota.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDirectionResolverTest {
    private val resolver = RouteDirectionResolver()

    @Test
    fun doksanToJongno3gaIsUpAndReverseIsDown() {
        val up = resolve("독산", "종로3가") as RouteDirectionResult.Resolved
        val down = resolve("종로3가", "독산") as RouteDirectionResult.Resolved
        assertEquals("1001", up.route.subwayId)
        assertEquals("1호선", up.route.line)
        assertEquals(TravelDirection.UP, up.route.direction)
        assertEquals(TravelDirection.DOWN, down.route.direction)
    }

    @Test
    fun entireVerifiedSegmentHasConsistentDirections() {
        val stations = listOf(
            "종로3가", "종각", "시청", "서울역", "남영", "용산", "노량진",
            "대방", "신길", "영등포", "신도림", "구로", "가산디지털단지", "독산",
        )
        for (origin in stations.indices) {
            for (destination in stations.indices) {
                if (origin == destination) continue
                val result = resolve(stations[origin], stations[destination]) as RouteDirectionResult.Resolved
                assertEquals(
                    if (origin < destination) TravelDirection.DOWN else TravelDirection.UP,
                    result.route.direction,
                )
            }
        }
    }

    @Test
    fun explicitStationAliasesAreNormalized() {
        val result = resolve(" 독산역 ", "종로3가역") as RouteDirectionResult.Resolved
        assertEquals("독산", result.route.originName)
        assertEquals("종로3가", result.route.destinationName)
        assertEquals("서울역", RouteDirectionResolver.canonicalName("서울"))
        assertEquals("서울", RouteDirectionResolver.apiStationName("서울역"))
        assertEquals("독산", RouteDirectionResolver.apiStationName("독산역"))
        assertEquals("부산대양산캠퍼스역", RouteDirectionResolver.canonicalName("부산대양산캠퍼스역"))
    }

    @Test
    fun ambiguousLineRequiresSelectionWithoutPickingFirst() {
        val lines = listOf("2호선", "1호선")
        val choice = resolver.resolve("시청", "신도림", lines, lines)
        assertEquals(RouteDirectionResult.LineSelectionRequired(listOf("1호선", "2호선")), choice)
        val selected = resolver.resolve("시청", "신도림", lines, lines, "1호선")
        assertEquals(TravelDirection.DOWN, (selected as RouteDirectionResult.Resolved).route.direction)
        assertTrue(resolver.resolve("시청", "신도림", lines, lines, "2호선") is RouteDirectionResult.Unsupported)
        assertTrue(resolver.resolve("시청", "신도림", lines, lines, "7호선") is RouteDirectionResult.Unknown)
    }

    @Test
    fun unverifiedBranchTransferCircularAndMissingStationsAreNotGuessed() {
        assertTrue(resolve("독산", "인천") is RouteDirectionResult.Unsupported)
        assertTrue(resolve("독산", "청량리") is RouteDirectionResult.Unsupported)
        assertTrue(resolver.resolve("독산", "강남", listOf("1호선"), listOf("2호선")) is RouteDirectionResult.Unsupported)
        assertTrue(resolver.resolve("시청", "신도림", listOf("2호선"), listOf("2호선")) is RouteDirectionResult.Unsupported)
        assertTrue(resolve("독산", "없는역") is RouteDirectionResult.Unsupported)
        assertTrue(resolve("독산역", "독산") is RouteDirectionResult.Unknown)
        assertTrue(resolve("", "종로3가") is RouteDirectionResult.Unknown)
    }

    @Test
    fun arrivalApiLabelsAreTypedAndUnknownValuesAreNotMatched() {
        assertEquals(TravelDirection.UP, TravelDirection.fromArrivalApi(" 상행 "))
        assertEquals(TravelDirection.DOWN, TravelDirection.fromArrivalApi("하행"))
        assertEquals(TravelDirection.INNER, TravelDirection.fromArrivalApi("내선"))
        assertEquals(TravelDirection.OUTER, TravelDirection.fromArrivalApi("외선"))
        assertFalse(TravelDirection.UP == TravelDirection.INNER)
        assertFalse(TravelDirection.DOWN == TravelDirection.OUTER)
        for (unknown in listOf("", "0", "1", "상행/내선", "가산디지털단지방면")) {
            assertNull(TravelDirection.fromArrivalApi(unknown))
        }
    }

    private fun resolve(origin: String, destination: String) =
        resolver.resolve(origin, destination, listOf("1호선"), listOf("1호선"))
}
