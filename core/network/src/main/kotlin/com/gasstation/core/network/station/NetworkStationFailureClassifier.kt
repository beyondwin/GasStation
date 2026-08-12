package com.gasstation.core.network.station

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.util.Collections
import java.util.IdentityHashMap

internal fun Throwable.hasJsonParsingCause(): Boolean {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this

    while (current != null && visited.add(current)) {
        if (current is JsonParseException || current is MalformedJsonException) return true
        current = current.cause
    }

    return false
}
