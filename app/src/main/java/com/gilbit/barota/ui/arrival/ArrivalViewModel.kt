package com.gilbit.barota.ui.arrival

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.repository.ArrivalRepository
import com.gilbit.barota.data.repository.MissingApiKeyException
import com.gilbit.barota.data.repository.SeoulApiException
import com.gilbit.barota.data.repository.ArrivalDirectionUnknownException
import com.gilbit.barota.domain.RouteDirectionResolver
import com.gilbit.barota.domain.RouteDirectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

data class ArrivalUiState(
    val isLoading: Boolean = false,
    val arrivals: List<TrainArrival> = emptyList(),
    val updatedAt: String? = null,
    val errorMessage: String? = null,
    val routeDirection: RouteDirectionResult? = null,
    val commonLines: List<String> = emptyList(),
    val selectedLine: String? = null,
)

@HiltViewModel
class ArrivalViewModel @Inject constructor(
    private val repository: ArrivalRepository,
    private val directionResolver: RouteDirectionResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = _uiState.asStateFlow()
    private data class RouteRequest(
        val originName: String,
        val destinationName: String,
        val originLines: List<String>,
        val destinationLines: List<String>,
        val selectedLine: String? = null,
    )
    private var loadedRoute: RouteRequest? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    fun load(
        stationName: String,
        destinationName: String,
        originLines: List<String>,
        destinationLines: List<String>,
    ) = load(RouteRequest(stationName, destinationName, originLines.toList(), destinationLines.toList()))

    private fun load(request: RouteRequest, force: Boolean = false) {
        if (!force && loadedRoute == request) return
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadedRoute = request
        val resolution = directionResolver.resolve(
            request.originName, request.destinationName,
            request.originLines, request.destinationLines, request.selectedLine,
        )
        _uiState.value = ArrivalUiState(
            isLoading = resolution is RouteDirectionResult.Resolved,
            routeDirection = resolution,
            commonLines = request.originLines.intersect(request.destinationLines.toSet()).sorted(),
            selectedLine = request.selectedLine ?: (resolution as? RouteDirectionResult.Resolved)?.route?.line,
        )
        if (resolution !is RouteDirectionResult.Resolved) return
        loadJob = viewModelScope.launch {
            runCatching { repository.getArrivals(resolution.route) }
                .onSuccess { arrivals ->
                    if (loadGeneration != generation) return@onSuccess
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            arrivals = arrivals,
                            updatedAt = arrivals.mapNotNull { arrival ->
                                arrival.receivedAt.takeIf(String::isNotBlank)
                            }.maxOrNull(),
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (loadGeneration != generation) return@onFailure
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.toUserMessage())
                    }
                }
        }
    }

    fun selectLine(line: String) {
        loadedRoute?.let { load(it.copy(selectedLine = line)) }
    }

    fun refresh() {
        loadedRoute?.let { load(it, force = true) }
    }
}

private fun Throwable.toUserMessage(): String = when (this) {
    is MissingApiKeyException -> message.orEmpty()
    is ArrivalDirectionUnknownException -> message.orEmpty()
    is SeoulApiException -> message ?: "서울시 API 요청에 실패했습니다."
    is IOException -> "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
    else -> "도착 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
}
