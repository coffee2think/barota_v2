package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival
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
class SeoulTrainStopRepository @Inject constructor(
    private val api: TimetableApi,
    @Named("timetableApiKey") private val apiKey: String,
) : TrainStopRepository {
    override suspend fun getDestinationStopStatus(
        arrival: TrainArrival,
        originName: String,
        destinationName: String,
    ): DestinationStopStatus {
        if (arrival.terminalStation.sameStation(destinationName)) return DestinationStopStatus.STOPS
        if (apiKey.isBlank() || arrival.trainNumber.isBlank() || arrival.line !in supportedLines) {
            return DestinationStopStatus.UNKNOWN
        }

        val destinationRows = getScheduleRows(arrival, destinationName)
        val originRows = getScheduleRows(arrival, originName)
        val originSchedules = originRows.findTrainAt(arrival.trainNumber, originName)
        if (originSchedules.isEmpty()) return DestinationStopStatus.UNKNOWN

        val destinationSchedules = destinationRows.findTrainAt(arrival.trainNumber, destinationName)
        if (destinationSchedules.isEmpty()) return DestinationStopStatus.DOES_NOT_STOP

        // 같은 열차가 목적지역에 있었더라도 이미 출발역을 지나온 경로라면 승차 후에는 도달하지 않는다.
        val hasFutureStop = originSchedules.any { origin ->
            val originTime = origin.departureTimeSeconds() ?: return@any false
            destinationSchedules.any { destination ->
                val destinationTime = destination.arrivalTimeSeconds() ?: return@any false
                destinationTime > originTime
            }
        }
        val hasComparableTimes = originSchedules.any { it.departureTimeSeconds() != null } &&
            destinationSchedules.any { it.arrivalTimeSeconds() != null }
        return when {
            hasFutureStop -> DestinationStopStatus.STOPS
            hasComparableTimes -> DestinationStopStatus.DOES_NOT_STOP
            else -> DestinationStopStatus.UNKNOWN
        }
    }

    private suspend fun getScheduleRows(arrival: TrainArrival, stationName: String): List<TrainScheduleItem> {
        val response = api.getTrainSchedule(buildUrl(arrival, stationName)).response
            ?: throw SeoulApiException("열차시간표 API 응답 형식을 확인할 수 없습니다.")
        if (response.header.resultCode != "00") {
            throw SeoulApiException(
                response.header.resultMsg.ifBlank { "열차시간표 API 요청에 실패했습니다." },
            )
        }
        return response.body.items.item
    }

    private fun buildUrl(arrival: TrainArrival, stationName: String): HttpUrl {
        val now = LocalDateTime.now(SEOUL_ZONE)
        val dayType = when (now.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> "주말"
            else -> "평일"
        }
        return BASE_URL.newBuilder()
            .addPathSegment(apiKey)
            .addPathSegment("json")
            .addPathSegment("getTrainSch")
            .addPathSegment("1")
            .addPathSegment("100")
            .addPathSegment("trainno,stnNm,lineNm,upbdnbSe")
            .addPathSegment("N")
            .addPathSegment(arrival.direction)
            .addPathSegment(dayType)
            .addPathSegment(arrival.line)
            .addPathSegment(arrival.trainNumber)
            .addPathSegment(stationName.removeSuffix("역"))
            .apply { repeat(6) { addPathSegment(" ") } }
            .addPathSegment(now.format(SEARCH_DATE_TIME_FORMAT))
            .build()
    }

    private companion object {
        val BASE_URL = "http://openapi.seoul.go.kr:8088/".toHttpUrl()
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val SEARCH_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val supportedLines = (1..9).map { "${it}호선" }.toSet()
    }
}

private fun List<TrainScheduleItem>.findTrainAt(
    trainNumber: String,
    stationName: String,
): List<TrainScheduleItem> = filter {
    it.trainno == trainNumber && it.stnNm.sameStation(stationName)
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

private fun String.sameStation(other: String): Boolean =
    normalizeStationName() == other.normalizeStationName()

private fun String.normalizeStationName(): String = trim().removeSuffix("역").replace(" ", "")
