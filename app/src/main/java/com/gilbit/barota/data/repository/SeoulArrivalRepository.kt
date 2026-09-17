package com.gilbit.barota.data.repository

import com.gilbit.barota.BuildConfig
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.remote.RealtimeArrivalDto
import com.gilbit.barota.data.remote.SeoulSubwayApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
class SeoulArrivalRepository @Inject constructor(
    private val api: SeoulSubwayApi,
    private val trainStopRepository: TrainStopRepository,
) : ArrivalRepository {
    override suspend fun getArrivals(stationName: String, destinationName: String): List<TrainArrival> {
        if (BuildConfig.SEOUL_SUBWAY_API_KEY.isBlank()) throw MissingApiKeyException()

        val response = api.getRealtimeArrivals(
            apiKey = BuildConfig.SEOUL_SUBWAY_API_KEY,
            stationName = stationName.removeSuffix("역"),
        )
        val resultCode = response.errorMessage?.code ?: response.code.orEmpty()
        val resultMessage = response.errorMessage?.message ?: response.message.orEmpty()
        if (resultCode.isNotBlank() && resultCode != "INFO-000") {
            throw SeoulApiException(resultMessage.ifBlank { "서울시 API 요청에 실패했습니다. ($resultCode)" })
        }

        val arrivals = response.realtimeArrivalList
            .filter { it.statnNm.removeSuffix("역") == stationName.removeSuffix("역") }
            .map(RealtimeArrivalDto::toDomain)
            .sortedWith(compareBy<TrainArrival> { it.line }.thenBy { it.direction }.thenBy { it.arrivalSeconds ?: Int.MAX_VALUE })

        return supervisorScope {
            val timetableConcurrency = Semaphore(4)
            arrivals.map { arrival ->
                async {
                    val status = timetableConcurrency.withPermit {
                        runCatching {
                            trainStopRepository.getDestinationStopStatus(
                                arrival = arrival,
                                originName = stationName,
                                destinationName = destinationName,
                            )
                        }.getOrDefault(com.gilbit.barota.data.model.DestinationStopStatus.UNKNOWN)
                    }
                    arrival.copy(destinationStopStatus = status)
                }
            }.map { it.await() }
        }
    }
}

private fun RealtimeArrivalDto.toDomain() = TrainArrival(
    id = listOf(subwayId, updnLine, btrainNo, rowNum).joinToString("-"),
    trainNumber = btrainNo,
    line = subwayLineName(subwayId),
    direction = updnLine.ifBlank { trainLineNm.substringAfterLast("-").trim() },
    terminalStation = bstatnNm.ifBlank { trainLineNm.substringBefore("행").trim() },
    arrivalMessage = arvlMsg2.ifBlank { arrivalTimeMessage(barvlDt.toIntOrNull()) },
    currentLocation = arvlMsg3,
    arrivalSeconds = barvlDt.toIntOrNull(),
    trainType = btrainSttus.ifBlank { "일반" },
    isLastTrain = lstcarAt == "1",
    receivedAt = recptnDt,
)

private fun arrivalTimeMessage(seconds: Int?): String = when {
    seconds == null -> "도착시간 확인 중"
    seconds < 60 -> "곧 도착"
    else -> "${seconds / 60}분 ${seconds % 60}초 후"
}

private fun subwayLineName(id: String): String = when (id) {
    "1001" -> "1호선"
    "1002" -> "2호선"
    "1003" -> "3호선"
    "1004" -> "4호선"
    "1005" -> "5호선"
    "1006" -> "6호선"
    "1007" -> "7호선"
    "1008" -> "8호선"
    "1009" -> "9호선"
    "1063" -> "경의중앙선"
    "1065" -> "공항철도"
    "1067" -> "경춘선"
    "1075" -> "수인분당선"
    "1077" -> "신분당선"
    "1092" -> "우이신설선"
    "1093" -> "서해선"
    "1032" -> "GTX-A"
    else -> "노선 $id"
}
