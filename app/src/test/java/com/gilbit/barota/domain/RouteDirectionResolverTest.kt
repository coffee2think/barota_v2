package com.gilbit.barota.domain

import com.gilbit.barota.data.model.RouteLine
import com.gilbit.barota.data.model.RouteNetwork
import com.gilbit.barota.data.model.RouteService
import com.gilbit.barota.data.model.ServiceDirections
import com.gilbit.barota.testing.RouteNetworkTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RouteDirectionResolverTest {
    private val network = RouteNetworkTestData.network
    private val resolver = RouteNetworkTestData.resolver()

    @Test
    fun doksanToJongno3gaIsUpAndReverseIsDownUsingAssetMetadata() {
        val up = resolve("독산", "종로3가") as RouteDirectionResult.Resolved
        val down = resolve("종로3가", "독산") as RouteDirectionResult.Resolved
        assertEquals("1001", up.route.subwayId)
        assertEquals("1호선", up.route.line)
        assertEquals(TravelDirection.UP, up.route.direction)
        assertEquals(TravelDirection.DOWN, down.route.direction)
    }

    @Test
    fun routeAssetContainsAllProvidedLinesServicesAndStations() {
        assertEquals(2, network.version)
        assertEquals((1..9).map { "${it}호선" }, network.lines.map { it.line })
        assertEquals((1001..1009).map(Int::toString), network.lines.map { it.subwayId })
        assertEquals((1..9).map { "${it}호선" }, network.lines.map { it.timetableLineName })
        assertEquals(16, network.lines.sumOf { it.services.size })
        assertEquals(634, network.lines.sumOf { line -> line.services.sumOf { it.edges.size } })
        assertEquals(
            398,
            network.lines.flatMap { it.services }.flatMap { it.edges }.flatten().distinct().size,
        )
    }

    @Test
    fun linearLinesResolveTheirProvidedEndToEndDirections() {
        assertDirection("1호선", "연천", "인천", TravelDirection.DOWN)
        assertDirection("1호선", "신창", "연천", TravelDirection.UP)
        assertDirection("3호선", "대화", "오금", TravelDirection.DOWN)
        assertDirection("4호선", "진접", "오이도", TravelDirection.DOWN)
        assertDirection("5호선", "방화", "하남검단산", TravelDirection.DOWN)
        assertDirection("5호선", "마천", "방화", TravelDirection.UP)
        assertDirection("6호선", "응암", "신내", TravelDirection.DOWN)
        assertDirection("7호선", "장암", "석남", TravelDirection.DOWN)
        assertDirection("8호선", "모란", "암사", TravelDirection.UP)
        assertDirection("8호선", "암사", "모란", TravelDirection.DOWN)
        assertDirection("9호선", "개화", "중앙보훈병원", TravelDirection.DOWN)
    }

    @Test
    fun providedBranchesUseSeparateServiceGraphs() {
        assertDirection("1호선", "연천", "광명", TravelDirection.DOWN)
        assertDirection("1호선", "연천", "서동탄", TravelDirection.DOWN)
        assertDirection("2호선", "성수", "신설동", TravelDirection.INNER)
        assertDirection("2호선", "까치산", "신도림", TravelDirection.OUTER)
        assertTrue(resolveOn("1호선", "인천", "신창") is RouteDirectionResult.Unsupported)
        assertTrue(resolveOn("5호선", "마천", "하남검단산") is RouteDirectionResult.Unsupported)
    }

    @Test
    fun eungamLoopIsOneWayAndMainCircleChoosesShortestDirection() {
        assertDirection("6호선", "응암", "구산", TravelDirection.DOWN)
        assertDirection("6호선", "구산", "역촌", TravelDirection.DOWN)
        assertDirection("2호선", "강남", "서울대입구", TravelDirection.INNER)
        assertDirection("2호선", "서울대입구", "강남", TravelDirection.OUTER)
    }

    @Test
    fun everyConfiguredEdgeIsResolvedFromRouteData() {
        network.lines.forEach { line ->
            line.services.forEach { service ->
                val forward = TravelDirection.fromArrivalApi(service.directions.forward)!!
                service.edges.forEach { edge ->
                    val forwardResult = resolveOn(line.line, edge[0], edge[1])
                    assertEquals(
                        forward,
                        (forwardResult as RouteDirectionResult.Resolved).route.direction,
                    )
                }
            }
        }
    }

    @Test
    fun explicitAliasesAndApiNamesComeFromRouteData() {
        val result = resolve(" 독산역 ", "종로3가역") as RouteDirectionResult.Resolved
        val seoul = resolve("서울", "남영") as RouteDirectionResult.Resolved
        assertEquals("독산", result.route.originName)
        assertEquals("종로3가", result.route.destinationName)
        assertEquals("서울역", seoul.route.originName)
        assertEquals("서울", seoul.route.originApiName)
        assertEquals("부산대양산캠퍼스역", network.canonicalName("부산대양산캠퍼스역"))
    }

    @Test
    fun ambiguousLineRequiresSelectionWithoutPickingFirst() {
        val lines = listOf("2호선", "1호선")
        val choice = resolver.resolve("시청", "신도림", lines, lines)
        assertEquals(RouteDirectionResult.LineSelectionRequired(listOf("1호선", "2호선")), choice)
        val selected = resolver.resolve("시청", "신도림", lines, lines, "1호선")
        assertEquals(TravelDirection.DOWN, (selected as RouteDirectionResult.Resolved).route.direction)
        assertEquals(
            TravelDirection.OUTER,
            (resolver.resolve("시청", "신도림", lines, lines, "2호선") as RouteDirectionResult.Resolved)
                .route.direction,
        )
        assertTrue(resolver.resolve("시청", "신도림", lines, lines, "7호선") is RouteDirectionResult.Unknown)
    }

    @Test
    fun branchEdgesAreTraversedWithoutStationIndexes() {
        val branchResolver = resolverFor(
            edges = listOf(listOf("A", "B"), listOf("B", "C"), listOf("B", "D")),
        )
        assertEquals(TravelDirection.DOWN, branchResolver.direction("A", "D"))
        assertEquals(TravelDirection.UP, branchResolver.direction("D", "A"))
        assertTrue(branchResolver.result("C", "D") is RouteDirectionResult.Unsupported)
    }

    @Test
    fun equallyShortCycleDirectionsAreReportedAsAmbiguous() {
        val circularResolver = resolverFor(
            edges = listOf(
                listOf("A", "B"),
                listOf("B", "C"),
                listOf("C", "D"),
                listOf("D", "A"),
            ),
            directions = ServiceDirections("내선", "외선"),
        )
        assertTrue(circularResolver.result("A", "C") is RouteDirectionResult.Unknown)
    }

    @Test
    fun disconnectedAndUnsupportedRoutesAreNotGuessed() {
        assertTrue(resolve("독산", "인천") is RouteDirectionResult.Unsupported)
        assertDirection("1호선", "독산", "청량리", TravelDirection.UP)
        assertTrue(resolver.resolve("독산", "강남", listOf("1호선"), listOf("2호선")) is RouteDirectionResult.Unsupported)
        assertEquals(
            TravelDirection.OUTER,
            (resolver.resolve("시청", "신도림", listOf("2호선"), listOf("2호선")) as RouteDirectionResult.Resolved)
                .route.direction,
        )
        assertTrue(resolve("독산", "없는역") is RouteDirectionResult.Unsupported)
        assertTrue(resolve("독산역", "독산") is RouteDirectionResult.Unknown)
        assertTrue(resolve("", "종로3가") is RouteDirectionResult.Unknown)
    }

    @Test
    fun malformedEdgesFailFast() {
        try {
            resolverFor(edges = listOf(listOf("A")))
            fail("Malformed edge must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Every edge"))
        }
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

    private fun resolveOn(line: String, origin: String, destination: String) =
        resolver.resolve(origin, destination, listOf(line), listOf(line))

    private fun assertDirection(
        line: String,
        origin: String,
        destination: String,
        expected: TravelDirection,
    ) {
        val result = resolveOn(line, origin, destination) as RouteDirectionResult.Resolved
        assertEquals(expected, result.route.direction)
        assertEquals(line, result.route.line)
    }

    private fun resolverFor(
        edges: List<List<String>>,
        directions: ServiceDirections = ServiceDirections("하행", "상행"),
    ) = RouteDirectionResolver(
        RouteNetwork(
            version = 1,
            lines = listOf(
                RouteLine(
                    line = "테스트선",
                    subwayId = "9999",
                    services = listOf(RouteService("main", directions, edges)),
                ),
            ),
        ),
    )

    private fun RouteDirectionResolver.result(origin: String, destination: String) =
        resolve(origin, destination, listOf("테스트선"), listOf("테스트선"))

    private fun RouteDirectionResolver.direction(origin: String, destination: String): TravelDirection =
        (result(origin, destination) as RouteDirectionResult.Resolved).route.direction
}
