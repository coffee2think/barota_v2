package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopReason
import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.model.TrainNumberMappingKey
import com.gilbit.barota.data.remote.TimetableApi
import com.gilbit.barota.data.remote.TrainScheduleBody
import com.gilbit.barota.data.remote.TrainScheduleEnvelope
import com.gilbit.barota.data.remote.TrainScheduleHeader
import com.gilbit.barota.data.remote.TrainScheduleItem
import com.gilbit.barota.data.remote.TrainScheduleItems
import com.gilbit.barota.data.remote.TrainScheduleResponse
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulTrainStopRepositoryTest {
    private val now = LocalDateTime.of(2026, 9, 21, 8, 0, 0)

    @Test
    fun mappedDestinationPresenceStopsWithoutFallbackOrTimeComparison() = runTest {
        val store = FakeTrainNumberMappingStore(mapOf(mappingKey("2395") to "2395"))
        val api = FakeTimetableApi(
            response(item("2395", "서울대입구", arrivalTime = "07:50:00")),
        )

        val result = decide(repository(api, store), arrival(), "강남", "서울대입구")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(DestinationStopReason.MAPPED_DESTINATION_FOUND, result.diagnostic.reason)
        assertEquals(listOf("2395"), api.requestedTrainNumbers())
        assertTrue(store.upserts.isEmpty())
    }

    @Test
    fun missingMappingDiscoversValidatesAndStoresUniqueNumericMatch() = runTest {
        val store = FakeTrainNumberMappingStore()
        val api = FakeTimetableApi(
            response(item("K472", "종로3가")),
            response(item("K472", "독산")),
        )

        val result = decide(repository(api, store), arrival("0472"), "독산", "종로3가")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(DestinationStopReason.DISCOVERED_MAPPING_FOUND, result.diagnostic.reason)
        assertEquals(listOf("K472"), result.diagnostic.discoveredTimetableTrainNumbers)
        assertTrue(result.diagnostic.mappingUpdated)
        assertEquals("K472", store.values.getValue(mappingKey("472")))
        assertEquals(listOf("", "K472"), api.requestedTrainNumbers())
    }

    @Test
    fun numericCoreRequiresEqualityRatherThanSubstring() = runTest {
        val api = FakeTimetableApi(response(item("1472", "종로3가")))

        val result = decide(repository(api, FakeTrainNumberMappingStore()), arrival("0472"), "독산", "종로3가")

        assertEquals(DestinationStopStatus.DOES_NOT_STOP, result.status)
        assertEquals(DestinationStopReason.DESTINATION_CANDIDATE_NOT_FOUND, result.diagnostic.reason)
        assertEquals(1, api.requestedUrls.size)
    }

    @Test
    fun ambiguousNumericMatchesStayUnknownAndAreNotStored() = runTest {
        val store = FakeTrainNumberMappingStore()
        val api = FakeTimetableApi(response(item("K472", "종로3가"), item("472K", "종로3가")))

        val result = decide(repository(api, store), arrival("0472"), "독산", "종로3가")

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.AMBIGUOUS_DESTINATION_CANDIDATES, result.diagnostic.reason)
        assertEquals(listOf("K472", "472K"), result.diagnostic.discoveredTimetableTrainNumbers)
        assertTrue(store.upserts.isEmpty())
    }

    @Test
    fun staleMappingIsCorrectedAfterOriginValidation() = runTest {
        val key = mappingKey("472")
        val store = FakeTrainNumberMappingStore(mapOf(key to "K472"))
        val api = FakeTimetableApi(
            response(),
            response(item("472K", "종로3가")),
            response(item("472K", "독산")),
        )

        val result = decide(repository(api, store), arrival("0472"), "독산", "종로3가")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(DestinationStopReason.CORRECTED_MAPPING_FOUND, result.diagnostic.reason)
        assertEquals("K472", result.diagnostic.existingTimetableTrainNumber)
        assertEquals("472K", store.values.getValue(key))
    }

    @Test
    fun failedOriginValidationDoesNotOverwriteExistingMapping() = runTest {
        val key = mappingKey("472")
        val store = FakeTrainNumberMappingStore(mapOf(key to "K472"))
        val api = FakeTimetableApi(
            response(),
            response(item("472K", "종로3가")),
            response(),
        )

        val result = decide(repository(api, store), arrival("0472"), "독산", "종로3가")

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.ORIGIN_VALIDATION_FAILED, result.diagnostic.reason)
        assertEquals("K472", store.values.getValue(key))
        assertTrue(store.upserts.isEmpty())
    }

    @Test
    fun unmappedTrainsShareOneDestinationFallbackLookup() = runTest {
        val api = FakeTimetableApi(
            response(item("K472", "종로3가"), item("K473", "종로3가")),
            response(item("K472", "독산")),
            response(item("K473", "독산")),
        )
        val arrivals = listOf(arrival("0472", "a"), arrival("0473", "b"))

        val results = repository(api, FakeTrainNumberMappingStore())
            .getDestinationStopDecisions(arrivals, "독산", "종로3가")

        assertEquals(setOf(DestinationStopStatus.STOPS), results.values.map { it.status }.toSet())
        assertEquals(1, api.requestedTrainNumbers().count(String::isEmpty))
    }

    @Test
    fun fallbackLoadsEveryReportedPage() = runTest {
        val api = FakeTimetableApi(
            response(item("9999", "종로3가"), totalCount = 1001),
            response(item("K472", "종로3가"), totalCount = 1001),
            response(item("K472", "독산")),
        )

        val result = decide(repository(api, FakeTrainNumberMappingStore()), arrival("0472"), "독산", "종로3가")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(listOf("1", "1001", "1"), api.requestedUrls.map { it.pathSegments[3] })
    }

    @Test
    fun lookupFailureBecomesUnknownAndCancellationPropagates() = runTest {
        val failed = decide(
            repository(FakeTimetableApi(errorResponse("unavailable")), FakeTrainNumberMappingStore()),
            arrival("0472"),
            "독산",
            "종로3가",
        )
        assertEquals(DestinationStopStatus.UNKNOWN, failed.status)
        assertEquals(DestinationStopReason.LOOKUP_FAILED, failed.diagnostic.reason)

        val cancellingApi = object : TimetableApi {
            override suspend fun getTrainSchedule(url: HttpUrl): TrainScheduleResponse = throw CancellationException()
        }
        var cancelled = false
        try {
            decide(repository(cancellingApi, FakeTrainNumberMappingStore()), arrival("0472"), "독산", "종로3가")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun terminalMatchAndInvalidInputsDoNotCallApis() = runTest {
        val api = FakeTimetableApi()
        val repository = repository(api, FakeTrainNumberMappingStore())
        val arrivals = listOf(
            arrival("2395", "terminal").copy(terminalStation = "서울대입구"),
            arrival("ABC", "invalid"),
            arrival("2396", "unsupported").copy(timetableLineName = null),
        )

        val results = repository.getDestinationStopDecisions(arrivals, "강남", "서울대입구")

        assertEquals(DestinationStopStatus.STOPS, results.getValue("terminal").status)
        assertEquals(DestinationStopReason.INVALID_REALTIME_TRAIN_NUMBER, results.getValue("invalid").diagnostic.reason)
        assertEquals(DestinationStopReason.UNSUPPORTED_LINE, results.getValue("unsupported").diagnostic.reason)
        assertTrue(api.requestedUrls.isEmpty())
    }

    @Test
    fun requestUsesDocumentedArgumentsAndEmptyTrainNumber() = runTest {
        val api = FakeTimetableApi(response())

        decide(repository(api, FakeTrainNumberMappingStore()), arrival(), "강남역", "서울대입구역")

        assertEquals(
            listOf(
                "test-key", "json", "getTrainSch", "1", "1000", "", "N", "내선", "평일",
                "2호선", "", "서울대입구", "", "", "", "", "", "", "2026-09-21 08:00:00",
            ),
            api.requestedUrls.first().pathSegments,
        )
    }

    private suspend fun decide(
        repository: SeoulTrainStopRepository,
        arrival: TrainArrival,
        origin: String,
        destination: String,
    ) = repository.getDestinationStopDecisions(listOf(arrival), origin, destination).getValue(arrival.id)

    private fun repository(api: TimetableApi, store: TrainNumberMappingStore) =
        SeoulTrainStopRepository(api, "test-key", store, now)

    private fun arrival(trainNumber: String = "2395", id: String = "arrival-$trainNumber") = TrainArrival(
        id = id,
        trainNumber = trainNumber,
        line = "2호선",
        direction = "내선",
        terminalStation = "성수",
        arrivalMessage = "3분 후",
        currentLocation = "역삼 출발",
        arrivalSeconds = 180,
        trainType = "일반",
        isLastTrain = false,
        receivedAt = "2026-09-21 08:00:00",
        timetableLineName = "2호선",
    )

    private fun mappingKey(number: String) = TrainNumberMappingKey(
        "2호선", number, "내선", "성수", "일반", "평일",
    )
}

private fun item(
    trainNumber: String,
    stationName: String,
    arrivalTime: String? = null,
) = TrainScheduleItem(trainno = trainNumber, stnNm = stationName, trainArvlTm = arrivalTime)

private fun response(
    vararg items: TrainScheduleItem,
    totalCount: Int = items.size,
) = TrainScheduleResponse(
    response = TrainScheduleEnvelope(
        header = TrainScheduleHeader(resultCode = "00", resultMsg = "NORMAL_CODE"),
        body = TrainScheduleBody(
            items = TrainScheduleItems(item = items.toList()),
            totalCount = totalCount,
        ),
    ),
)

private fun errorResponse(message: String) = TrainScheduleResponse(
    response = TrainScheduleEnvelope(header = TrainScheduleHeader(resultCode = "ERROR", resultMsg = message)),
)

private class FakeTimetableApi(vararg responses: TrainScheduleResponse) : TimetableApi {
    private val queuedResponses = ArrayDeque(responses.toList())
    val requestedUrls = mutableListOf<HttpUrl>()

    override suspend fun getTrainSchedule(url: HttpUrl): TrainScheduleResponse {
        requestedUrls += url
        return queuedResponses.removeFirst()
    }

    fun requestedTrainNumbers(): List<String> = requestedUrls.map { it.pathSegments[10] }
}

private class FakeTrainNumberMappingStore(initial: Map<TrainNumberMappingKey, String> = emptyMap()) :
    TrainNumberMappingStore {
    val values = initial.toMutableMap()
    val upserts = mutableListOf<Pair<TrainNumberMappingKey, String>>()

    override suspend fun find(key: TrainNumberMappingKey): String? = values[key]

    override suspend fun upsert(key: TrainNumberMappingKey, timetableTrainNumber: String) {
        values[key] = timetableTrainNumber
        upserts += key to timetableTrainNumber
    }
}
