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
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SeoulTrainStopRepositoryTest {
    private val now = LocalDateTime.of(2026, 9, 21, 8, 0, 0)

    @Test
    fun staticMappingIsLookedUpBeforeTimetableRequests() = runTest {
        val store = FakeTrainNumberMappingStore("2395")
        val api = FakeTimetableApi(
            listOf(
                response(item("2395", "강남", departureTime = "08:03:00")),
                response(item("2395", "서울대입구", arrivalTime = "08:15:00")),
            ),
        )

        val result = repository(api, store).getDestinationStopDecision(
            arrival("2395"),
            "강남",
            "서울대입구",
        )

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(listOf("2395", "2395"), api.requestedTrainNumbers())
        assertEquals(
            TrainNumberMappingKey("2호선", "2395", "내선", "성수", "일반", "평일"),
            store.requestedKeys.single(),
        )
    }

    @Test
    fun missingStaticMappingReturnsUnknownWithoutRuntimeDiscovery() = runTest {
        val store = FakeTrainNumberMappingStore(null)
        val api = FakeTimetableApi(emptyList())

        val result = repository(api, store).getDestinationStopDecision(
            arrival("2395"),
            "강남",
            "서울대입구",
        )

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.TIMETABLE_TRAIN_NUMBER_NOT_FOUND, result.diagnostic.reason)
        assertTrue(api.requestedUrls.isEmpty())
    }

    @Test
    fun staticMappingSupportsIdentityPrefixAndSuffixNumbers() = runTest {
        for ((realtimeNumber, timetableNumber) in listOf(
            "2395" to "2395",
            "00472" to "K472",
            "3014" to "3014K",
            "9679" to "E9679",
        )) {
            val api = FakeTimetableApi(
                listOf(
                    response(item(timetableNumber, "출발", departureTime = "08:03:00")),
                    response(item(timetableNumber, "중간", arrivalTime = "08:10:00")),
                ),
            )
            val result = repository(api, FakeTrainNumberMappingStore(timetableNumber))
                .getDestinationStopDecision(
                    arrival(realtimeNumber).copy(terminalStation = "도착"),
                    "출발",
                    "중간",
                )

            assertEquals(timetableNumber, result.diagnostic.resolvedTimetableTrainNumber)
        }
    }

    @Test
    fun mappedTrainMissingAtOriginReturnsUnknownWithoutAlternativeGuess() = runTest {
        val api = FakeTimetableApi(listOf(response()))

        val result = repository(api, FakeTrainNumberMappingStore("S4178"))
            .getDestinationStopDecision(arrival("4178"), "사당", "서울역")

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.TIMETABLE_TRAIN_NUMBER_NOT_FOUND, result.diagnostic.reason)
        assertEquals(listOf("S4178"), api.requestedTrainNumbers())
    }

    @Test
    fun destinationMissingForMappedTrainReturnsDoesNotStop() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("2395", "강남", departureTime = "08:03:00")),
                response(),
            ),
        )

        val result = repository(api, FakeTrainNumberMappingStore("2395"))
            .getDestinationStopDecision(arrival(), "강남", "서울대입구")

        assertEquals(DestinationStopStatus.DOES_NOT_STOP, result.status)
        assertEquals(DestinationStopReason.DESTINATION_TRAIN_NOT_FOUND, result.diagnostic.reason)
    }

    @Test
    fun destinationThatTrainPassedBeforeOriginReturnsDoesNotStop() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("2395", "강남", departureTime = "08:03:00")),
                response(item("2395", "서울대입구", arrivalTime = "07:50:00")),
            ),
        )

        val result = repository(api, FakeTrainNumberMappingStore("2395"))
            .getDestinationStopDecision(arrival(), "강남", "서울대입구")

        assertEquals(DestinationStopStatus.DOES_NOT_STOP, result.status)
        assertEquals(DestinationStopReason.DESTINATION_ALREADY_PASSED, result.diagnostic.reason)
    }

    @Test
    fun destinationEqualsTerminalReturnsStopsWithoutMappingOrApiCall() = runTest {
        val store = FakeTrainNumberMappingStore(null)
        val api = FakeTimetableApi(emptyList())

        val result = repository(api, store).getDestinationStopDecision(arrival(), "강남", "성수역")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(DestinationStopReason.TERMINAL_MATCH, result.diagnostic.reason)
        assertTrue(store.requestedKeys.isEmpty())
        assertTrue(api.requestedUrls.isEmpty())
    }

    @Test
    fun nonNumericRealtimeNumberReturnsUnknownBeforeMappingLookup() = runTest {
        val store = FakeTrainNumberMappingStore(null)
        val api = FakeTimetableApi(emptyList())

        val result = repository(api, store).getDestinationStopDecision(
            arrival("04A72"),
            "강남",
            "서울대입구",
        )

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.INVALID_REALTIME_TRAIN_NUMBER, result.diagnostic.reason)
        assertTrue(store.requestedKeys.isEmpty())
        assertTrue(api.requestedUrls.isEmpty())
    }

    @Test
    fun timetableApiErrorDoesNotChangeStaticMapping() = runTest {
        val store = FakeTrainNumberMappingStore("2395")
        val api = FakeTimetableApi(listOf(errorResponse("API unavailable")))

        try {
            repository(api, store).getDestinationStopDecision(arrival(), "강남", "서울대입구")
            fail("시간표 API 오류가 전파되어야 합니다.")
        } catch (error: SeoulApiException) {
            assertEquals("API unavailable", error.message)
        }
        assertEquals(1, store.requestedKeys.size)
    }

    @Test
    fun mappedTimetableUrlUsesDocumentedArguments() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("2395", "강남", departureTime = "08:03:00")),
                response(),
            ),
        )

        repository(api, FakeTrainNumberMappingStore("2395"))
            .getDestinationStopDecision(arrival(), "강남역", "서울대입구역")

        assertEquals(
            listOf(
                "test-key", "json", "getTrainSch", "1", "1000", "", "N", "내선", "평일",
                "2호선", "2395", "강남", "", "", "", "", "", "", "2026-09-21 08:00:00",
            ),
            api.requestedUrls.first().pathSegments,
        )
    }

    private fun repository(
        api: TimetableApi,
        store: TrainNumberMappingStore,
    ) = SeoulTrainStopRepository(api, "test-key", store, now)

    private fun arrival(trainNumber: String = "2395") = TrainArrival(
        id = "arrival-1",
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

    private fun item(
        trainNumber: String,
        stationName: String,
        arrivalTime: String? = null,
        departureTime: String? = null,
    ) = TrainScheduleItem(
        trainno = trainNumber,
        stnNm = stationName,
        trainArvlTm = arrivalTime,
        trainDptreTm = departureTime,
    )

    private fun response(vararg items: TrainScheduleItem) = TrainScheduleResponse(
        response = TrainScheduleEnvelope(
            header = TrainScheduleHeader(resultCode = "00", resultMsg = "NORMAL_CODE"),
            body = TrainScheduleBody(
                items = TrainScheduleItems(item = items.toList()),
                totalCount = items.size,
            ),
        ),
    )

    private fun errorResponse(message: String) = TrainScheduleResponse(
        response = TrainScheduleEnvelope(
            header = TrainScheduleHeader(resultCode = "ERROR", resultMsg = message),
        ),
    )
}

private class FakeTimetableApi(
    responses: List<TrainScheduleResponse>,
) : TimetableApi {
    private val queuedResponses = ArrayDeque(responses)
    val requestedUrls = mutableListOf<HttpUrl>()

    override suspend fun getTrainSchedule(url: HttpUrl): TrainScheduleResponse {
        requestedUrls += url
        return queuedResponses.removeFirst()
    }
}

private class FakeTrainNumberMappingStore(
    private val timetableTrainNumber: String?,
) : TrainNumberMappingStore {
    val requestedKeys = mutableListOf<TrainNumberMappingKey>()

    override suspend fun find(key: TrainNumberMappingKey): String? {
        requestedKeys += key
        return timetableTrainNumber
    }
}

private fun FakeTimetableApi.requestedTrainNumbers(): List<String> =
    requestedUrls.map { it.pathSegments[10] }
