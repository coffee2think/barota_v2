package com.gilbit.barota.ui.selection

import com.gilbit.barota.data.model.StationPairUsage
import com.gilbit.barota.data.model.StationUsage
import com.gilbit.barota.data.model.StationUsageRole
import com.gilbit.barota.data.repository.StationUsageRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStationUsageRepository : StationUsageRepository {
    val usage = MutableStateFlow<Map<String, StationUsage>>(emptyMap())
    val pairs = mutableListOf<StationPairUsage>()
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
    }
    override suspend fun getPairs(originId: String) = pairs.filter { it.originStationId == originId }
}
