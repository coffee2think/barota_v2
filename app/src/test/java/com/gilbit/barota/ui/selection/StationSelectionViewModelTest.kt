package com.gilbit.barota.ui.selection

import com.gilbit.barota.data.model.Station
import com.gilbit.barota.data.repository.StationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationSelectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gangnam = Station("gangnam", "강남", listOf("2호선", "신분당선"))
    private val seoul = Station("seoul", "서울역", listOf("1호선", "4호선"))

    @Test
    fun selectingDifferentStationsEnablesSearch() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.selectStation(gangnam)
        viewModel.openSelector(SelectionTarget.DESTINATION)
        viewModel.selectStation(seoul)

        assertTrue(viewModel.uiState.value.canSearch)
        assertEquals(gangnam, viewModel.uiState.value.origin)
        assertEquals(seoul, viewModel.uiState.value.destination)
    }

    @Test
    fun selectingSameStationIsRejected() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.selectStation(gangnam)
        viewModel.openSelector(SelectionTarget.DESTINATION)

        viewModel.selectStation(gangnam)

        assertNull(viewModel.uiState.value.destination)
        assertFalse(viewModel.uiState.value.canSearch)
        assertEquals("출발역과 도착역은 달라야 해요.", viewModel.uiState.value.message)
    }

    @Test
    fun swapExchangesOriginAndDestination() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.selectStation(gangnam)
        viewModel.openSelector(SelectionTarget.DESTINATION)
        viewModel.selectStation(seoul)

        viewModel.swapStations()

        assertEquals(seoul, viewModel.uiState.value.origin)
        assertEquals(gangnam, viewModel.uiState.value.destination)
    }

    @Test
    fun queryFiltersByStationNameAndLine() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateQuery("강남")
        assertEquals(listOf(gangnam), viewModel.uiState.value.filteredStations)

        viewModel.updateQuery("1호선")
        assertEquals(listOf(seoul), viewModel.uiState.value.filteredStations)
    }

    private fun viewModel() = StationSelectionViewModel(
        repository = object : StationRepository {
            override suspend fun getStations() = listOf(gangnam, seoul)
        },
    )
}
