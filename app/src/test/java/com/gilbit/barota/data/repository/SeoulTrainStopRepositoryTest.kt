package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.DestinationStopReason
import com.gilbit.barota.data.model.TrainArrival
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SeoulTrainStopRepositoryTest {
    @Test
    fun leadingZeroRealtimeNumberUsesKCandidateAndDestinationScheduleReturnsStops() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("K472", "강남역", departureTime = "08:00:00")),
                response(item("K472", "서울역", arrivalTime = "08:10:00")),
            ),
        )
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopDecision(arrival("0472"), "강남", "서울역")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(DestinationStopReason.FUTURE_DESTINATION_FOUND, result.diagnostic.reason)
        assertEquals(1, result.diagnostic.originScheduleMatchCount)
        assertEquals(1, result.diagnostic.destinationScheduleMatchCount)
        assertEquals(listOf("K472"), result.diagnostic.attemptedTimetableTrainNumbers)
        assertEquals("K472", result.diagnostic.resolvedTimetableTrainNumber)
        assertEquals(listOf("K472", "K472"), api.requestedTrainNumbers())
        assertEquals(listOf("강남", "서울"), api.requestedStationNames())
        assertEquals(2, api.requestedUrls.size)
    }

    @Test
    fun emptyKOriginRetriesSAndUsesResolvedSNumberAtDestination() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(),
                response(item("S472", "강남역", departureTime = "08:00:00")),
                response(item("S472", "서울역", arrivalTime = "08:10:00")),
            ),
        )
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopDecision(arrival(" 00472 "), "강남", "서울역")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(listOf("K472", "S472"), result.diagnostic.attemptedTimetableTrainNumbers)
        assertEquals("S472", result.diagnostic.resolvedTimetableTrainNumber)
        assertEquals(listOf("K472", "S472", "S472"), api.requestedTrainNumbers())
    }

    @Test
    fun missingKAndSOriginSchedulesReturnsDistinctUnknownProblem() = runTest {
        val api = FakeTimetableApi(listOf(response(), response()))
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopDecision(arrival("0472"), "강남", "서울역")

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.TIMETABLE_TRAIN_NUMBER_NOT_FOUND, result.diagnostic.reason)
        assertEquals(0, result.diagnostic.originScheduleMatchCount)
        assertNull(result.diagnostic.destinationScheduleMatchCount)
        assertEquals(listOf("K472", "S472"), result.diagnostic.attemptedTimetableTrainNumbers)
        assertNull(result.diagnostic.resolvedTimetableTrainNumber)
        assertEquals(listOf("K472", "S472"), api.requestedTrainNumbers())
    }

    @Test
    fun resolvedKMissingAtDestinationReturnsDoesNotStopWithoutTryingS() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("K472", "강남역", departureTime = "08:00:00")),
                response(),
            ),
        )
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopDecision(arrival("0472"), "강남", "서울역")

        assertEquals(DestinationStopStatus.DOES_NOT_STOP, result.status)
        assertEquals(DestinationStopReason.DESTINATION_TRAIN_NOT_FOUND, result.diagnostic.reason)
        assertEquals(listOf("K472", "K472"), api.requestedTrainNumbers())
    }

    @Test
    fun destinationThatTrainPassedBeforeOriginReturnsDoesNotStop() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("K472", "강남역", departureTime = "08:00:00")),
                response(item("K472", "서울역", arrivalTime = "07:50:00")),
            ),
        )
        val result = SeoulTrainStopRepository(api, "test-key")
            .getDestinationStopDecision(arrival("0472"), "강남", "서울역")

        assertEquals(DestinationStopStatus.DOES_NOT_STOP, result.status)
        assertEquals(DestinationStopReason.DESTINATION_ALREADY_PASSED, result.diagnostic.reason)
    }

    @Test
    fun unsupportedLineReturnsUnknownWithoutApiCall() = runTest {
        val api = FakeTimetableApi(emptyList())
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopDecision(
            arrival().copy(line = "신분당선", timetableLineName = null),
            "강남",
            "서울역",
        )

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.UNSUPPORTED_LINE, result.diagnostic.reason)
        assertEquals(0, api.requestedUrls.size)
    }

    @Test
    fun destinationEqualsTerminalReturnsStopsWithoutApiCall() = runTest {
        val api = FakeTimetableApi(emptyList())
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopDecision(arrival(), "강남", "성수역")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(DestinationStopReason.TERMINAL_MATCH, result.diagnostic.reason)
        assertEquals(0, api.requestedUrls.size)
    }

    @Test
    fun zeroOnlyNumberNormalizesToKZero() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("K0", "강남", departureTime = "08:00:00")),
                response(item("K0", "서울", arrivalTime = "08:10:00")),
            ),
        )

        val result = SeoulTrainStopRepository(api, "test-key")
            .getDestinationStopDecision(arrival("0000"), "강남", "서울")

        assertEquals(DestinationStopStatus.STOPS, result.status)
        assertEquals(listOf("K0", "K0"), api.requestedTrainNumbers())
    }

    @Test
    fun nonNumericRealtimeNumberReturnsUnknownWithoutApiCall() = runTest {
        val api = FakeTimetableApi(emptyList())

        val result = SeoulTrainStopRepository(api, "test-key")
            .getDestinationStopDecision(arrival("04A72"), "강남", "서울")

        assertEquals(DestinationStopStatus.UNKNOWN, result.status)
        assertEquals(DestinationStopReason.INVALID_REALTIME_TRAIN_NUMBER, result.diagnostic.reason)
        assertTrue(api.requestedUrls.isEmpty())
    }

    @Test
    fun apiErrorDoesNotFallBackToS() = runTest {
        val api = FakeTimetableApi(listOf(errorResponse("API unavailable")))

        try {
            SeoulTrainStopRepository(api, "test-key")
                .getDestinationStopDecision(arrival("0472"), "강남", "서울")
            fail("API errors must not trigger an S fallback")
        } catch (error: SeoulApiException) {
            assertEquals("API unavailable", error.message)
        }
        assertEquals(listOf("K472"), api.requestedTrainNumbers())
    }

    @Test
    fun timetableUrlMatchesDocumentedArgumentsAndUsesFixedSeoulDate() = runTest {
        val api = FakeTimetableApi(listOf(response(), response()))
        val repository = SeoulTrainStopRepository(
            api,
            "test-key",
            LocalDateTime.of(2026, 9, 21, 8, 9, 10),
        )

        repository.getDestinationStopDecision(arrival("0472"), "강남역", "서울역")

        assertEquals(
            listOf(
                "test-key", "json", "getTrainSch", "1", "1000", "", "N", "내선", "평일",
                "2호선", "K472", "강남", "", "", "", "", "", "", "2026-09-21 08:09:10",
            ),
            api.requestedUrls.first().pathSegments,
        )
    }

    private fun arrival(trainNumber: String = "0472") = TrainArrival(
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
        receivedAt = "2026-09-16 08:00:00",
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

private fun FakeTimetableApi.requestedTrainNumbers(): List<String> =
    requestedUrls.map { it.pathSegments[10] }

private fun FakeTimetableApi.requestedStationNames(): List<String> =
    requestedUrls.map { it.pathSegments[11] }
