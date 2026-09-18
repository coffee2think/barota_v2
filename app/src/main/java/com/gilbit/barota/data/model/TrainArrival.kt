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
    val arrivalState: ArrivalState = ArrivalState.UNKNOWN,
    val arrivalOrderKey: String = "",
    val arrivalTimeKind: ArrivalTimeKind = when {
        arrivalSeconds == null -> ArrivalTimeKind.MISSING
        arrivalSeconds < 0 -> ArrivalTimeKind.NEGATIVE
        arrivalSeconds > 0 -> ArrivalTimeKind.ESTIMATED
        arrivalState == ArrivalState.ENTERING || arrivalState == ArrivalState.ARRIVED -> ArrivalTimeKind.CONFIRMED_NOW
        else -> ArrivalTimeKind.ZERO_UNCONFIRMED
    },
)

enum class ArrivalState {
    ENTERING, ARRIVED, DEPARTED, PREVIOUS_DEPARTED, PREVIOUS_ENTERING, PREVIOUS_ARRIVED, RUNNING, UNKNOWN;

    companion object {
        fun fromApi(code: String): ArrivalState = when (code.trim()) {
            "0" -> ENTERING
            "1" -> ARRIVED
            "2" -> DEPARTED
            "3" -> PREVIOUS_DEPARTED
            "4" -> PREVIOUS_ENTERING
            "5" -> PREVIOUS_ARRIVED
            "99" -> RUNNING
            else -> UNKNOWN
        }
    }
}

enum class ArrivalTimeKind {
    ESTIMATED, CONFIRMED_NOW, ZERO_UNCONFIRMED, NEGATIVE, MISSING, INVALID,
}

enum class DestinationStopStatus {
    UNKNOWN,
    STOPS,
    DOES_NOT_STOP,
}
