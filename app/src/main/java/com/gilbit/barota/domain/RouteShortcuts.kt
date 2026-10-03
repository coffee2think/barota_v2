package com.gilbit.barota.domain

import com.gilbit.barota.data.model.Station
import com.gilbit.barota.data.model.StationPairUsage
import com.gilbit.barota.data.model.SavedStationPair

data class RouteShortcut(val origin: Station, val destination: Station)

fun savedRouteShortcuts(stations: List<Station>, saved: List<SavedStationPair>): List<RouteShortcut> {
    val byId = stations.associateBy { it.id }
    return saved.sortedWith(compareByDescending<SavedStationPair> { it.savedAt }
        .thenBy { it.originStationId }.thenBy { it.destinationStationId }).mapNotNull {
        resolveRoute(byId, it.originStationId, it.destinationStationId)
    }
}

fun recommendedRouteShortcuts(
    stations: List<Station>, history: List<StationPairUsage>, saved: List<SavedStationPair>,
): List<RouteShortcut> {
    val byId = stations.associateBy { it.id }
    val savedKeys = saved.map { it.originStationId to it.destinationStationId }.toSet()
    return history.filter { it.count > 0 && (it.originStationId to it.destinationStationId) !in savedKeys }
        .mapNotNull { pair -> resolveRoute(byId, pair.originStationId, pair.destinationStationId)?.let { it to pair } }
        .sortedWith(compareByDescending<Pair<RouteShortcut, StationPairUsage>> { it.second.count }
            .thenByDescending { it.second.lastUsedAt }
            .thenBy { it.first.origin.name }.thenBy { it.first.destination.name }
            .thenBy { it.first.origin.id }.thenBy { it.first.destination.id })
        .take(3).map { it.first }
}

private fun resolveRoute(stations: Map<String, Station>, origin: String, destination: String): RouteShortcut? {
    if (origin == destination) return null
    return RouteShortcut(stations[origin] ?: return null, stations[destination] ?: return null)
}
