package com.notifplus.service

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationEventQueueTest {
    @Test
    fun offer_keeps_all_events_in_order_without_blocking() = runTest {
        val queue = NotificationEventQueue<Int>()

        repeat(100) { value ->
            assertThat(queue.offer(value)).isTrue()
        }
        assertThat(queue.depth).isEqualTo(100)

        val received = buildList {
            repeat(100) { add(requireNotNull(queue.receive())) }
        }

        assertThat(received).containsExactlyElementsIn(0 until 100).inOrder()
        assertThat(queue.depth).isEqualTo(0)
    }

    @Test
    fun close_stops_receiving_after_buffer_is_drained() = runTest {
        val queue = NotificationEventQueue<String>()
        queue.offer("event")
        queue.close()

        assertThat(queue.receive()).isEqualTo("event")
        assertThat(queue.receive()).isNull()
    }
}
