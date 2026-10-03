package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.StationPairUsage
import com.gilbit.barota.data.model.SavedStationPair
import com.gilbit.barota.data.model.StationUsage
import com.gilbit.barota.data.model.StationUsageRole
import kotlinx.coroutines.flow.Flow

interface StationUsageRepository {
    fun observeUsage(): Flow<Map<String, StationUsage>>
    fun observePairs(): Flow<List<StationPairUsage>>
    fun observeSavedPairs(): Flow<List<SavedStationPair>>
    suspend fun savePair(originId: String, destinationId: String, savedAt: Long)
    suspend fun removeSavedPair(originId: String, destinationId: String)
    suspend fun recordSelection(stationId: String, role: StationUsageRole, usedAt: Long)
    suspend fun recordPair(originId: String, destinationId: String, usedAt: Long)
    suspend fun getPairs(originId: String): List<StationPairUsage>
}
