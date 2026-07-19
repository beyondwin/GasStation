package com.gasstation.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPreferencesDataStoreModuleTest {

    @Test
    fun `provider reuses one datastore across recreated singleton components`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val first = UserPreferencesDataStoreModule.provideUserPreferencesDataStore(context)
        val second = UserPreferencesDataStoreModule.provideUserPreferencesDataStore(context)

        assertSame(first, second)
    }
}
