package com.gilbit.barota.data.model

data class TrainArrival(
    val id: String,
    val trainNumber: String,
    val line: String,
    val direction: String,
    val terminalStation: String,
    val arrivalMessage: String,
    val currentLocation: String,
    val arrivalSeconds: Int?,
    val trainType: String,
    val isLastTrain: Boolean,
    val receivedAt: String,
    val destinationStopStatus: DestinationStopStatus = DestinationStopStatus.UNKNOWN,
)

enum class DestinationStopStatus {
    UNKNOWN,
    STOPS,
    DOES_NOT_STOP,
}
