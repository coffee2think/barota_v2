package com.gilbit.barota.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gilbit.barota.data.model.Station
import com.gilbit.barota.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SelectionTarget { ORIGIN, DESTINATION }

data class StationSelectionUiState(
    val isLoading: Boolean = true,
    val stations: List<Station> = emptyList(),
    val filteredStations: List<Station> = emptyList(),
    val origin: Station? = null,
    val destination: Station? = null,
    val selectionTarget: SelectionTarget? = null,
    val query: String = "",
    val message: String? = null,
) {
    val canSearch: Boolean get() = origin != null && destination != null
}

@HiltViewModel
class StationSelectionViewModel @Inject constructor(
    private val repository: StationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StationSelectionUiState())
    val uiState: StateFlow<StationSelectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.getStations() }
                .onSuccess { stations ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            stations = stations,
                            filteredStations = stations,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, message = "역 목록을 불러오지 못했어요.")
                    }
                }
        }
    }

    fun openSelector(target: SelectionTarget) {
        _uiState.update {
            it.copy(
                selectionTarget = target,
                query = "",
                filteredStations = it.stations,
                message = null,
            )
        }
    }

    fun closeSelector() {
        _uiState.update { it.copy(selectionTarget = null, query = "") }
    }

    fun updateQuery(query: String) {
        _uiState.update { state ->
            val normalized = query.trim()
            state.copy(
                query = query,
                filteredStations = if (normalized.isBlank()) {
                    state.stations
                } else {
                    state.stations.filter { station ->
                        station.name.contains(normalized, ignoreCase = true) ||
                            station.lines.any { it.contains(normalized, ignoreCase = true) }
                    }
                },
            )
        }
    }

    fun selectStation(station: Station) {
        _uiState.update { state ->
            when (state.selectionTarget) {
                SelectionTarget.ORIGIN -> {
                    if (station.id == state.destination?.id) {
                        state.copy(message = "출발역과 도착역은 달라야 해요.")
                    } else {
                        state.copy(origin = station, selectionTarget = null, query = "", message = null)
                    }
                }

                SelectionTarget.DESTINATION -> {
                    if (station.id == state.origin?.id) {
                        state.copy(message = "출발역과 도착역은 달라야 해요.")
                    } else {
                        state.copy(destination = station, selectionTarget = null, query = "", message = null)
                    }
                }

                null -> state
            }
        }
    }

    fun swapStations() {
        _uiState.update { state ->
            state.copy(origin = state.destination, destination = state.origin, message = null)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
