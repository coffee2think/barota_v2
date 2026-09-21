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

    override suspend fun getDestinationStopDecision(
        arrival: TrainArrival,
        originName: String,
        destinationName: String,
    ): DestinationStopDecision {
        if (arrival.terminalStation.sameStation(destinationName)) {
            return decision(DestinationStopStatus.STOPS, DestinationStopReason.TERMINAL_MATCH)
        }
        if (apiKey.isBlank()) {
            return decision(DestinationStopStatus.UNKNOWN, DestinationStopReason.MISSING_API_KEY)
        }
        if (arrival.trainNumber.isBlank()) {
            return decision(DestinationStopStatus.UNKNOWN, DestinationStopReason.MISSING_TRAIN_NUMBER)
        }
        val normalizedTrainNumber = arrival.trainNumber.normalizeRealtimeTrainNumber()
            ?: return decision(
                DestinationStopStatus.UNKNOWN,
                DestinationStopReason.INVALID_REALTIME_TRAIN_NUMBER,
            )
        if (arrival.timetableLineName == null) {
            return decision(DestinationStopStatus.UNKNOWN, DestinationStopReason.UNSUPPORTED_LINE)
        }

        val resolution = resolveTimetableTrainNumber(
            arrival = arrival,
            originName = originName,
            normalizedRealtimeTrainNumber = normalizedTrainNumber,
        )
        if (resolution == null) {
            return DestinationStopDecision(
                status = DestinationStopStatus.UNKNOWN,
                diagnostic = DestinationStopDiagnostic(
                    reason = DestinationStopReason.TIMETABLE_TRAIN_NUMBER_NOT_FOUND,
                    originScheduleMatchCount = 0,
                ),
            )
        }

        val destinationRows = getScheduleRows(arrival, destinationName, resolution.timetableTrainNumber)
        val destinationSchedules = destinationRows.findTrainAt(resolution.timetableTrainNumber, destinationName)
        val diagnosticCounts = DestinationStopDiagnostic(
            originScheduleMatchCount = resolution.originSchedules.size,
            destinationScheduleMatchCount = destinationSchedules.size,
            attemptedTimetableTrainNumbers = resolution.attemptedTrainNumbers,
            resolvedTimetableTrainNumber = resolution.timetableTrainNumber,
        )
        if (destinationSchedules.isEmpty()) {
            return DestinationStopDecision(
                status = DestinationStopStatus.DOES_NOT_STOP,
                diagnostic = diagnosticCounts.copy(reason = DestinationStopReason.DESTINATION_TRAIN_NOT_FOUND),
            )
        }

        // 같은 열차가 목적지역에 있었더라도 이미 출발역을 지나온 경로라면 승차 후에는 도달하지 않는다.
        val hasFutureStop = resolution.originSchedules.any { origin ->
            val originTime = origin.departureTimeSeconds() ?: return@any false
            destinationSchedules.any { destination ->
                val destinationTime = destination.arrivalTimeSeconds() ?: return@any false
                destinationTime > originTime
            }
        }
        val hasComparableTimes = resolution.originSchedules.any { it.departureTimeSeconds() != null } &&
            destinationSchedules.any { it.arrivalTimeSeconds() != null }
        return when {
            hasFutureStop -> DestinationStopDecision(
                DestinationStopStatus.STOPS,
                diagnosticCounts.copy(reason = DestinationStopReason.FUTURE_DESTINATION_FOUND),
            )
            hasComparableTimes -> DestinationStopDecision(
                DestinationStopStatus.DOES_NOT_STOP,
                diagnosticCounts.copy(reason = DestinationStopReason.DESTINATION_ALREADY_PASSED),
            )
            else -> DestinationStopDecision(
                DestinationStopStatus.UNKNOWN,
                diagnosticCounts.copy(reason = DestinationStopReason.SCHEDULE_TIME_UNAVAILABLE),
            )
        }
    }

    private suspend fun resolveTimetableTrainNumber(
        arrival: TrainArrival,
        originName: String,
        normalizedRealtimeTrainNumber: String,
    ): ResolvedTrainNumber? {
        val now = nowProvider()
        val key = TrainNumberMappingKey(
            timetableLineName = checkNotNull(arrival.timetableLineName),
            realtimeTrainNumber = normalizedRealtimeTrainNumber,
            direction = arrival.direction.trim(),
            terminalStation = arrival.terminalStation.normalizeTerminalName(),
            trainType = arrival.trainType.trim(),
            dayType = now.dayType(),
        )
        val timetableTrainNumber = mappingStore.find(key) ?: return null
        val originSchedules = getScheduleRows(arrival, originName, timetableTrainNumber)
            .findTrainAt(timetableTrainNumber, originName)
        if (originSchedules.isEmpty()) return null
        return ResolvedTrainNumber(
            timetableTrainNumber = timetableTrainNumber,
            originSchedules = originSchedules,
            attemptedTrainNumbers = listOf(timetableTrainNumber),
        )
    }

    private suspend fun getScheduleRows(
        arrival: TrainArrival,
        stationName: String,
        timetableTrainNumber: String,
    ): List<TrainScheduleItem> {
        val response = api.getTrainSchedule(
            buildUrl(arrival, stationName, timetableTrainNumber),
        ).response
            ?: throw SeoulApiException("열차시간표 API 응답 형식을 확인할 수 없습니다.")
        if (response.header.resultCode != "00") {
            throw SeoulApiException(
                response.header.resultMsg.ifBlank { "열차시간표 API 요청에 실패했습니다." },
            )
        }
        return response.body.items.item
    }

    private fun buildUrl(
        arrival: TrainArrival,
        stationName: String,
        timetableTrainNumber: String,
    ): HttpUrl {
        val now = nowProvider()
        val dayType = now.dayType()
        val pathSegments = listOf(
            apiKey,
            "json",
            "getTrainSch",
            "1",
            "1000",
            "",
            "N",
            arrival.direction,
            dayType,
            checkNotNull(arrival.timetableLineName),
            timetableTrainNumber,
            stationName.removeSuffix("역"),
            "", // stnCd: 시간표용 역 코드 데이터가 확보되기 전까지 역명으로 조회한다.
            "",
            "",
            "",
            "",
            "",
            now.format(SEARCH_DATE_TIME_FORMAT),
        )
        return BASE_URL.newBuilder()
            .addPathSegments(pathSegments.joinToString("/"))
            .build()
    }

    private companion object {
        val BASE_URL = "http://openapi.seoul.go.kr:8088/".toHttpUrl()
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val SEARCH_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

private data class ResolvedTrainNumber(
    val timetableTrainNumber: String,
    val originSchedules: List<TrainScheduleItem>,
    val attemptedTrainNumbers: List<String>,
)

private fun decision(
    status: DestinationStopStatus,
    reason: DestinationStopReason,
): DestinationStopDecision = DestinationStopDecision(
    status = status,
    diagnostic = DestinationStopDiagnostic(reason = reason),
)

private fun List<TrainScheduleItem>.findTrainAt(
    trainNumber: String,
    stationName: String,
): List<TrainScheduleItem> = filter {
    it.trainno == trainNumber && it.stnNm.sameStation(stationName)
}

private fun String.normalizeRealtimeTrainNumber(): String? {
    val value = trim()
    if (value.isEmpty() || value.any { !it.isDigit() }) return null
    return value.trimStart('0').ifEmpty { "0" }
}

private fun TrainScheduleItem.departureTimeSeconds(): Int? =
    trainDptreTm.toServiceDaySeconds() ?: trainArvlTm.toServiceDaySeconds()

private fun TrainScheduleItem.arrivalTimeSeconds(): Int? =
    trainArvlTm.toServiceDaySeconds() ?: trainDptreTm.toServiceDaySeconds()

private fun String?.toServiceDaySeconds(): Int? {
    if (this == null) return null
    val match = TIME_AT_END.find(this) ?: return null
    val (hours, minutes, seconds) = match.destructured
    val hour = hours.toIntOrNull() ?: return null
    val minute = minutes.toIntOrNull() ?: return null
    val second = seconds.toIntOrNull() ?: return null
    if (minute !in 0..59 || second !in 0..59) return null
    return hour * 3600 + minute * 60 + second
}

private val TIME_AT_END = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})$")

private fun LocalDateTime.dayType(): String = when (dayOfWeek) {
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> "주말"
    else -> "평일"
}

private fun String.sameStation(other: String): Boolean =
    normalizeStationName() == other.normalizeStationName()

private fun String.normalizeStationName(): String = trim().removeSuffix("역").replace(" ", "")

private fun String.normalizeTerminalName(): String = normalizeStationName()
    .replace(TERMINAL_QUALIFIER, "")
    .removeSuffix("행")

private val TERMINAL_QUALIFIER = Regex("\\([^)]*\\)")
