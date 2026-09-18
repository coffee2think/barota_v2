package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.domain.DirectionalRoute

interface ArrivalRepository {
    suspend fun getArrivals(route: DirectionalRoute): List<TrainArrival>
}

class MissingApiKeyException : IllegalStateException(
    "서울시 API 키가 설정되지 않았습니다. local.properties에 SEOUL_SUBWAY_API_KEY를 입력해 주세요.",
)

class SeoulApiException(message: String) : IllegalStateException(message)

class ArrivalDirectionUnknownException : IllegalStateException(
    "응답의 열차 방향을 확인할 수 없습니다. 잠시 후 다시 조회해 주세요.",
)
