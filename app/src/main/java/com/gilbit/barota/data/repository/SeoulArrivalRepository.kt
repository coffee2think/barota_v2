package com.gilbit.barota.data.repository

import com.gilbit.barota.domain.DirectionalRoute
import com.gilbit.barota.domain.RouteDirectionResolver
import com.gilbit.barota.domain.TravelDirection
import com.gilbit.barota.domain.normalizeArrivalTime
import com.gilbit.barota.domain.orderIncomingArrivals
import com.gilbit.barota.data.model.ArrivalState
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.remote.RealtimeArrivalDto
import com.gilbit.barota.data.remote.SeoulSubwayApi
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
class SeoulArrivalRepository @Inject constructor(
    private val api: SeoulSubwayApi,
    private val trainStopRepository: TrainStopRepository,
    @Named("subwayApiKey") private val apiKey: String,
) : ArrivalRepository {
    override suspend fun getArrivals(route: DirectionalRoute): List<TrainArrival> {
        if (apiKey.isBlank()) throw MissingApiKeyException()

        val response = api.getRealtimeArrivals(
            apiKey = apiKey,
            stationName = RouteDirectionResolver.apiStationName(route.originName),
        )
        val resultCode = response.errorMessage?.code ?: response.code.orEmpty()
        val resultMessage = response.errorMessage?.message ?: response.message.orEmpty()
        if (resultCode == "INFO-200") return emptyList()
        if (resultCode.isNotBlank() && resultCode != "INFO-000") {
            throw SeoulApiException(resultMessage.ifBlank { "서울시 API 요청에 실패했습니다. ($resultCode)" })
        }

        val stationLineRows = response.realtimeArrivalList
            .filter {
                RouteDirectionResolver.canonicalName(it.statnNm) == route.originName &&
                    it.subwayId == route.subwayId
            }
        val filteredRows = stationLineRows.filter { TravelDirection.fromArrivalApi(it.updnLine) == route.direction }
        val allowedDirections = if (route.direction in setOf(TravelDirection.INNER, TravelDirection.OUTER)) {
            setOf(TravelDirection.INNER, TravelDirection.OUTER)
        } else {
            setOf(TravelDirection.UP, TravelDirection.DOWN)
        }
        if (filteredRows.isEmpty() && stationLineRows.any { TravelDirection.fromArrivalApi(it.updnLine) !in allowedDirections }) {
            throw ArrivalDirectionUnknownException()
        }
        val arrivals = orderIncomingArrivals(filteredRows.map(RealtimeArrivalDto::toDomain))

        return supervisorScope {
            val timetableConcurrency = Semaphore(4)
            arrivals.map { arrival ->
                async {
                    val status = timetableConcurrency.withPermit {
                        runCatching {
                            trainStopRepository.getDestinationStopStatus(
                                arrival = arrival,
                                originName = route.originName,
                                destinationName = route.destinationName,
                            )
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            com.gilbit.barota.data.model.DestinationStopStatus.UNKNOWN
                        }
                    }
                    arrival.copy(destinationStopStatus = status)
                }
            }.map { it.await() }
        }
    }
}

private fun RealtimeArrivalDto.toDomain(): TrainArrival {
    val state = ArrivalState.fromApi(arvlCd)
    val time = normalizeArrivalTime(barvlDt, state)
    return TrainArrival(
        id = listOf(subwayId, updnLine, btrainNo, rowNum).joinToString("-"),
        trainNumber = btrainNo,
        line = subwayLineName(subwayId),
        direction = updnLine.trim(),
        terminalStation = bstatnNm.ifBlank { trainLineNm.substringBefore("행").trim() },
        arrivalMessage = arvlMsg2.ifBlank { arrivalTimeMessage(time.seconds) },
        currentLocation = arvlMsg3,
        arrivalSeconds = time.seconds,
        trainType = btrainSttus.ifBlank { "일반" },
        isLastTrain = lstcarAt == "1",
        receivedAt = recptnDt,
        arrivalState = state,
        arrivalOrderKey = ordkey,
        arrivalTimeKind = time.kind,
    )
}

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
