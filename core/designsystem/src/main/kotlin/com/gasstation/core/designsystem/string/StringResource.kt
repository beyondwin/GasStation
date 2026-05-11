package com.gasstation.core.designsystem.string

import android.content.Context
import androidx.annotation.StringRes

sealed interface StringResource {
    fun resolve(context: Context): String

    data class FromId(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : StringResource {
        override fun resolve(context: Context): String = if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args.toTypedArray())
        }
    }

    data class Raw(val value: String) : StringResource {
        override fun resolve(context: Context): String = value
    }

    companion object {
        fun fromId(@StringRes id: Int, args: List<Any> = emptyList()): StringResource = FromId(id, args)

        fun raw(value: String): StringResource = Raw(value)
    }
}
