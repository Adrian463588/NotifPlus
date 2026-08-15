package com.notifplus.service

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

/**
 * Non-blocking listener ingress. Android callbacks must return quickly, so the
 * consumer owns all extraction, file I/O, and Room work.
 */
internal class NotificationEventQueue<T> {
    private val channel = Channel<T>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    val depth: Int
        get() = pending.get().coerceAtLeast(0)

    fun offer(event: T): Boolean {
        pending.incrementAndGet()
        val result = channel.trySend(event)
        if (result.isFailure) pending.decrementAndGet()
        return result.isSuccess
    }

    suspend fun receive(): T? {
        val result = channel.receiveCatching()
        if (result.isSuccess) pending.decrementAndGet()
        return result.getOrNull()
    }

    fun close() {
        channel.close()
    }
}
