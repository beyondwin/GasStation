package com.gasstation.core.database

import android.content.Context
import androidx.room.Room
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.WatchedStationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideGasStationDatabase(
        @ApplicationContext context: Context,
    ): GasStationDatabase = Room.databaseBuilder(
        context,
        GasStationDatabase::class.java,
        GasStationDatabase.DATABASE_NAME,
    ).addMigrations(
        GasStationDatabase.MIGRATION_1_2,
        GasStationDatabase.MIGRATION_2_3,
        GasStationDatabase.MIGRATION_3_4,
        GasStationDatabase.MIGRATION_4_5,
    )
        .build()

    @Provides
    fun provideStationCacheDao(
        database: GasStationDatabase,
    ): StationCacheDao = database.stationCacheDao()

    @Provides
    fun provideStationPriceHistoryDao(
        database: GasStationDatabase,
    ): StationPriceHistoryDao = database.stationPriceHistoryDao()

    @Provides
    fun provideWatchedStationDao(
        database: GasStationDatabase,
    ): WatchedStationDao = database.watchedStationDao()
}
