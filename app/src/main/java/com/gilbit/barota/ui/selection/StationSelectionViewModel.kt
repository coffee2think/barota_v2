package com.gilbit.barota.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gilbit.barota.data.model.Station
import com.gilbit.barota.data.repository.StationRepository
import com.gilbit.barota.data.repository.StationUsageRepository
import com.gilbit.barota.data.model.StationUsage
import com.gilbit.barota.data.model.StationUsageRole
import com.gilbit.barota.domain.frequentStations
import com.gilbit.barota.domain.RouteShortcut
import com.gilbit.barota.domain.savedRouteShortcuts
import com.gilbit.barota.domain.recommendedRouteShortcuts
import com.gilbit.barota.data.model.StationPairUsage
import com.gilbit.barota.data.model.SavedStationPair
import com.gilbit.barota.domain.orderStationResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel

enum class SelectionTarget { ORIGIN, DESTINATION }

data class StationSelectionUiState(
    val isLoading: Boolean = true,
    val stations: List<Station> = emptyList(),
    val filteredStations: List<Station> = emptyList(),
    val frequentStations: List<Station> = emptyList(),
    val isSubmitting: Boolean = false,
    val savedRoutes: List<RouteShortcut> = emptyList(),
    val recommendedRoutes: List<RouteShortcut> = emptyList(),
    val isSavingRoute: Boolean = false,
    val origin: Station? = null,
    val destination: Station? = null,
    val selectionTarget: SelectionTarget? = null,
    val query: String = "",
    val message: String? = null,
) {
    val canSearch: Boolean get() = origin != null && destination != null && !isSubmitting
}

@HiltViewModel
class StationSelectionViewModel @Inject constructor(
    private val repository: StationRepository,
    private val usageRepository: StationUsageRepository,
) : ViewModel() {
    private var usage: Map<String, StationUsage> = emptyMap()
    private var pairs: List<StationPairUsage> = emptyList()
    private var savedPairs: List<SavedStationPair> = emptyList()
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val _uiState = MutableStateFlow(StationSelectionUiState())
    val uiState: StateFlow<StationSelectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                usageRepository.observePairs().collect { pairs = it; _uiState.update { refreshLists(it) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(message = "경로 사용 이력을 불러오지 못했어요.") } }
        }
        viewModelScope.launch {
            try {
                usageRepository.observeSavedPairs().collect { savedPairs = it; _uiState.update { refreshLists(it) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(message = "저장한 경로를 불러오지 못했어요.") } }
        }
        viewModelScope.launch {
            for (write in writes) write()
        }
        viewModelScope.launch {
            try {
                usageRepository.observeUsage().collect { savedUsage ->
                    usage = savedUsage
                    _uiState.update { refreshLists(it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "사용 이력을 불러오지 못했어요. 기본 역 목록을 표시합니다.") }
            }
        }
        viewModelScope.launch {
            runCatching { repository.getStations() }
                .onSuccess { stations ->
                    _uiState.update {
                        refreshLists(it.copy(
                            isLoading = false,
                            stations = stations,
                            filteredStations = stations,
                        ))
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
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            refreshLists(it.copy(
                selectionTarget = target,
                query = "",
                filteredStations = it.stations,
                message = null,
            ))
        }
    }

    fun closeSelector() {
        _uiState.update { refreshLists(it.copy(selectionTarget = null, query = "")) }
    }

    fun updateQuery(query: String) {
        _uiState.update { state ->
            refreshLists(state.copy(query = query))
        }
    }

    fun selectStation(station: Station) {
        val state = _uiState.value
        val target = state.selectionTarget ?: return
        if (state.isSubmitting || state.stations.none { it.id == station.id }) return
        val opposite = if (target == SelectionTarget.ORIGIN) state.destination else state.origin
        if (station.id == opposite?.id) {
            _uiState.update { it.copy(message = "출발역과 도착역은 달라야 해요.") }
            return
        }
        // Consume the picker event synchronously. A second click after dismissal cannot record it again.
        _uiState.value = refreshLists(state.copy(
            origin = if (target == SelectionTarget.ORIGIN) station else state.origin,
            destination = if (target == SelectionTarget.DESTINATION) station else state.destination,
            selectionTarget = null, query = "", message = null,
        ))
        val usedAt = System.currentTimeMillis()
        val role = if (target == SelectionTarget.ORIGIN) StationUsageRole.ORIGIN else StationUsageRole.DESTINATION
        writes.trySend {
            try {
                usageRepository.recordSelection(station.id, role, usedAt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "역은 선택됐지만 사용 이력을 저장하지 못했어요.") }
            }
        }
    }

    fun confirmSearch(onSearch: (Station, Station) -> Unit) {
        val state = _uiState.value
        if (!state.canSearch) return
        val origin = state.origin ?: return
        val destination = state.destination ?: return
        executeSearch(origin, destination, onSearch)
    }

    fun launchRoute(route: RouteShortcut, onSearch: (Station, Station) -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting || state.isLoading || route.origin.id == route.destination.id) return
        val origin = state.stations.find { it.id == route.origin.id } ?: return
        val destination = state.stations.find { it.id == route.destination.id } ?: return
        _uiState.update { it.copy(origin = origin, destination = destination, message = null) }
        executeSearch(origin, destination, onSearch)
    }

    fun toggleSavedRoute(route: RouteShortcut) {
        val state = _uiState.value
        if (state.isSavingRoute || route.origin.id == route.destination.id ||
            state.stations.none { it.id == route.origin.id } || state.stations.none { it.id == route.destination.id }) return
        val isSaved = savedPairs.any { it.originStationId == route.origin.id && it.destinationStationId == route.destination.id }
        _uiState.update { it.copy(isSavingRoute = true) }
        writes.trySend {
            try {
                if (isSaved) usageRepository.removeSavedPair(route.origin.id, route.destination.id)
                else usageRepository.savePair(route.origin.id, route.destination.id, System.currentTimeMillis())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(message = "경로 저장 상태를 변경하지 못했어요.") } }
            finally { _uiState.update { it.copy(isSavingRoute = false) } }
        }
    }

    private fun executeSearch(origin: Station, destination: Station, onSearch: (Station, Station) -> Unit) {
        val usedAt = System.currentTimeMillis()
        _uiState.update { it.copy(isSubmitting = true) }
        writes.trySend {
            try {
                usageRepository.recordPair(origin.id, destination.id, usedAt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "경로 사용 이력을 저장하지 못했어요.") }
            }
            try {
                onSearch(origin, destination)
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun refreshLists(state: StationSelectionUiState) = state.copy(
        savedRoutes = savedRouteShortcuts(state.stations, savedPairs),
        recommendedRoutes = recommendedRouteShortcuts(state.stations, pairs, savedPairs),
        filteredStations = orderStationResults(state.stations, state.query, usage),
        frequentStations = frequentStations(state.stations, usage),
    )

    fun swapStations() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { state ->
            state.copy(origin = state.destination, destination = state.origin, message = null)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        writes.close()
        super.onCleared()
    }
}
