package com.gasstation.core.designsystem.string

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.designsystem.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StringResourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fromId_resolves_to_resource_value() {
        val resource = StringResource.fromId(R.string.test_hello)
        assertEquals("Hello", resource.resolve(context))
    }

    @Test
    fun fromId_with_args_substitutes() {
        val resource = StringResource.fromId(R.string.test_greeting, listOf("Kim"))
        assertEquals("Hello, Kim", resource.resolve(context))
    }

    @Test
    fun raw_returns_literal_value() {
        val resource = StringResource.raw("literal")
        assertEquals("literal", resource.resolve(context))
    }
}
