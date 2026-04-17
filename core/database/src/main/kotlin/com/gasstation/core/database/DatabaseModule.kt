package com.gasstation.core.database

import android.content.Context
import androidx.room.Room
import com.gasstation.core.database.station.StationCacheDao
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
    ).fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideStationCacheDao(
        database: GasStationDatabase,
    ): StationCacheDao = database.stationCacheDao()
}
