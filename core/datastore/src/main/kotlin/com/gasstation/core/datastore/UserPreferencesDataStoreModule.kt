package com.gasstation.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.gasstation.domain.settings.model.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserPreferencesDataStoreModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<UserPreferences> =
        DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            produceFile = { context.filesDir.resolve(USER_PREFERENCES_FILE_NAME) },
        )

    @Provides
    @Singleton
    fun provideUserPreferencesDataSource(
        dataStore: DataStore<UserPreferences>,
    ): UserPreferencesDataSource = AndroidUserPreferencesDataSource(dataStore)

    private const val USER_PREFERENCES_FILE_NAME = "user_preferences.pb"
}
