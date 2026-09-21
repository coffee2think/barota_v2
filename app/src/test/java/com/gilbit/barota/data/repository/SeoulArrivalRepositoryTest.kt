package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopDecision
import com.gilbit.barota.data.model.DestinationStopDiagnostic
import com.gilbit.barota.data.model.DestinationStopReason
import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.remote.ApiResult
import com.gilbit.barota.data.remote.RealtimeArrivalDto
import com.gilbit.barota.data.remote.RealtimeArrivalResponse
import com.gilbit.barota.data.remote.SeoulSubwayApi
import com.gilbit.barota.testing.RouteNetworkTestData
import com.gilbit.barota.domain.RouteDirectionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulArrivalRepositoryTest {
    @Test
    fun directionFilterThenTimeOrderingThenStopStatusPreservesFinalOrder() = runTest {
        val api = FakeSubwayApi(listOf(
            dto("later", "상행").copy(barvlDt = "100"),
            dto("zero", "상행").copy(barvlDt = "0", arvlCd = "99", ordkey = "02003청량리0"),
            dto("gone", "상행").copy(barvlDt = "1", arvlCd = "2"),
            dto("now", "상행").copy(barvlDt = "0", arvlCd = "0"),
            dto("soon", "상행", terminal = "구로").copy(barvlDt = "20"),
            dto("down", "하행").copy(barvlDt = "1"),
            dto("negative", "상행").copy(barvlDt = "-1", ordkey = "01003청량리0"),
            dto("missing", "상행").copy(barvlDt = ""),
            dto("invalid", "상행").copy(barvlDt = "bad"),
        ))
        val stops = RecordingTrainStopRepository()
        val arrivals = SeoulArrivalRepository(api, stops, "test-key").getArrivals(route())
        assertEquals(listOf("now", "soon", "later", "negative", "zero", "invalid", "missing"), arrivals.map { it.trainNumber })
        assertEquals(arrivals.map { it.trainNumber }.toSet(), stops.calls.map { it.first }.toSet())
        assertEquals(com.gilbit.barota.data.model.ArrivalTimeKind.ZERO_UNCONFIRMED, arrivals.single { it.trainNumber == "zero" }.arrivalTimeKind)
        assertEquals("02003청량리0", arrivals.single { it.trainNumber == "zero" }.arrivalOrderKey)
        assertEquals(DestinationStopStatus.DOES_NOT_STOP, arrivals[1].destinationStopStatus)
        assertTrue(arrivals.drop(3).all { it.arrivalSeconds == null })
    }

    @Test
    fun filtersDirectionLineAndOriginBeforeAnyTimetableRequest() = runTest {
        // Synthetic edge cases, NOT a claim about actual operator responses.
        val api = FakeSubwayApi(listOf(
            dto("up", "상행"), dto("down", "하행"),
            dto("wrong-line", "상행", line = "1007"),
            dto("wrong-station", "상행", station = "구로"),
            dto("blank", ""), dto("unknown", "0"), dto("inner", "내선"),
            dto("short-service", "상행", terminal = "구로"),
        ))
        val stops = RecordingTrainStopRepository()
        val arrivals = SeoulArrivalRepository(api, stops, "test-key").getArrivals(route())
        assertEquals(setOf("up", "short-service"), arrivals.map { it.trainNumber }.toSet())
        assertEquals(setOf("up", "short-service"), stops.calls.map { it.first }.toSet())
        assertEquals(2, stops.calls.size)
        assertTrue(arrivals.all { it.direction == "상행" })
        assertEquals(DestinationStopStatus.DOES_NOT_STOP, arrivals.single { it.trainNumber == "short-service" }.destinationStopStatus)
        assertEquals(listOf("독산"), api.requestedStations)
        assertTrue(stops.calls.all { it.second == "독산" && it.third == "종로3가" })
    }

    @Test
    fun reversedRouteRetainsOnlyDownTrains() = runTest {
        val stops = RecordingTrainStopRepository()
        val api = FakeSubwayApi(listOf(dto("up", "상행", "종로3가"), dto("down", "하행", "종로3가")))
        val arrivals = SeoulArrivalRepository(api, stops, "test-key").getArrivals(route("종로3가", "독산"))
        assertEquals(listOf("down"), arrivals.map { it.trainNumber })
        assertEquals(listOf(Triple("down", "종로3가", "독산")), stops.calls)
    }

    @Test
    fun capturedDoksanResponseRetainsActualUpTrainsOnly() = runTest {
        val fixture = checkNotNull(javaClass.getResource("/fixtures/doksan-arrivals-2026-09-18.json")).readText()
        val response = Json.decodeFromString<RealtimeArrivalResponse>(fixture)
        assertTrue(response.realtimeArrivalList.any { it.updnLine == "상행" })
        assertTrue(response.realtimeArrivalList.any { it.updnLine == "하행" })
        val stops = RecordingTrainStopRepository()
        val arrivals = SeoulArrivalRepository(FakeSubwayApi(response.realtimeArrivalList), stops, "test-key").getArrivals(route())
        val expected = response.realtimeArrivalList.filter { it.subwayId == "1001" && it.updnLine == "상행" }
        assertEquals(expected.size, arrivals.size)
        assertEquals(expected.map { it.btrainNo }.sorted(), arrivals.map { it.trainNumber }.sorted())
        assertEquals(expected.size, stops.calls.size)
        assertTrue(arrivals.all { it.line == "1호선" && it.direction == "상행" })
        // Actual zero-second running rows must not become "arriving now".
        assertTrue(arrivals.all { it.arrivalSeconds == null })
        assertTrue(arrivals.all { it.arrivalTimeKind == com.gilbit.barota.data.model.ArrivalTimeKind.ZERO_UNCONFIRMED })
    }

    @Test
    fun filteredEmptyResponseDoesNotCallTimetable() = runTest {
        val stops = RecordingTrainStopRepository()
        val arrivals = SeoulArrivalRepository(FakeSubwayApi(listOf(dto("down", "하행"))), stops, "test-key").getArrivals(route())
        assertTrue(arrivals.isEmpty())
        assertTrue(stops.calls.isEmpty())
    }

    @Test
    fun unknownApiDirectionIsNotReportedAsNoTrains() = runTest {
        val stops = RecordingTrainStopRepository()
        try {
            SeoulArrivalRepository(FakeSubwayApi(listOf(dto("unknown", ""))), stops, "test-key").getArrivals(route())
            throw AssertionError("Unknown direction must be distinct from an empty result")
        } catch (_: ArrivalDirectionUnknownException) { /* expected */ }
        assertTrue(stops.calls.isEmpty())
    }

    @Test
    fun missingKeyFailsBeforeApiOrTimetableCalls() = runTest {
        val api = FakeSubwayApi(listOf(dto("up", "상행")))
        val stops = RecordingTrainStopRepository()
        try {
            SeoulArrivalRepository(api, stops, "").getArrivals(route())
            throw AssertionError("Missing key must fail")
        } catch (_: MissingApiKeyException) { /* expected */ }
        assertTrue(api.requestedStations.isEmpty())
        assertTrue(stops.calls.isEmpty())
    }

    @Test
    fun apiNoDataCodeIsAnEmptyResultNotAnError() = runTest {
        val stops = RecordingTrainStopRepository()
        val api = FakeSubwayApi(emptyList(), ApiResult(code = "INFO-200"))
        assertTrue(SeoulArrivalRepository(api, stops, "test-key").getArrivals(route()).isEmpty())
        assertTrue(stops.calls.isEmpty())
    }

    @Test
    fun apiErrorFailsBeforeTimetableCalls() = runTest {
        val stops = RecordingTrainStopRepository()
        val api = FakeSubwayApi(emptyList(), ApiResult(code = "ERROR-500", message = "API unavailable"))
        try {
            SeoulArrivalRepository(api, stops, "test-key").getArrivals(route())
            throw AssertionError("API error must propagate")
        } catch (error: SeoulApiException) {
            assertEquals("API unavailable", error.message)
        }
        assertTrue(stops.calls.isEmpty())
    }

    @Test
    fun seoulAliasUsesApiNameAndMatchesApiResponse() = runTest {
        val api = FakeSubwayApi(listOf(dto("up", "상행", "서울")))
        assertEquals(1, SeoulArrivalRepository(api, RecordingTrainStopRepository(), "test-key").getArrivals(route("서울역", "종로3가")).size)
        assertEquals(listOf("서울"), api.requestedStations)
    }

    @Test
    fun routeMetadataDrivesLineNameAndParentheticalApiStationMatching() = runTest {
        val resolved = RouteNetworkTestData.resolver().resolve(
            "자양",
            "청담",
            listOf("7호선"),
            listOf("7호선"),
        ) as RouteDirectionResult.Resolved
        val api = FakeSubwayApi(
            listOf(dto("line-seven", "하행", "자양(뚝섬한강공원)", "1007")),
        )

        val arrival = SeoulArrivalRepository(api, RecordingTrainStopRepository(), "test-key")
            .getArrivals(resolved.route)
            .single()

        assertEquals("7호선", arrival.line)
        assertEquals("7호선", arrival.timetableLineName)
        assertEquals(listOf("자양(뚝섬한강공원)"), api.requestedStations)
    }

    @Test
    fun cancellationIsNotConvertedToUnknownStopStatus() = runTest {
        val stops = object : TrainStopRepository {
            override suspend fun getDestinationStopDecisions(arrivals: List<TrainArrival>, originName: String, destinationName: String): Map<String, DestinationStopDecision> {
                throw CancellationException("cancelled")
            }
        }
        try {
            SeoulArrivalRepository(FakeSubwayApi(listOf(dto("up", "상행"))), stops, "test-key").getArrivals(route())
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) { /* expected */ }
    }

    @Test
    fun failedIndividualTimetableRetainsTrainAsUnknown() = runTest {
        val stops = object : TrainStopRepository {
            override suspend fun getDestinationStopDecisions(arrivals: List<TrainArrival>, originName: String, destinationName: String): Map<String, DestinationStopDecision> {
                throw java.io.IOException("timetable unavailable")
            }
        }
        val arrivals = SeoulArrivalRepository(FakeSubwayApi(listOf(dto("up", "상행"))), stops, "test-key").getArrivals(route())
        assertEquals(DestinationStopStatus.UNKNOWN, arrivals.single().destinationStopStatus)
        assertEquals(DestinationStopReason.LOOKUP_FAILED, arrivals.single().destinationStopDiagnostic.reason)
    }

    private fun route(origin: String = "독산", destination: String = "종로3가") =
        (RouteNetworkTestData.resolver().resolve(origin, destination, listOf("1호선"), listOf("1호선")) as RouteDirectionResult.Resolved).route

    private fun dto(number: String, direction: String, station: String = "독산", line: String = "1001", terminal: String = "청량리") =
        RealtimeArrivalDto(
            subwayId = line, updnLine = direction, statnNm = station,
            btrainNo = number, bstatnNm = terminal, barvlDt = "120",
            trainLineNm = "청량리행 - 상행", // Must not replace missing updnLine.
        )
}

