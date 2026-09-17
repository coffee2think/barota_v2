package com.gilbit.barota.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class TrainScheduleResponse(
    val response: TrainScheduleEnvelope? = null,
)

@Serializable
data class TrainScheduleEnvelope(
    val header: TrainScheduleHeader = TrainScheduleHeader(),
    val body: TrainScheduleBody = TrainScheduleBody(),
)

@Serializable
data class TrainScheduleHeader(
    val resultCode: String = "",
    val resultMsg: String = "",
)

@Serializable
data class TrainScheduleBody(
    val items: TrainScheduleItems = TrainScheduleItems(),
    val totalCount: Int = 0,
)

@Serializable
data class TrainScheduleItems(
    val item: List<TrainScheduleItem> = emptyList(),
)

@Serializable
data class TrainScheduleItem(
    val trainno: String = "",
    val upbdnbSe: String = "",
    val wkndSe: String = "",
    val lineNm: String = "",
    val stnNm: String = "",
    val stnCd: String = "",
    val dptreStnNm: String = "",
    val arvlStnNm: String = "",
    val trainDptreTm: String? = null,
    val trainArvlTm: String? = null,
    val tmprTmtblYn: String = "",
    val vldBgngDt: String? = null,
    val vldEndDt: String? = null,
    val etrnYn: String? = null,
    val lnkgTrainno: String? = null,
)
