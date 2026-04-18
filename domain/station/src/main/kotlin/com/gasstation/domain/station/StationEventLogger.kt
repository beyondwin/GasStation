package com.gasstation.domain.station

import com.gasstation.domain.station.model.StationEvent

interface StationEventLogger {
    fun log(event: StationEvent)
}
