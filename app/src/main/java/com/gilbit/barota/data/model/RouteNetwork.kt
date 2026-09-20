package com.gilbit.barota.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RouteNetwork(
    val version: Int,
    val aliases: Map<String, String> = emptyMap(),
    val apiStationNames: Map<String, String> = emptyMap(),
    val lines: List<RouteLine>,
) {
    fun canonicalName(value: String): String {
        val trimmed = value.trim()
        return aliases[trimmed] ?: trimmed
    }

    fun apiStationName(canonicalName: String): String = apiStationNames[canonicalName] ?: canonicalName
}

@Serializable
data class RouteLine(
    val line: String,
    val subwayId: String,
    val timetableLineName: String? = null,
    val services: List<RouteService>,
)

@Serializable
data class RouteService(
    val id: String,
    val directions: ServiceDirections,
    val edges: List<List<String>>,
)

@Serializable
data class ServiceDirections(val forward: String, val reverse: String? = null)
