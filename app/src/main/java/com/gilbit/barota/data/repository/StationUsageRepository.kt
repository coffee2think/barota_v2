package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.StationPairUsage
import com.gilbit.barota.data.model.StationUsage
import com.gilbit.barota.data.model.StationUsageRole
import kotlinx.coroutines.flow.Flow

interface StationUsageRepository {
    fun observeUsage(): Flow<Map<String, StationUsage>>
    suspend fun recordSelection(stationId: String, role: StationUsageRole, usedAt: Long)
    suspend fun recordPair(originId: String, destinationId: String, usedAt: Long)
    suspend fun getPairs(originId: String): List<StationPairUsage>
}
