package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.TrainNumberMappingKey

interface TrainNumberMappingStore {
    suspend fun find(key: TrainNumberMappingKey): String?
}
