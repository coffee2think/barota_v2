package com.gilbit.barota.ui.arrival

import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.repository.ArrivalRepository
import com.gilbit.barota.data.repository.ArrivalDirectionUnknownException
import com.gilbit.barota.domain.DirectionalRoute
import com.gilbit.barota.testing.RouteNetworkTestData
import com.gilbit.barota.domain.RouteDirectionResult
import com.gilbit.barota.domain.TravelDirection
import com.gilbit.barota.ui.selection.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadShowsArrivalsAndReceivedTime() = runTest {
        val arrival = arrival()
        val viewModel = ArrivalViewModel(FakeArrivalRepository(result = listOf(arrival)), RouteNetworkTestData.resolver())

        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선", "3호선", "5호선"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(arrival), viewModel.uiState.value.arrivals)
        assertEquals("2026-09-16 08:00:00", viewModel.uiState.value.updatedAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(TravelDirection.UP, (viewModel.uiState.value.routeDirection as RouteDirectionResult.Resolved).route.direction)
    }

    @Test
    fun failureShowsRetryableError() = runTest {
        val viewModel = ArrivalViewModel(FakeArrivalRepository(error = java.io.IOException()), RouteNetworkTestData.resolver())

        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.arrivals.isEmpty())
        assertEquals("네트워크 연결을 확인한 뒤 다시 시도해 주세요.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun repeatedLoadDoesNotCallRepositoryAgainUnlessForced() = runTest {
        val repository = FakeArrivalRepository(result = listOf(arrival()))
        val viewModel = ArrivalViewModel(repository, RouteNetworkTestData.resolver())

        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        advanceUntilIdle()
        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        advanceUntilIdle()
        assertEquals(1, repository.callCount)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, repository.callCount)
    }

    @Test
    fun unsupportedAndSelectionRequiredStatesDoNotCallRepositoryUntilLineIsResolved() = runTest {
        val repository = FakeArrivalRepository(result = listOf(arrival()))
        val viewModel = ArrivalViewModel(repository, RouteNetworkTestData.resolver())
        viewModel.load("독산", "강남", listOf("1호선"), listOf("2호선"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.routeDirection is RouteDirectionResult.Unsupported)
        assertEquals(0, repository.callCount)
        val lines = listOf("1호선", "2호선")
        viewModel.load("시청", "신도림", lines, lines)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.routeDirection is RouteDirectionResult.LineSelectionRequired)
        assertEquals(0, repository.callCount)
        viewModel.selectLine("1호선")
        advanceUntilIdle()
        assertEquals(1, repository.callCount)
        assertEquals("1호선", viewModel.uiState.value.selectedLine)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, repository.callCount)
        assertEquals("1호선", viewModel.uiState.value.selectedLine)
        viewModel.selectLine("2호선")
        advanceUntilIdle()
        val lineTwo = viewModel.uiState.value.routeDirection as RouteDirectionResult.Resolved
        assertEquals(TravelDirection.OUTER, lineTwo.route.direction)
        assertEquals(1, viewModel.uiState.value.arrivals.size)
        assertEquals(3, repository.callCount)
        viewModel.selectLine("1호선")
        advanceUntilIdle()
        assertEquals(4, repository.callCount)
    }

    @Test
    fun reversedRouteClearsOldArrivalsAndPassesDownDirection() = runTest {
        val repository = FakeArrivalRepository(result = listOf(arrival()))
        val viewModel = ArrivalViewModel(repository, RouteNetworkTestData.resolver())
        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        advanceUntilIdle()
        viewModel.load("종로3가", "독산", listOf("1호선"), listOf("1호선"))
        assertTrue(viewModel.uiState.value.arrivals.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(TravelDirection.UP, TravelDirection.DOWN), repository.routes.map { it.direction })
    }

    @Test
    fun latePreviousRouteCannotOverwriteNewRouteEvenIfCancellationIsIgnored() = runTest {
        val previous = CompletableDeferred<List<TrainArrival>>()
        val repository = object : ArrivalRepository {
            override suspend fun getArrivals(route: DirectionalRoute): List<TrainArrival> =
                if (route.direction == TravelDirection.UP) {
                    try {
                        withContext(NonCancellable) { previous.await() }
                    } catch (_: CancellationException) {
                        // Deliberately broken dependency: generation guard must still reject its result.
                        listOf(arrival().copy(id = "old-up"))
                    }
                }
                else listOf(arrival().copy(id = "new-down", direction = "하행"))
        }
        val viewModel = ArrivalViewModel(repository, RouteNetworkTestData.resolver())
        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        runCurrent()
        viewModel.load("종로3가", "독산", listOf("1호선"), listOf("1호선"))
        runCurrent()
        previous.complete(listOf(arrival().copy(id = "old-up")))
        advanceUntilIdle()
        assertEquals(listOf("new-down"), viewModel.uiState.value.arrivals.map { it.id })
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun lateRefreshCannotOverwriteNewerResponseForTheSameRoute() = runTest {
        val previous = CompletableDeferred<List<TrainArrival>>()
        var calls = 0
        val repository = object : ArrivalRepository {
            override suspend fun getArrivals(route: DirectionalRoute): List<TrainArrival> {
                if (++calls > 1) return listOf(arrival().copy(id = "latest"))
                return try {
                    withContext(NonCancellable) { previous.await() }
                } catch (_: CancellationException) {
                    listOf(arrival().copy(id = "stale"))
                }
            }
        }
        val viewModel = ArrivalViewModel(repository, RouteNetworkTestData.resolver())
        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        runCurrent()
        viewModel.refresh()
        runCurrent()
        previous.complete(listOf(arrival().copy(id = "stale")))
        advanceUntilIdle()
        assertEquals(listOf("latest"), viewModel.uiState.value.arrivals.map { it.id })
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun noTrainsIsResolvedStateNotUnsupportedState() = runTest {
        val viewModel = ArrivalViewModel(FakeArrivalRepository(), RouteNetworkTestData.resolver())
        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.routeDirection is RouteDirectionResult.Resolved)
        assertTrue(viewModel.uiState.value.arrivals.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun unknownArrivalDirectionHasSpecificErrorMessage() = runTest {
        val viewModel = ArrivalViewModel(FakeArrivalRepository(error = ArrivalDirectionUnknownException()), RouteNetworkTestData.resolver())
        viewModel.load("독산", "종로3가", listOf("1호선"), listOf("1호선"))
        advanceUntilIdle()
        assertEquals(ArrivalDirectionUnknownException().message, viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.arrivals.isEmpty())
    }

    private fun arrival() = TrainArrival(
        id = "1001-상행-1234-1",
        trainNumber = "1234",
        line = "1호선",
        direction = "상행",
        terminalStation = "청량리",
        arrivalMessage = "3분 후",
        currentLocation = "금천구청",
        arrivalSeconds = 180,
        trainType = "일반",
        isLastTrain = false,
        receivedAt = "2026-09-16 08:00:00",
    )
}

private class FakeArrivalRepository(
    private val result: List<TrainArrival> = emptyList(),
    private val error: Throwable? = null,
) : ArrivalRepository {
    var callCount = 0
    val routes = mutableListOf<DirectionalRoute>()

    override suspend fun getArrivals(route: DirectionalRoute): List<TrainArrival> {
        callCount += 1
        routes += route
        error?.let { throw it }
        return result
    }
}