private class FakeSubwayApi(
    private val rows: List<RealtimeArrivalDto>,
    private val result: ApiResult = ApiResult(code = "INFO-000"),
) : SeoulSubwayApi {
    val requestedStations = mutableListOf<String>()
    override suspend fun getRealtimeArrivals(apiKey: String, stationName: String): RealtimeArrivalResponse {
        requestedStations += stationName
        return RealtimeArrivalResponse(errorMessage = result, realtimeArrivalList = rows)
    }
}

private class RecordingTrainStopRepository : TrainStopRepository {
    val calls = mutableListOf<Triple<String, String, String>>()
    override suspend fun getDestinationStopDecisions(
        arrivals: List<TrainArrival>,
        originName: String,
        destinationName: String,
    ): Map<String, DestinationStopDecision> = arrivals.associate { arrival ->
        calls += Triple(arrival.trainNumber, originName, destinationName)
        val status = if (arrival.terminalStation == "구로") DestinationStopStatus.DOES_NOT_STOP else DestinationStopStatus.STOPS
        arrival.id to DestinationStopDecision(
            status = status,
            diagnostic = DestinationStopDiagnostic(
                reason = if (status == DestinationStopStatus.STOPS) {
                    DestinationStopReason.MAPPED_DESTINATION_FOUND
                } else {
                    DestinationStopReason.DESTINATION_CANDIDATE_NOT_FOUND
                },
            ),
        )
    }
}
