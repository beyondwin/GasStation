package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StationListCommandHandlerTest {

    @Test
    fun `normal handler return acknowledges exact id once`() = runTest {
        val command = command(id = 41L)
        val handled = mutableListOf<StationListCommandPayload>()
        val acknowledgements = mutableListOf<Long>()

        handleAndAcknowledgeStationListCommand(
            command = command,
            handle = handled::add,
            acknowledge = acknowledgements::add,
        )

        assertEquals(listOf(command.payload), handled)
        assertEquals(listOf(41L), acknowledgements)
    }

    @Test
    fun `handler exception is not acknowledged`() = runTest {
        val expected = IllegalStateException("handler failed")
        val acknowledgements = mutableListOf<Long>()

        val actual = runCatching {
            handleAndAcknowledgeStationListCommand(
                command = command(id = 42L),
                handle = { throw expected },
                acknowledge = acknowledgements::add,
            )
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertTrue(acknowledgements.isEmpty())
    }

    @Test
    fun `handler cancellation is propagated and not acknowledged`() = runTest {
        val expected = CancellationException("handler cancelled")
        val acknowledgements = mutableListOf<Long>()

        val actual = runCatching {
            handleAndAcknowledgeStationListCommand(
                command = command(id = 43L),
                handle = { throw expected },
                acknowledge = acknowledgements::add,
            )
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertTrue(acknowledgements.isEmpty())
    }

    private fun command(id: Long) = StationListUiCommand(
        id = id,
        payload = StationListCommandPayload.ShowSnackbar(StringResource.raw("message-$id")),
    )
}
