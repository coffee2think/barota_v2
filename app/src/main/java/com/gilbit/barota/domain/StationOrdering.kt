package com.gilbit.barota.domain

import com.gilbit.barota.data.model.Station
import com.gilbit.barota.data.model.StationUsage

private val stationNameOrder = compareBy(Station::name, Station::id)

private fun usageOrder(usage: Map<String, StationUsage>) =
    compareByDescending<Station> { usage[it.id]?.totalCount ?: 0L }
        .thenByDescending { usage[it.id]?.lastUsedAt ?: Long.MIN_VALUE }
        .then(stationNameOrder)

fun frequentStations(stations: List<Station>, usage: Map<String, StationUsage>): List<Station> =
    stations.filter { (usage[it.id]?.totalCount ?: 0L) > 0L }
        .sortedWith(usageOrder(usage)).take(5)

fun orderStationResults(
    stations: List<Station>, query: String, usage: Map<String, StationUsage>,
): List<Station> {
    val normalized = query.trim()
    if (normalized.isBlank()) return stations.sortedWith(stationNameOrder)
    val historyOrder = usageOrder(usage)
    return stations.mapNotNull { station ->
        val match = when {
            station.name.equals(normalized, ignoreCase = true) -> 0
            station.name.startsWith(normalized, ignoreCase = true) -> 1
            station.name.contains(normalized, ignoreCase = true) -> 2
            station.lines.any { it.contains(normalized, ignoreCase = true) } -> 3
            else -> return@mapNotNull null
        }
        station to match
    }.sortedWith(compareBy<Pair<Station, Int>> { it.second }
        .thenComparator { a, b -> historyOrder.compare(a.first, b.first) })
        .map { it.first }
}
