package com.gilbit.barota.di

import com.gilbit.barota.BuildConfig
import com.gilbit.barota.data.remote.SeoulSubwayApi
import com.gilbit.barota.data.remote.TimetableApi
import com.gilbit.barota.data.repository.AssetStationRepository
import com.gilbit.barota.data.repository.AssetRouteNetworkRepository
import com.gilbit.barota.data.repository.AssetTrainNumberMappingStore
import com.gilbit.barota.data.repository.ArrivalRepository
import com.gilbit.barota.data.repository.SeoulArrivalRepository
import com.gilbit.barota.data.repository.SeoulTrainStopRepository
import com.gilbit.barota.data.repository.RouteNetworkRepository
import com.gilbit.barota.data.repository.StationRepository
import com.gilbit.barota.data.repository.TrainNumberMappingStore
import com.gilbit.barota.data.repository.TrainStopRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindRouteNetworkRepository(
        repository: AssetRouteNetworkRepository,
    ): RouteNetworkRepository

    @Binds
    @Singleton
    abstract fun bindStationRepository(
        repository: AssetStationRepository,
    ): StationRepository

    @Binds
    @Singleton
    abstract fun bindArrivalRepository(
        repository: SeoulArrivalRepository,
    ): ArrivalRepository

    @Binds
    @Singleton
    abstract fun bindTrainStopRepository(
        repository: SeoulTrainStopRepository,
    ): TrainStopRepository

    @Binds
    @Singleton
    abstract fun bindTrainNumberMappingStore(
        store: AssetTrainNumberMappingStore,
    ): TrainNumberMappingStore

    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        @Provides
        @Singleton
        fun provideRetrofit(json: Json): Retrofit = Retrofit.Builder()
            .baseUrl("http://swopenapi.seoul.go.kr/api/subway/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        @Provides
        @Singleton
        fun provideSeoulSubwayApi(retrofit: Retrofit): SeoulSubwayApi =
            retrofit.create(SeoulSubwayApi::class.java)

        @Provides
        @Singleton
        fun provideTimetableApi(retrofit: Retrofit): TimetableApi =
            retrofit.create(TimetableApi::class.java)

        @Provides
        @Named("subwayApiKey")
        fun provideSubwayApiKey(): String = BuildConfig.SEOUL_SUBWAY_API_KEY

        @Provides
        @Named("timetableApiKey")
        fun provideTimetableApiKey(): String = BuildConfig.SEOUL_TIMETABLE_API_KEY
    }
}
