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
    val destinationStopDiagnostic: DestinationStopDiagnostic = DestinationStopDiagnostic(),
    val arrivalState: ArrivalState = ArrivalState.UNKNOWN,
    val arrivalOrderKey: String = "",
    val arrivalTimeKind: ArrivalTimeKind = when {
        arrivalSeconds == null -> ArrivalTimeKind.MISSING
        arrivalSeconds < 0 -> ArrivalTimeKind.NEGATIVE
        arrivalSeconds > 0 -> ArrivalTimeKind.ESTIMATED
        arrivalState == ArrivalState.ENTERING || arrivalState == ArrivalState.ARRIVED -> ArrivalTimeKind.CONFIRMED_NOW
        else -> ArrivalTimeKind.ZERO_UNCONFIRMED
    },
    val timetableLineName: String? = null,
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

data class DestinationStopDecision(
    val status: DestinationStopStatus,
    val diagnostic: DestinationStopDiagnostic,
)

data class DestinationStopDiagnostic(
    val reason: DestinationStopReason = DestinationStopReason.NOT_EVALUATED,
    val originScheduleMatchCount: Int? = null,
    val destinationScheduleMatchCount: Int? = null,
    val attemptedTimetableTrainNumbers: List<String> = emptyList(),
    val resolvedTimetableTrainNumber: String? = null,
    val existingTimetableTrainNumber: String? = null,
    val discoveredTimetableTrainNumbers: List<String> = emptyList(),
    val mappingUpdated: Boolean = false,
)

enum class DestinationStopReason {
    NOT_EVALUATED,
    TERMINAL_MATCH,
    MISSING_API_KEY,
    MISSING_TRAIN_NUMBER,
    INVALID_REALTIME_TRAIN_NUMBER,
    UNSUPPORTED_LINE,
    MAPPED_DESTINATION_FOUND,
    DISCOVERED_MAPPING_FOUND,
    CORRECTED_MAPPING_FOUND,
    DESTINATION_CANDIDATE_NOT_FOUND,
    AMBIGUOUS_DESTINATION_CANDIDATES,
    ORIGIN_VALIDATION_FAILED,
    LOOKUP_FAILED,
}
