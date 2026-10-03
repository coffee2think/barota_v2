package com.gilbit.barota.ui.selection

import com.gilbit.barota.data.model.StationPairUsage
import com.gilbit.barota.data.model.SavedStationPair
import com.gilbit.barota.data.model.StationUsage
import com.gilbit.barota.data.model.StationUsageRole
import com.gilbit.barota.data.repository.StationUsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

class FakeStationUsageRepository : StationUsageRepository {
    val usage = MutableStateFlow<Map<String, StationUsage>>(emptyMap())
    val pairs = mutableListOf<StationPairUsage>()
    val pairFlow = MutableStateFlow<List<StationPairUsage>>(emptyList())
    val savedPairs = MutableStateFlow<List<SavedStationPair>>(emptyList())
    var failRouteReads = false
    override fun observePairs() = flow { check(!failRouteReads); emitAll(pairFlow) }
    override fun observeSavedPairs() = flow { check(!failRouteReads); emitAll(savedPairs) }
    override suspend fun savePair(originId: String, destinationId: String, savedAt: Long) {
        check(!failWrites)
        if (savedPairs.value.none { it.originStationId == originId && it.destinationStationId == destinationId })
            savedPairs.value += SavedStationPair(originId, destinationId, savedAt)
    }
    override suspend fun removeSavedPair(originId: String, destinationId: String) {
        check(!failWrites)
        savedPairs.value = savedPairs.value.filterNot { it.originStationId == originId && it.destinationStationId == destinationId }
    }
    var failWrites = false
    override fun observeUsage() = usage
    override suspend fun recordSelection(stationId: String, role: StationUsageRole, usedAt: Long) {
        check(!failWrites)
        val previous = usage.value[stationId] ?: StationUsage(stationId)
        val next = previous.copy(
            originCount = previous.originCount + if (role == StationUsageRole.ORIGIN) 1 else 0,
            destinationCount = previous.destinationCount + if (role == StationUsageRole.DESTINATION) 1 else 0,
            lastUsedAt = usedAt,
        )
        usage.value += stationId to next
    }
    override suspend fun recordPair(originId: String, destinationId: String, usedAt: Long) {
        check(!failWrites)
        pairs.add(StationPairUsage(originId, destinationId, 1, usedAt))
        val previous = pairFlow.value.find { it.originStationId == originId && it.destinationStationId == destinationId }
        pairFlow.value = pairFlow.value.filterNot { it == previous } + StationPairUsage(originId, destinationId, (previous?.count ?: 0) + 1, usedAt)
    }
    override suspend fun getPairs(originId: String) = pairs.filter { it.originStationId == originId }
}
