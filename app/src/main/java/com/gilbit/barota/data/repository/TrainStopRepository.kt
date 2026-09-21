package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.DestinationStopDecision
import com.gilbit.barota.data.model.TrainArrival

interface TrainStopRepository {
    suspend fun getDestinationStopDecisions(
        arrivals: List<TrainArrival>,
        originName: String,
        destinationName: String,
    ): Map<String, DestinationStopDecision>
}
