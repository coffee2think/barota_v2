package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival

interface TrainStopRepository {
    suspend fun getDestinationStopStatus(
        arrival: TrainArrival,
        originName: String,
        destinationName: String,
    ): DestinationStopStatus
}
