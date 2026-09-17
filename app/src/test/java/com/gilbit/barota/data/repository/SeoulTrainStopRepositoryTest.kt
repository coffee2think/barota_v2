package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.remote.TimetableApi
import com.gilbit.barota.data.remote.TrainScheduleBody
import com.gilbit.barota.data.remote.TrainScheduleEnvelope
import com.gilbit.barota.data.remote.TrainScheduleHeader
import com.gilbit.barota.data.remote.TrainScheduleItem
import com.gilbit.barota.data.remote.TrainScheduleItems
import com.gilbit.barota.data.remote.TrainScheduleResponse
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class SeoulTrainStopRepositoryTest {
    @Test
    fun destinationScheduleContainsTrainReturnsStops() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("1234", "서울역", arrivalTime = "08:10:00")),
                response(item("1234", "강남역", departureTime = "08:00:00")),
            ),
        )
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopStatus(arrival(), "강남", "서울역")

        assertEquals(DestinationStopStatus.STOPS, result)
        assertEquals(2, api.requestedUrls.size)
    }

    @Test
    fun onlyOriginScheduleContainsTrainReturnsDoesNotStop() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(),
                response(item("1234", "강남역", departureTime = "08:00:00")),
            ),
        )
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopStatus(arrival(), "강남", "서울역")

        assertEquals(DestinationStopStatus.DOES_NOT_STOP, result)
        assertEquals(2, api.requestedUrls.size)
    }

    @Test
    fun trainMissingFromDestinationAndOriginReturnsUnknown() = runTest {
        val api = FakeTimetableApi(listOf(response(), response()))
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopStatus(arrival(), "강남", "서울역")

        assertEquals(DestinationStopStatus.UNKNOWN, result)
    }

    @Test
    fun destinationThatTrainPassedBeforeOriginReturnsDoesNotStop() = runTest {
        val api = FakeTimetableApi(
            listOf(
                response(item("1234", "서울역", arrivalTime = "07:50:00")),
                response(item("1234", "강남역", departureTime = "08:00:00")),
            ),
        )
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopStatus(arrival(), "강남", "서울역")

        assertEquals(DestinationStopStatus.DOES_NOT_STOP, result)
    }

    @Test
    fun unsupportedLineReturnsUnknownWithoutApiCall() = runTest {
        val api = FakeTimetableApi(emptyList())
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopStatus(
            arrival().copy(line = "신분당선"),
            "강남",
            "서울역",
        )

        assertEquals(DestinationStopStatus.UNKNOWN, result)
        assertEquals(0, api.requestedUrls.size)
    }

    @Test
    fun destinationEqualsTerminalReturnsStopsWithoutApiCall() = runTest {
        val api = FakeTimetableApi(emptyList())
        val repository = SeoulTrainStopRepository(api, "test-key")

        val result = repository.getDestinationStopStatus(arrival(), "강남", "성수역")

        assertEquals(DestinationStopStatus.STOPS, result)
        assertEquals(0, api.requestedUrls.size)
    }

    private fun arrival() = TrainArrival(
        id = "arrival-1",
        trainNumber = "1234",
        line = "2호선",
        direction = "내선",
        terminalStation = "성수",
        arrivalMessage = "3분 후",
        currentLocation = "역삼 출발",
        arrivalSeconds = 180,
        trainType = "일반",
        isLastTrain = false,
        receivedAt = "2026-09-16 08:00:00",
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
