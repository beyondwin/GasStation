package com.gasstation.core.network.station

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException

internal fun Throwable.hasJsonParsingCause(): Boolean = generateSequence(this) { it.cause }
    .any { cause -> cause is JsonParseException || cause is MalformedJsonException }
