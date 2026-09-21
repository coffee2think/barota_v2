package com.gilbit.barota.data.repository

import android.content.Context
import com.gilbit.barota.data.model.TrainNumberMappingAsset
import com.gilbit.barota.data.model.TrainNumberMappingKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class AssetTrainNumberMappingStore private constructor(
    private val json: Json,
    private val assetReader: () -> String,
) : TrainNumberMappingStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : this(
        json = json,
        assetReader = {
            context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        },
    )

    internal constructor(
        json: Json,
        assetContent: String,
    ) : this(json, { assetContent })

    private val mappingIndex: MappingIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val asset = json.decodeFromString<TrainNumberMappingAsset>(assetReader())
        check(asset.version == SUPPORTED_VERSION) {
            "지원하지 않는 열차번호 매핑 자산 버전입니다: ${asset.version}"
        }
        val exact = asset.mappings.associate { mapping -> mapping.key() to mapping.timetableTrainNumber }
        val unambiguousWithoutTerminal = asset.mappings
            .groupBy { mapping -> mapping.key().withoutTerminal() }
            .mapNotNull { (key, mappings) ->
                val timetableTrainNumbers = mappings.map { it.timetableTrainNumber }.distinct()
                if (timetableTrainNumbers.size == 1) key to timetableTrainNumbers.single() else null
            }
            .toMap()
        MappingIndex(exact, unambiguousWithoutTerminal)
    }

    private val runtimeMappings = ConcurrentHashMap<TrainNumberMappingKey, String>()

    override suspend fun find(key: TrainNumberMappingKey): String? = withContext(Dispatchers.IO) {
        runtimeMappings[key]
            ?: mappingIndex.exact[key]
            ?: mappingIndex.unambiguousWithoutTerminal[key.withoutTerminal()]
    }

    override suspend fun upsert(
        key: TrainNumberMappingKey,
        timetableTrainNumber: String,
    ) = withContext(Dispatchers.IO) {
        runtimeMappings[key] = timetableTrainNumber
    }

    private companion object {
        const val ASSET_NAME = "train_number_mappings.json"
        const val SUPPORTED_VERSION = 1
    }
}

private data class MappingIndex(
    val exact: Map<TrainNumberMappingKey, String>,
    val unambiguousWithoutTerminal: Map<TrainNumberMappingKeyWithoutTerminal, String>,
)

private data class TrainNumberMappingKeyWithoutTerminal(
    val timetableLineName: String,
    val realtimeTrainNumber: String,
    val direction: String,
    val trainType: String,
    val dayType: String,
)

private fun TrainNumberMappingKey.withoutTerminal(): TrainNumberMappingKeyWithoutTerminal =
    TrainNumberMappingKeyWithoutTerminal(
        timetableLineName = timetableLineName,
        realtimeTrainNumber = realtimeTrainNumber,
        direction = direction,
        trainType = trainType,
        dayType = dayType,
    )
