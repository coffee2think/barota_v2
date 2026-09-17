package com.gilbit.barota.ui.arrival

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.repository.ArrivalRepository
import com.gilbit.barota.data.repository.MissingApiKeyException
import com.gilbit.barota.data.repository.SeoulApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArrivalUiState(
    val isLoading: Boolean = false,
    val arrivals: List<TrainArrival> = emptyList(),
    val updatedAt: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ArrivalViewModel @Inject constructor(
    private val repository: ArrivalRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = _uiState.asStateFlow()
    private var loadedRoute: Pair<String, String>? = null

    fun load(stationName: String, destinationName: String, force: Boolean = false) {
        val route = stationName to destinationName
        if (!force && loadedRoute == route) return
        loadedRoute = route
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.getArrivals(stationName, destinationName) }
                .onSuccess { arrivals ->
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
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.toUserMessage())
                    }
                }
        }
    }

    fun refresh(stationName: String, destinationName: String) =
        load(stationName, destinationName, force = true)
}

private fun Throwable.toUserMessage(): String = when (this) {
    is MissingApiKeyException -> message.orEmpty()
    is SeoulApiException -> message ?: "서울시 API 요청에 실패했습니다."
    is IOException -> "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
    else -> "도착 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
}
