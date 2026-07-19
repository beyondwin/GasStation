package com.gasstation.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserPreferencesDataStoreModule {

    @Volatile
    private var processDataStore: DataStore<StoredUserPreferences>? = null

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(@ApplicationContext context: Context): DataStore<StoredUserPreferences> =
        processDataStore ?: synchronized(this) {
            processDataStore ?: DataStoreFactory.create(
                serializer = UserPreferencesSerializer,
                produceFile = {
                    context.applicationContext.filesDir.resolve(USER_PREFERENCES_FILE_NAME)
                },
            ).also { processDataStore = it }
        }

    @Provides
    @Singleton
    fun provideUserPreferencesDataSource(dataStore: DataStore<StoredUserPreferences>): UserPreferencesDataSource =
        AndroidUserPreferencesDataSource(dataStore)

    private const val USER_PREFERENCES_FILE_NAME = "user_preferences.pb"
}
