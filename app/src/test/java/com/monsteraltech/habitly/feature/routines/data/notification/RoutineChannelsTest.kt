package com.monsteraltech.habitly.feature.routines.data.notification

import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The level -> channel mapping is the one piece of the notification stack that is pure logic, and
 * getting it wrong would silently send every routine down the same channel.
 */
class RoutineChannelsTest {

    @Test
    fun `each level maps to its own channel`() {
        val ids = NotificationLevel.entries.map { RoutineChannels.channelIdFor(it) }

        assertEquals("ningún nivel debe compartir canal", ids.size, ids.toSet().size)
    }

    @Test
    fun `mapped channels are the ones the settings screen lists`() {
        NotificationLevel.entries.forEach { level ->
            assertTrue(
                "el canal de $level no está en allChannelIds",
                RoutineChannels.channelIdFor(level) in RoutineChannels.allChannelIds
            )
        }
    }

    @Test
    fun `silent and high map to distinct known channels`() {
        assertEquals(RoutineChannels.CHANNEL_SILENT, RoutineChannels.channelIdFor(NotificationLevel.SILENT))
        assertEquals(RoutineChannels.CHANNEL_DEFAULT, RoutineChannels.channelIdFor(NotificationLevel.DEFAULT))
        assertEquals(RoutineChannels.CHANNEL_HIGH, RoutineChannels.channelIdFor(NotificationLevel.HIGH))
    }
}
