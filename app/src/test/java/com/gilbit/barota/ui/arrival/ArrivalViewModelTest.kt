package com.gilbit.barota.ui.arrival

import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.repository.ArrivalRepository
import com.gilbit.barota.ui.selection.MainDispatcherRule
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
class ArrivalViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadShowsArrivalsAndReceivedTime() = runTest {
        val arrival = arrival()
        val viewModel = ArrivalViewModel(FakeArrivalRepository(result = listOf(arrival)))

        viewModel.load("강남", "서울역")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(arrival), viewModel.uiState.value.arrivals)
        assertEquals("2026-09-16 08:00:00", viewModel.uiState.value.updatedAt)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun failureShowsRetryableError() = runTest {
        val viewModel = ArrivalViewModel(FakeArrivalRepository(error = java.io.IOException()))

        viewModel.load("강남", "서울역")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.arrivals.isEmpty())
        assertEquals("네트워크 연결을 확인한 뒤 다시 시도해 주세요.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun repeatedLoadDoesNotCallRepositoryAgainUnlessForced() = runTest {
        val repository = FakeArrivalRepository(result = listOf(arrival()))
        val viewModel = ArrivalViewModel(repository)

        viewModel.load("강남", "서울역")
        advanceUntilIdle()
        viewModel.load("강남", "서울역")
        advanceUntilIdle()
        assertEquals(1, repository.callCount)

        viewModel.refresh("강남", "서울역")
        advanceUntilIdle()
        assertEquals(2, repository.callCount)
    }

    private fun arrival() = TrainArrival(
        id = "1002-상행-1234-1",
        trainNumber = "1234",
        line = "2호선",
        direction = "상행",
        terminalStation = "성수",
        arrivalMessage = "3분 후",
        currentLocation = "역삼",
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

    override suspend fun getArrivals(stationName: String, destinationName: String): List<TrainArrival> {
        callCount += 1
        error?.let { throw it }
        return result
    }
}
