package com.gasstation.feature.stationlist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

internal class StationListCommandQueue @Inject constructor() {
    private val lock = Any()
    private var nextId = 1L
    private val mutableCommands = MutableStateFlow<List<StationListUiCommand>>(emptyList())

    val commands: StateFlow<List<StationListUiCommand>> = mutableCommands.asStateFlow()

    fun enqueue(payload: StationListCommandPayload): StationListUiCommand = synchronized(lock) {
        check(nextId != Long.MAX_VALUE) { "Station-list command id exhausted" }
        val command = StationListUiCommand(
            id = nextId++,
            payload = payload,
        )
        mutableCommands.value = mutableCommands.value + command
        command
    }

    fun acknowledge(commandId: Long) = synchronized(lock) {
        val current = mutableCommands.value
        if (current.firstOrNull()?.id == commandId) {
            mutableCommands.value = current.drop(1)
        }
    }
}
