package com.gilbit.barota.domain

import com.gilbit.barota.data.model.RouteLine
import com.gilbit.barota.data.model.RouteNetwork
import com.gilbit.barota.data.model.RouteService
import com.gilbit.barota.data.repository.RouteNetworkRepository
import java.util.ArrayDeque
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
    val originApiName: String = originName,
    val timetableLineName: String? = null,
)

sealed interface RouteDirectionResult {
    data class Resolved(val route: DirectionalRoute) : RouteDirectionResult
    data class LineSelectionRequired(val lines: List<String>) : RouteDirectionResult
    data class Unsupported(val message: String) : RouteDirectionResult
    data class Unknown(val message: String) : RouteDirectionResult
}

class RouteDirectionResolver(private val network: RouteNetwork) {
    @Inject
    constructor(repository: RouteNetworkRepository) : this(repository.getRouteNetwork())

    init {
        validate(network)
    }

    fun resolve(
        originName: String,
        destinationName: String,
        originLines: List<String>,
        destinationLines: List<String>,
        selectedLine: String? = null,
    ): RouteDirectionResult {
        val origin = network.canonicalName(originName)
        val destination = network.canonicalName(destinationName)
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

        val lineName = selectedLine ?: commonLines.single()
        val line = network.lines.singleOrNull { it.line == lineName }
            ?: return RouteDirectionResult.Unsupported("$lineName 방향 기준은 아직 지원하지 않습니다.")
        val candidates = line.services.flatMap { service ->
            service.resolveDirections(origin, destination)
        }

        if (candidates.isEmpty()) {
            return RouteDirectionResult.Unsupported("${lineName}에서 선택한 두 역을 잇는 운행 경로는 아직 지원하지 않습니다.")
        }
        val shortestDistance = candidates.minOf { it.distance }
        val shortestDirections = candidates
            .filter { it.distance == shortestDistance }
            .map { it.direction }
            .distinct()
        if (shortestDirections.size > 1) {
            return RouteDirectionResult.Unknown("${lineName}에서 가능한 운행 방향이 여러 개입니다. 방향 선택이 필요합니다.")
        }
        return resolved(line, origin, destination, shortestDirections.single())
    }

    private fun resolved(
        line: RouteLine,
        origin: String,
        destination: String,
        direction: TravelDirection,
    ) = RouteDirectionResult.Resolved(
        DirectionalRoute(
            originName = origin,
            destinationName = destination,
            line = line.line,
            subwayId = line.subwayId,
            direction = direction,
            originApiName = network.apiStationName(origin),
            timetableLineName = line.timetableLineName,
        ),
    )

    private data class DirectionCandidate(
        val direction: TravelDirection,
        val distance: Int,
    )

    private fun RouteService.resolveDirections(origin: String, destination: String): List<DirectionCandidate> {
        val forward = TravelDirection.fromArrivalApi(directions.forward)!!
        val adjacency = edges.groupBy({ it[0] }, { it[1] })
        val reverseAdjacency = edges.groupBy({ it[1] }, { it[0] })
        return buildList {
            adjacency.shortestPathDistance(origin, destination)?.let { distance ->
                add(DirectionCandidate(forward, distance))
            }
            directions.reverse?.let { label ->
                val reverse = TravelDirection.fromArrivalApi(label)!!
                reverseAdjacency.shortestPathDistance(origin, destination)?.let { distance ->
                    add(DirectionCandidate(reverse, distance))
                }
            }
        }
    }

    private fun Map<String, List<String>>.shortestPathDistance(origin: String, destination: String): Int? {
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf(origin)
        queue.add(origin to 0)
        while (queue.isNotEmpty()) {
            val (current, distance) = queue.removeFirst()
            for (next in this[current].orEmpty()) {
                if (next == destination) return distance + 1
                if (visited.add(next)) queue.add(next to distance + 1)
            }
        }
        return null
    }

    private companion object {
        fun validate(network: RouteNetwork) {
            require(network.version > 0) { "Route network version must be positive." }
            require(network.lines.map { it.line }.distinct().size == network.lines.size) {
                "Route line names must be unique."
            }
            network.lines.forEach { line ->
                require(line.line.isNotBlank() && line.subwayId.isNotBlank()) { "Route line metadata is required." }
                require(line.timetableLineName == null || line.timetableLineName.isNotBlank()) {
                    "Timetable line name must be null or non-blank in ${line.line}."
                }
                require(line.services.map { it.id }.distinct().size == line.services.size) {
                    "Service IDs must be unique within ${line.line}."
                }
                line.services.forEach { service ->
                    val forward = TravelDirection.fromArrivalApi(service.directions.forward)
                    val reverse = service.directions.reverse?.let(TravelDirection::fromArrivalApi)
                    require(forward != null) {
                        "Unknown forward direction in ${line.line}/${service.id}."
                    }
                    require(service.directions.reverse == null || reverse != null) {
                        "Unknown reverse direction in ${line.line}/${service.id}."
                    }
                    require(reverse == null || forward != reverse) {
                        "Forward and reverse directions must differ in ${line.line}/${service.id}."
                    }
                    require(service.edges.isNotEmpty()) { "At least one edge is required in ${line.line}/${service.id}." }
                    require(service.edges.all { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() && it[0] != it[1] }) {
                        "Every edge must contain two different station names in ${line.line}/${service.id}."
                    }
                }
            }
        }
    }
}
