package com.gilbit.barota.data.model

import kotlinx.serialization.Serializable

data class TrainNumberMappingKey(
    val timetableLineName: String,
    val realtimeTrainNumber: String,
    val direction: String,
    val terminalStation: String,
    val trainType: String,
    val dayType: String,
)

@Serializable
data class TrainNumberMapping(
    val timetableLineName: String,
    val realtimeTrainNumber: String,
    val direction: String,
    val terminalStation: String,
    val trainType: String,
    val dayType: String,
    val timetableTrainNumber: String,
) {
    fun key(): TrainNumberMappingKey = TrainNumberMappingKey(
        timetableLineName = timetableLineName,
        realtimeTrainNumber = realtimeTrainNumber,
        direction = direction,
        terminalStation = terminalStation,
        trainType = trainType,
        dayType = dayType,
    )
}

@Serializable
data class TrainNumberMappingAsset(
    val version: Int,
    val generatedAt: String,
    val sourceDates: Map<String, String> = emptyMap(),
    val mappings: List<TrainNumberMapping>,
)
