package com.gilbit.barota.data.repository

import com.gilbit.barota.domain.DirectionalRoute
import com.gilbit.barota.domain.TravelDirection
import com.gilbit.barota.domain.normalizeArrivalTime
import com.gilbit.barota.domain.orderIncomingArrivals
import com.gilbit.barota.data.model.ArrivalState
import com.gilbit.barota.data.model.DestinationStopDecision
import com.gilbit.barota.data.model.DestinationStopDiagnostic
import com.gilbit.barota.data.model.DestinationStopReason
import com.gilbit.barota.data.model.DestinationStopStatus
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
            stationName = route.originApiName,
        )
        val resultCode = response.errorMessage?.code ?: response.code.orEmpty()
        val resultMessage = response.errorMessage?.message ?: response.message.orEmpty()
        if (resultCode == "INFO-200") return emptyList()
        if (resultCode.isNotBlank() && resultCode != "INFO-000") {
            throw SeoulApiException(resultMessage.ifBlank { "서울시 API 요청에 실패했습니다. ($resultCode)" })
        }

        val stationLineRows = response.realtimeArrivalList
            .filter {
                stationNamesMatch(it.statnNm, route.originApiName, route.originName) &&
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
        val arrivals = orderIncomingArrivals(
            filteredRows.map { it.toDomain(route.line, route.timetableLineName) },
        )

        return supervisorScope {
            val timetableConcurrency = Semaphore(4)
            arrivals.map { arrival ->
                async {
                    val decision = timetableConcurrency.withPermit {
                        runCatching {
                            trainStopRepository.getDestinationStopDecision(
                                arrival = arrival,
                                originName = route.originName,
                                destinationName = route.destinationName,
                            )
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            DestinationStopDecision(
                                status = DestinationStopStatus.UNKNOWN,
                                diagnostic = DestinationStopDiagnostic(
                                    reason = DestinationStopReason.LOOKUP_FAILED,
                                ),
                            )
                        }
                    }
                    arrival.copy(
                        destinationStopStatus = decision.status,
                        destinationStopDiagnostic = decision.diagnostic,
                    )
                }
            }.map { it.await() }
        }
    }
}

private fun RealtimeArrivalDto.toDomain(
    routeLineName: String,
    timetableLineName: String?,
): TrainArrival {
    val state = ArrivalState.fromApi(arvlCd)
    val time = normalizeArrivalTime(barvlDt, state)
    return TrainArrival(
        id = listOf(subwayId, updnLine, btrainNo, rowNum).joinToString("-"),
        trainNumber = btrainNo,
        line = routeLineName,
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
        timetableLineName = timetableLineName,
    )
}

private fun arrivalTimeMessage(seconds: Int?): String = when {
    seconds == null -> "도착시간 확인 중"
    seconds < 60 -> "곧 도착"
    else -> "${seconds / 60}분 ${seconds % 60}초 후"
}

private fun stationNamesMatch(
    actual: String,
    apiName: String,
    canonicalName: String,
): Boolean {
    val actualNames = stationNameCandidates(actual)
    return actualNames.intersect(stationNameCandidates(apiName)).isNotEmpty() ||
        actualNames.intersect(stationNameCandidates(canonicalName)).isNotEmpty()
}

private fun stationNameCandidates(value: String): Set<String> {
    val trimmed = value.trim()
    val parenthetical = trimmed.substringAfter('(', missingDelimiterValue = "")
        .substringBeforeLast(')', missingDelimiterValue = "")
        .split(',')
    return buildSet {
        add(trimmed)
        add(trimmed.substringBefore('('))
        addAll(parenthetical)
    }.mapTo(mutableSetOf()) { name ->
        name.trim()
            .removeSuffix("역")
            .removeSuffix("순환")
            .replace(" ", "")
    }.filterTo(mutableSetOf(), String::isNotBlank)
}
