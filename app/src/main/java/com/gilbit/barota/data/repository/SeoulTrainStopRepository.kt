package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopDecision
import com.gilbit.barota.data.model.DestinationStopDiagnostic
import com.gilbit.barota.data.model.DestinationStopReason
import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.model.TrainNumberMappingKey
import com.gilbit.barota.data.remote.TimetableApi
import com.gilbit.barota.data.remote.TrainScheduleItem
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Singleton
class SeoulTrainStopRepository private constructor(
    private val api: TimetableApi,
    private val apiKey: String,
    private val mappingStore: TrainNumberMappingStore,
    private val nowProvider: () -> LocalDateTime,
) : TrainStopRepository {
    @Inject
    constructor(
        api: TimetableApi,
        @Named("timetableApiKey") apiKey: String,
        mappingStore: TrainNumberMappingStore,
    ) : this(api, apiKey, mappingStore, { LocalDateTime.now(SEOUL_ZONE) })

    internal constructor(
        api: TimetableApi,
        apiKey: String,
        mappingStore: TrainNumberMappingStore,
        now: LocalDateTime,
    ) : this(api, apiKey, mappingStore, { now })

    override suspend fun getDestinationStopDecisions(
        arrivals: List<TrainArrival>,
        originName: String,
        destinationName: String,
    ): Map<String, DestinationStopDecision> {
        if (arrivals.isEmpty()) return emptyMap()

        val decisions = linkedMapOf<String, DestinationStopDecision>()
        val candidates = mutableListOf<Candidate>()
        for (arrival in arrivals) {
            when {
                arrival.terminalStation.sameStation(destinationName) -> {
                    decisions[arrival.id] = decision(DestinationStopStatus.STOPS, DestinationStopReason.TERMINAL_MATCH)
                }
                apiKey.isBlank() -> {
                    decisions[arrival.id] = decision(DestinationStopStatus.UNKNOWN, DestinationStopReason.MISSING_API_KEY)
                }
                arrival.trainNumber.isBlank() -> {
                    decisions[arrival.id] = decision(DestinationStopStatus.UNKNOWN, DestinationStopReason.MISSING_TRAIN_NUMBER)
                }
                arrival.trainNumber.numericCore() == null -> {
                    decisions[arrival.id] = decision(
                        DestinationStopStatus.UNKNOWN,
                        DestinationStopReason.INVALID_REALTIME_TRAIN_NUMBER,
                    )
                }
                arrival.timetableLineName == null -> {
                    decisions[arrival.id] = decision(DestinationStopStatus.UNKNOWN, DestinationStopReason.UNSUPPORTED_LINE)
                }
                else -> candidates += Candidate(
                    arrival = arrival,
                    realtimeNumericCore = checkNotNull(arrival.trainNumber.numericCore()),
                    key = arrival.mappingKey(nowProvider()),
                )
            }
        }

        val fallbackCandidates = mutableListOf<MappedCandidate>()
        for (candidate in candidates) {
            val mappedTrainNumber = mappingStore.find(candidate.key)
            if (mappedTrainNumber == null) {
                fallbackCandidates += MappedCandidate(candidate, null)
                continue
            }

            when (val lookup = scheduleRows(candidate.arrival, destinationName, mappedTrainNumber)) {
                is ScheduleLookup.Failure -> decisions[candidate.arrival.id] = lookupFailure(mappedTrainNumber)
                is ScheduleLookup.Success -> {
                    val matches = lookup.rows.findTrainAt(mappedTrainNumber, destinationName)
                    if (matches.isNotEmpty()) {
                        decisions[candidate.arrival.id] = DestinationStopDecision(
                            status = DestinationStopStatus.STOPS,
                            diagnostic = DestinationStopDiagnostic(
                                reason = DestinationStopReason.MAPPED_DESTINATION_FOUND,
                                destinationScheduleMatchCount = matches.size,
                                attemptedTimetableTrainNumbers = listOf(mappedTrainNumber),
                                resolvedTimetableTrainNumber = mappedTrainNumber,
                                existingTimetableTrainNumber = mappedTrainNumber,
                            ),
                        )
                    } else {
                        fallbackCandidates += MappedCandidate(candidate, mappedTrainNumber)
                    }
                }
            }
        }

        fallbackCandidates
            .groupBy {
                ScheduleContext(
                    checkNotNull(it.candidate.arrival.timetableLineName),
                    it.candidate.arrival.direction,
                )
            }
            .forEach { (_, group) ->
                resolveFallbackGroup(group, originName, destinationName, decisions)
            }

        return arrivals.associate { arrival ->
            arrival.id to (decisions[arrival.id] ?: decision(
                DestinationStopStatus.UNKNOWN,
                DestinationStopReason.LOOKUP_FAILED,
            ))
        }
    }

    private suspend fun resolveFallbackGroup(
        group: List<MappedCandidate>,
        originName: String,
        destinationName: String,
        decisions: MutableMap<String, DestinationStopDecision>,
    ) {
        val representative = group.first().candidate.arrival
        val destinationLookup = scheduleRows(representative, destinationName, "")
        if (destinationLookup is ScheduleLookup.Failure) {
            group.forEach { mapped -> decisions[mapped.candidate.arrival.id] = lookupFailure(mapped.existingMapping) }
            return
        }
        val destinationRows = (destinationLookup as ScheduleLookup.Success).rows

        for (mapped in group) {
            val candidate = mapped.candidate
            val discovered = destinationRows
                .filter { row ->
                    row.stnNm.sameStation(destinationName) &&
                        row.trainno.numericCore() == candidate.realtimeNumericCore
                }
                .map { it.trainno }
                .filter(String::isNotBlank)
                .distinct()

            when {
                discovered.isEmpty() -> {
                    decisions[candidate.arrival.id] = DestinationStopDecision(
                        status = DestinationStopStatus.DOES_NOT_STOP,
                        diagnostic = fallbackDiagnostic(
                            reason = DestinationStopReason.DESTINATION_CANDIDATE_NOT_FOUND,
                            mapped = mapped,
                            discovered = discovered,
                            destinationCount = 0,
                        ),
                    )
                }
                discovered.size > 1 -> {
                    decisions[candidate.arrival.id] = DestinationStopDecision(
                        status = DestinationStopStatus.UNKNOWN,
                        diagnostic = fallbackDiagnostic(
                            reason = DestinationStopReason.AMBIGUOUS_DESTINATION_CANDIDATES,
                            mapped = mapped,
                            discovered = discovered,
                            destinationCount = destinationRows.count { row ->
                                row.stnNm.sameStation(destinationName) && row.trainno in discovered
                            },
                        ),
                    )
                }
                else -> resolveUniqueCandidate(
                    mapped = mapped,
                    trainNumber = discovered.single(),
                    discovered = discovered,
                    destinationRows = destinationRows,
                    originName = originName,
                    destinationName = destinationName,
                    decisions = decisions,
                )
            }
        }
    }

    private suspend fun resolveUniqueCandidate(
        mapped: MappedCandidate,
        trainNumber: String,
        discovered: List<String>,
        destinationRows: List<TrainScheduleItem>,
        originName: String,
        destinationName: String,
        decisions: MutableMap<String, DestinationStopDecision>,
    ) {
        val candidate = mapped.candidate
        when (val originLookup = scheduleRows(candidate.arrival, originName, trainNumber)) {
            is ScheduleLookup.Failure -> {
                decisions[candidate.arrival.id] = lookupFailure(
                    attemptedTrainNumber = trainNumber,
                    existingTrainNumber = mapped.existingMapping,
                    discovered = discovered,
                )
            }
            is ScheduleLookup.Success -> {
                val originMatches = originLookup.rows.findTrainAt(trainNumber, originName)
                if (originMatches.isEmpty()) {
                    decisions[candidate.arrival.id] = DestinationStopDecision(
                        status = DestinationStopStatus.UNKNOWN,
                        diagnostic = fallbackDiagnostic(
                            reason = DestinationStopReason.ORIGIN_VALIDATION_FAILED,
                            mapped = mapped,
                            discovered = discovered,
                            destinationCount = destinationRows.count {
                                it.trainno == trainNumber && it.stnNm.sameStation(destinationName)
                            },
                            originCount = 0,
                            resolved = trainNumber,
                        ),
                    )
                    return
                }

                mappingStore.upsert(candidate.key, trainNumber)
                val corrected = mapped.existingMapping != null && mapped.existingMapping != trainNumber
                decisions[candidate.arrival.id] = DestinationStopDecision(
                    status = DestinationStopStatus.STOPS,
                    diagnostic = fallbackDiagnostic(
                        reason = if (corrected) {
                            DestinationStopReason.CORRECTED_MAPPING_FOUND
                        } else {
                            DestinationStopReason.DISCOVERED_MAPPING_FOUND
                        },
                        mapped = mapped,
                        discovered = discovered,
                        destinationCount = destinationRows.count {
                            it.trainno == trainNumber && it.stnNm.sameStation(destinationName)
                        },
                        originCount = originMatches.size,
                        resolved = trainNumber,
                        mappingUpdated = true,
                    ),
                )
            }
        }
    }

    private suspend fun scheduleRows(
        arrival: TrainArrival,
        stationName: String,
        timetableTrainNumber: String,
    ): ScheduleLookup = try {
        ScheduleLookup.Success(getAllScheduleRows(arrival, stationName, timetableTrainNumber))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ScheduleLookup.Failure
    }

    private suspend fun getAllScheduleRows(
        arrival: TrainArrival,
        stationName: String,
        timetableTrainNumber: String,
    ): List<TrainScheduleItem> {
        val rows = mutableListOf<TrainScheduleItem>()
        var startIndex = 1
        while (true) {
            val endIndex = startIndex + PAGE_SIZE - 1
            val response = api.getTrainSchedule(
                buildUrl(arrival, stationName, timetableTrainNumber, startIndex, endIndex),
            ).response ?: throw SeoulApiException("열차시간표 API 응답 형식을 확인할 수 없습니다.")
            if (response.header.resultCode != "00") {
                throw SeoulApiException(response.header.resultMsg.ifBlank { "열차시간표 API 요청에 실패했습니다." })
            }
            val pageRows = response.body.items.item
            rows += pageRows
            if (endIndex >= response.body.totalCount) break
            if (pageRows.isEmpty()) throw SeoulApiException("열차시간표 API 페이지 응답이 완전하지 않습니다.")
            startIndex += PAGE_SIZE
        }
        return rows
    }

    private fun buildUrl(
        arrival: TrainArrival,
        stationName: String,
        timetableTrainNumber: String,
        startIndex: Int,
        endIndex: Int,
    ): HttpUrl {
        val now = nowProvider()
        val pathSegments = listOf(
            apiKey, "json", "getTrainSch", startIndex.toString(), endIndex.toString(), "", "N",
            arrival.direction, now.dayType(), checkNotNull(arrival.timetableLineName),
            timetableTrainNumber, stationName.removeSuffix("역"), "", "", "", "", "", "",
            now.format(SEARCH_DATE_TIME_FORMAT),
        )
        return BASE_URL.newBuilder().addPathSegments(pathSegments.joinToString("/")).build()
    }

    private companion object {
        const val PAGE_SIZE = 1000
        val BASE_URL = "http://openapi.seoul.go.kr:8088/".toHttpUrl()
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val SEARCH_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

private data class Candidate(
    val arrival: TrainArrival,
    val realtimeNumericCore: String,
    val key: TrainNumberMappingKey,
)

private data class MappedCandidate(val candidate: Candidate, val existingMapping: String?)

private data class ScheduleContext(val lineName: String, val direction: String)

private sealed interface ScheduleLookup {
    data class Success(val rows: List<TrainScheduleItem>) : ScheduleLookup
    data object Failure : ScheduleLookup
}

private fun TrainArrival.mappingKey(now: LocalDateTime): TrainNumberMappingKey = TrainNumberMappingKey(
    timetableLineName = checkNotNull(timetableLineName),
    realtimeTrainNumber = checkNotNull(trainNumber.numericCore()),
    direction = direction.trim(),
    terminalStation = terminalStation.normalizeTerminalName(),
    trainType = trainType.trim(),
    dayType = now.dayType(),
)

private fun fallbackDiagnostic(
    reason: DestinationStopReason,
    mapped: MappedCandidate,
    discovered: List<String>,
    destinationCount: Int,
    originCount: Int? = null,
    resolved: String? = null,
    mappingUpdated: Boolean = false,
) = DestinationStopDiagnostic(
    reason = reason,
    originScheduleMatchCount = originCount,
    destinationScheduleMatchCount = destinationCount,
    attemptedTimetableTrainNumbers = listOfNotNull(mapped.existingMapping, resolved).distinct(),
    resolvedTimetableTrainNumber = resolved,
    existingTimetableTrainNumber = mapped.existingMapping,
    discoveredTimetableTrainNumbers = discovered,
    mappingUpdated = mappingUpdated,
)

private fun lookupFailure(
    attemptedTrainNumber: String?,
    existingTrainNumber: String? = attemptedTrainNumber,
    discovered: List<String> = emptyList(),
) = DestinationStopDecision(
    status = DestinationStopStatus.UNKNOWN,
    diagnostic = DestinationStopDiagnostic(
        reason = DestinationStopReason.LOOKUP_FAILED,
        attemptedTimetableTrainNumbers = listOfNotNull(attemptedTrainNumber),
        existingTimetableTrainNumber = existingTrainNumber,
        discoveredTimetableTrainNumbers = discovered,
    ),
)

private fun decision(status: DestinationStopStatus, reason: DestinationStopReason) =
    DestinationStopDecision(status, DestinationStopDiagnostic(reason = reason))

private fun List<TrainScheduleItem>.findTrainAt(trainNumber: String, stationName: String) = filter {
    it.trainno == trainNumber && it.stnNm.sameStation(stationName)
}

private fun String.numericCore(): String? {
    val digits = filter(Char::isDigit)
    if (digits.isEmpty()) return null
    return digits.trimStart('0').ifEmpty { "0" }
}

private fun LocalDateTime.dayType(): String = when (dayOfWeek) {
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> "주말"
    else -> "평일"
}

private fun String.sameStation(other: String) = normalizeStationName() == other.normalizeStationName()

private fun String.normalizeStationName() = trim().removeSuffix("역").replace(" ", "")

private fun String.normalizeTerminalName() = normalizeStationName()
    .replace(TERMINAL_QUALIFIER, "")
    .removeSuffix("행")

private val TERMINAL_QUALIFIER = Regex("\\([^)]*\\)")
