package com.gilbit.barota.domain

import com.gilbit.barota.data.model.ArrivalState
import com.gilbit.barota.data.model.ArrivalTimeKind
import com.gilbit.barota.data.model.TrainArrival

data class ArrivalTime(val seconds: Int?, val kind: ArrivalTimeKind)

fun normalizeArrivalTime(rawSeconds: String, state: ArrivalState): ArrivalTime {
    val raw = rawSeconds.trim()
    if (raw.isEmpty()) return ArrivalTime(null, ArrivalTimeKind.MISSING)
    val seconds = raw.toIntOrNull() ?: return ArrivalTime(null, ArrivalTimeKind.INVALID)
    return when {
        seconds < 0 -> ArrivalTime(null, ArrivalTimeKind.NEGATIVE)
        seconds > 0 -> ArrivalTime(seconds, ArrivalTimeKind.ESTIMATED)
        state == ArrivalState.ENTERING || state == ArrivalState.ARRIVED ->
            ArrivalTime(0, ArrivalTimeKind.CONFIRMED_NOW)
        else -> ArrivalTime(null, ArrivalTimeKind.ZERO_UNCONFIRMED)
    }
}

fun TrainArrival.effectiveArrivalSeconds(): Int? = arrivalSeconds?.takeIf { seconds ->
    seconds > 0 || (seconds == 0 && arrivalState in setOf(ArrivalState.ENTERING, ArrivalState.ARRIVED))
}

// ordkey is direction(1), train ordinal(1), distance(3), terminal, express(1).
// The ordinal is only a tie breaker, never a cross-line ETA or a distance estimate.
// rowNum is a response row index and is intentionally not an ordering input.
internal fun arrivalOrdinal(key: String): Int? {
    val trimmed = key.trim()
    if (!ORDER_KEY_PATTERN.matches(trimmed)) return null
    return trimmed[1].digitToInt().takeIf { it > 0 }
}

private val ORDER_KEY_PATTERN = Regex("^[01][1-9][0-9]{3}.+[01]$")

fun orderIncomingArrivals(arrivals: List<TrainArrival>): List<TrainArrival> = arrivals
    .filter { it.arrivalState != ArrivalState.DEPARTED }
    .sortedWith(
        // A separate unknown-time flag keeps a valid Int.MAX_VALUE before unknown times.
        compareBy<TrainArrival> { it.effectiveArrivalSeconds() == null }
            .thenBy { it.effectiveArrivalSeconds() ?: 0 }
            .thenBy { arrivalOrdinal(it.arrivalOrderKey) ?: Int.MAX_VALUE }
            .thenBy { it.trainNumber }
            .thenBy { it.id },
    )
