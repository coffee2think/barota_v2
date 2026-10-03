package com.gilbit.barota.data.model

enum class StationUsageRole { ORIGIN, DESTINATION }

data class SavedStationPair(val originStationId: String, val destinationStationId: String, val savedAt: Long)

data class StationUsage(
    val stationId: String,
    val originCount: Long = 0,
    val destinationCount: Long = 0,
    val lastUsedAt: Long? = null,
) {
    val totalCount: Long get() = originCount + destinationCount
}

data class StationPairUsage(
    val originStationId: String,
    val destinationStationId: String,
    val count: Long,
    val lastUsedAt: Long,
)
