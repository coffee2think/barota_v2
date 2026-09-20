package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopDecision
import com.gilbit.barota.data.model.TrainArrival

interface TrainStopRepository {
    suspend fun getDestinationStopDecision(
        arrival: TrainArrival,
        originName: String,
        destinationName: String,
    ): DestinationStopDecision
}
