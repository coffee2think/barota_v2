package com.gilbit.barota.domain

import com.gilbit.barota.data.model.ArrivalState
import com.gilbit.barota.data.model.ArrivalTimeKind
import com.gilbit.barota.data.model.TrainArrival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalOrderingTest {
    @Test
    fun mapsOperatorStatesWithoutTreatingPreviousDepartureAsDepartureHere() {
        val states = listOf(ArrivalState.ENTERING, ArrivalState.ARRIVED, ArrivalState.DEPARTED,
            ArrivalState.PREVIOUS_DEPARTED, ArrivalState.PREVIOUS_ENTERING, ArrivalState.PREVIOUS_ARRIVED)
        states.forEachIndexed { index, state -> assertEquals(state, ArrivalState.fromApi(" $index ")) }
        assertEquals(ArrivalState.RUNNING, ArrivalState.fromApi("99"))
        assertEquals(ArrivalState.UNKNOWN, ArrivalState.fromApi("invalid"))
    }

    @Test
    fun distinguishesAllTimeKindsAndNeverInfersNowFromMissingOrInvalid() {
        listOf("120" to ArrivalTime(120, ArrivalTimeKind.ESTIMATED),
            "0" to ArrivalTime(null, ArrivalTimeKind.ZERO_UNCONFIRMED),
            "-1" to ArrivalTime(null, ArrivalTimeKind.NEGATIVE),
            " " to ArrivalTime(null, ArrivalTimeKind.MISSING),
            "abc" to ArrivalTime(null, ArrivalTimeKind.INVALID),
            "2147483648" to ArrivalTime(null, ArrivalTimeKind.INVALID)
        ).forEach { (raw, expected) -> assertEquals(expected, normalizeArrivalTime(raw, ArrivalState.RUNNING)) }
        listOf(ArrivalState.ENTERING, ArrivalState.ARRIVED).forEach { state ->
            assertEquals(ArrivalTime(0, ArrivalTimeKind.CONFIRMED_NOW), normalizeArrivalTime(" 0 ", state))
            assertNull(normalizeArrivalTime("", state).seconds)
            assertNull(normalizeArrivalTime("bad", state).seconds)
        }
    }

    @Test
    fun ordersByTimeNotLineOrDirectionThenOrdinalNumberAndId() {
        val expected = listOf(train("now", 0, state = ArrivalState.ARRIVED),
            train("z", 60, "01003청량리0", line = "9호선"),
            train("a", 60, "02001청량리0", line = "1호선"),
            train("b", 60, "02001청량리0"), train("later", 120), train("missing", null))
        assertEquals(expected, orderIncomingArrivals(expected.reversed()))
        assertEquals(expected, orderIncomingArrivals(expected.shuffled(kotlin.random.Random(42))))
        val tied = listOf(train("same", 30).copy(id = "b"), train("same", 30).copy(id = "a"))
        assertEquals(listOf("a", "b"), orderIncomingArrivals(tied).map { it.id })
    }

    @Test
    fun unconfirmedZeroAndNegativeStayBehindEvenMaximumValidTime() {
        val inputs = listOf(train("zero", 0), train("negative", -2), train("null", null), train("max", Int.MAX_VALUE))
        assertEquals(listOf("max", "negative", "null", "zero"), orderIncomingArrivals(inputs).map { it.id })
    }

    @Test
    fun departureHereIsExcludedBeforeOrderingButPreviousDepartureRemains() {
        val inputs = listOf(train("gone", 1, state = ArrivalState.DEPARTED),
            train("previous", 40, state = ArrivalState.PREVIOUS_DEPARTED))
        assertEquals(listOf("previous"), orderIncomingArrivals(inputs).map { it.id })
    }

    @Test
    fun malformedOrderKeysCannotSupplyAnOrdinal() {
        listOf("", "1", "0100청량리0", "01003청량리", "x1003청량리0", "00003청량리0").forEach {
            assertNull(arrivalOrdinal(it))
        }
        assertEquals(2, arrivalOrdinal(" 12002신창0 "))
        assertEquals(listOf("valid", "invalid"), orderIncomingArrivals(listOf(
            train("invalid", 30, "broken"), train("valid", 30, "02003청량리0"))).map { it.id })
    }

    private fun train(number: String, seconds: Int?, order: String = "", line: String = "2호선", state: ArrivalState = ArrivalState.RUNNING) =
        TrainArrival(number, number, line, "상행", "청량리", "", "", seconds, "일반", false, "",
            arrivalState = state, arrivalOrderKey = order)
}
