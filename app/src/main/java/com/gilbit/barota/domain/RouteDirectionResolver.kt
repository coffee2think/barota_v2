package com.gilbit.barota.domain

import javax.inject.Inject

enum class TravelDirection(val label: String) {
    UP("상행"), DOWN("하행"), INNER("내선"), OUTER("외선");

    companion object {
        // realtimeStationArrival returns labels, not realtimePosition's numeric codes.
        fun fromArrivalApi(value: String): TravelDirection? = entries.firstOrNull { it.label == value.trim() }
    }
}

data class DirectionalRoute(
    val originName: String,
    val destinationName: String,
    val line: String,
    val subwayId: String,
    val direction: TravelDirection,
)

sealed interface RouteDirectionResult {
    data class Resolved(val route: DirectionalRoute) : RouteDirectionResult
    data class LineSelectionRequired(val lines: List<String>) : RouteDirectionResult
    data class Unsupported(val message: String) : RouteDirectionResult
    data class Unknown(val message: String) : RouteDirectionResult
}

class RouteDirectionResolver @Inject constructor() {
    fun resolve(
        originName: String,
        destinationName: String,
        originLines: List<String>,
        destinationLines: List<String>,
        selectedLine: String? = null,
    ): RouteDirectionResult {
        val origin = canonicalName(originName)
        val destination = canonicalName(destinationName)
        if (origin.isBlank() || destination.isBlank()) {
            return RouteDirectionResult.Unknown("출발역과 도착역을 확인해 주세요.")
        }
        if (origin == destination) {
            return RouteDirectionResult.Unknown("출발역과 도착역은 달라야 합니다.")
        }
        val commonLines = originLines.intersect(destinationLines.toSet()).sorted()
        if (commonLines.isEmpty()) {
            return RouteDirectionResult.Unsupported("공통 노선이 없습니다. 환승 경로의 방향 판정은 아직 지원하지 않습니다.")
        }
        if (selectedLine != null && selectedLine !in commonLines) {
            return RouteDirectionResult.Unknown("선택한 노선이 두 역의 공통 노선인지 확인해 주세요.")
        }
        if (selectedLine == null && commonLines.size > 1) {
            return RouteDirectionResult.LineSelectionRequired(commonLines)
        }
        val line = selectedLine ?: commonLines.single()
        if (line != "1호선") {
            return RouteDirectionResult.Unsupported(
                if (line == "2호선") "2호선 내선·외선 및 지선의 방향 기준은 아직 지원하지 않습니다."
                else "$line 방향 기준은 아직 지원하지 않습니다.",
            )
        }
        val originIndex = LINE_ONE_DOWN_STATIONS.indexOf(origin)
        val destinationIndex = LINE_ONE_DOWN_STATIONS.indexOf(destination)
        if (originIndex < 0 || destinationIndex < 0) {
            return RouteDirectionResult.Unsupported("1호선 방향 판정은 현재 종로3가–독산 구간에서 지원합니다. 지선·다른 구간은 아직 지원하지 않습니다.")
        }
        return RouteDirectionResult.Resolved(
            DirectionalRoute(
                originName = origin,
                destinationName = destination,
                line = line,
                subwayId = "1001",
                direction = if (originIndex < destinationIndex) TravelDirection.DOWN else TravelDirection.UP,
            ),
        )
    }

    companion object {
        // Explicit railway order, NOT stations.json order or numerical station IDs.
        // Evidence and intentionally limited coverage: docs/development-log/2026-09-18-route-direction-implementation.md
        private val LINE_ONE_DOWN_STATIONS = listOf(
            "종로3가", "종각", "시청", "서울역", "남영", "용산", "노량진",
            "대방", "신길", "영등포", "신도림", "구로", "가산디지털단지", "독산",
        )

        fun canonicalName(value: String): String {
            val trimmed = value.trim()
            if (trimmed == "서울") return "서울역"
            if (trimmed in LINE_ONE_DOWN_STATIONS) return trimmed
            val withoutSuffix = trimmed.removeSuffix("역")
            return if (withoutSuffix in LINE_ONE_DOWN_STATIONS) withoutSuffix else trimmed
        }

        fun apiStationName(value: String): String = when (val name = canonicalName(value)) {
            "서울역" -> "서울"
            else -> name
        }
    }
}
