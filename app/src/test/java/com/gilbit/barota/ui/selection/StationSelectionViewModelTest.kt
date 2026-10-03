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
    private val history = FakeStationUsageRepository()

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

    @Test
    fun onlyConfirmedSelectionRecordsRoleAndTimestamp() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.updateQuery("강남")
        viewModel.closeSelector()
        advanceUntilIdle()
        assertTrue(history.usage.value.isEmpty())
        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.selectStation(gangnam)
        viewModel.selectStation(gangnam) // duplicate picker event after dismissal
        viewModel.openSelector(SelectionTarget.DESTINATION)
        viewModel.selectStation(gangnam) // rejected
        viewModel.selectStation(seoul)
        advanceUntilIdle()
        assertEquals(1L, history.usage.value.getValue("gangnam").originCount)
        assertEquals(0L, history.usage.value.getValue("gangnam").destinationCount)
        assertEquals(1L, history.usage.value.getValue("seoul").destinationCount)
        assertTrue(history.usage.value.getValue("gangnam").lastUsedAt!! > 0)
        val before = history.usage.value
        viewModel.swapStations()
        viewModel.uiState.value // state read/recomposition does not invoke persistence
        advanceUntilIdle()
        assertEquals(before, history.usage.value)
        assertTrue(history.pairs.isEmpty())
    }

    @Test
    fun explicitReselectionCountsAndPersistedUsageIsLoadedByNewViewModel() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        repeat(2) {
            viewModel.openSelector(SelectionTarget.ORIGIN)
            viewModel.selectStation(seoul)
            advanceUntilIdle()
        }
        assertEquals(2L, history.usage.value.getValue("seoul").originCount)
        val restored = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(seoul), restored.uiState.value.frequentStations)
        assertEquals(listOf(gangnam, seoul), restored.uiState.value.filteredStations)
        assertEquals(2L, history.usage.value.getValue("seoul").totalCount)
    }

    @Test
    fun confirmedSearchRecordsOneDirectedPairAndDoesNotIncrementStations() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.selectStation(gangnam)
        viewModel.openSelector(SelectionTarget.DESTINATION)
        viewModel.selectStation(seoul)
        var navigations = 0
        viewModel.confirmSearch { from, to ->
            assertEquals(gangnam, from)
            assertEquals(seoul, to)
            navigations++
        }
        viewModel.confirmSearch { _, _ -> navigations++ }
        advanceUntilIdle()
        assertEquals(1, navigations)
        assertEquals(1, history.pairs.size)
        assertEquals("gangnam", history.pairs.single().originStationId)
        assertEquals(1L, history.usage.value.getValue("gangnam").totalCount)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun writeFailurePreservesSelectionAndNavigationAndAllowsSubsequentWrites() = runTest {
        history.failWrites = true
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.selectStation(gangnam)
        viewModel.openSelector(SelectionTarget.DESTINATION)
        viewModel.selectStation(seoul)
        var navigated = false
        viewModel.confirmSearch { _, _ -> navigated = true }
        advanceUntilIdle()
        assertTrue(navigated)
        assertEquals(gangnam, viewModel.uiState.value.origin)
        assertTrue(history.usage.value.isEmpty())
        history.failWrites = false
        viewModel.openSelector(SelectionTarget.ORIGIN)
        viewModel.selectStation(gangnam)
        advanceUntilIdle()
        assertEquals(1L, history.usage.value.getValue("gangnam").originCount)
    }

    private fun viewModel() = StationSelectionViewModel(
        repository = object : StationRepository {
            override suspend fun getStations() = listOf(gangnam, seoul)
        },
        usageRepository = history,
    )

    @Test fun shortcutsSaveLaunchOnceAndReturnToRecommendationsAfterRemoval() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val route = com.gilbit.barota.domain.RouteShortcut(gangnam, seoul)
        vm.toggleSavedRoute(route)
        vm.toggleSavedRoute(route)
        advanceUntilIdle()
        assertEquals(listOf(route), vm.uiState.value.savedRoutes)
        assertTrue(history.pairs.isEmpty())
        var launches = 0
        vm.launchRoute(route) { _, _ -> launches++ }
        vm.launchRoute(route) { _, _ -> launches++ }
        advanceUntilIdle()
        assertEquals(1, launches)
        assertEquals(1, history.pairs.size)
        assertTrue(history.usage.value.isEmpty())
        assertEquals(gangnam, vm.uiState.value.origin)
        assertEquals(seoul, vm.uiState.value.destination)
        assertTrue(vm.uiState.value.recommendedRoutes.isEmpty())
        vm.toggleSavedRoute(route)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.savedRoutes.isEmpty())
        assertEquals(listOf(route), vm.uiState.value.recommendedRoutes)
    }

    @Test fun failedShortcutWritesPreserveStateAndDoNotBlockLaunchOrLaterSave() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val route = com.gilbit.barota.domain.RouteShortcut(gangnam, seoul)
        history.failWrites = true
        vm.toggleSavedRoute(route)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.savedRoutes.isEmpty())
        assertFalse(vm.uiState.value.isSavingRoute)
        var launches = 0
        vm.launchRoute(route) { _, _ -> launches++ }
        advanceUntilIdle()
        assertEquals(1, launches)
        history.failWrites = false
        vm.toggleSavedRoute(route)
        advanceUntilIdle()
        assertEquals(listOf(route), vm.uiState.value.savedRoutes)
        history.failWrites = true
        vm.toggleSavedRoute(route)
        advanceUntilIdle()
        assertEquals(listOf(route), vm.uiState.value.savedRoutes)
    }

    @Test fun failedRouteReadsStillAllowManualSelectionAndSearch() = runTest {
        history.failRouteReads = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.openSelector(SelectionTarget.ORIGIN)
        vm.selectStation(gangnam)
        vm.openSelector(SelectionTarget.DESTINATION)
        vm.selectStation(seoul)
        var launches = 0
        vm.confirmSearch { _, _ -> launches++ }
        advanceUntilIdle()
        assertEquals(1, launches)
        assertTrue(vm.uiState.value.savedRoutes.isEmpty())
    }
}
