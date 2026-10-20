package com.example.taxicodegen

import io.aeron.Aeron
import io.aeron.FragmentAssembler
import io.aeron.Publication
import io.aeron.driver.MediaDriver
import io.aeron.driver.ThreadingMode
import org.agrona.concurrent.UnsafeBuffer
import trading.OrderEncoder
import trading.OrderFlyweight
import trading.OrderPojo
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

fun main() {
    println("Received through Aeron IPC: ${exchangeOrder()}")
}

/** Owns an isolated driver directory. The borrowed view never escapes the callback. */
fun exchangeOrder(expected: OrderPojo = OrderPojo(123, 100500, 10)): OrderPojo {
    val directory = Files.createTempDirectory("taxi-aeron-")
    val failure = AtomicReference<Throwable?>()
    try {
        val context =
            MediaDriver
                .Context()
                .aeronDirectoryName(directory.toString())
                .dirDeleteOnShutdown(true)
                .threadingMode(ThreadingMode.SHARED)
                .ipcTermBufferLength(64 * 1024)
                .errorHandler { failure.compareAndSet(null, it) }
        MediaDriver.launch(context).use { driver ->
            Aeron.connect(Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName())).use { aeron ->
                aeron.addSubscription("aeron:ipc", 1001).use { subscription ->
                    aeron.addPublication("aeron:ipc", 1001).use { publication ->
                        val buffer = UnsafeBuffer(ByteArray(OrderEncoder.HEADER_LENGTH + OrderEncoder.BLOCK_LENGTH))
                        OrderEncoder().wrapAndApplyHeader(buffer, 0).fromPojo(expected)
                        val view = OrderFlyweight()
                        var received: OrderPojo? = null
                        val handler =
                            FragmentAssembler { data, offset, length, _ ->
                                view.wrap(data, offset, length)
                                check(
                                    view.orderId == expected.orderId && view.price == expected.price && view.quantity == expected.quantity,
                                )
                                received = view.toPojo()
                            }
                        val deadline = System.nanoTime() + 10_000_000_000L

                        fun awaitProgress() {
                            failure.get()?.let { throw IllegalStateException("Aeron driver failed", it) }
                            check(System.nanoTime() < deadline) { "Timed out waiting for Aeron IPC" }
                            LockSupport.parkNanos(100_000)
                        }
                        while (true) {
                            val result = publication.offer(buffer)
                            if (result >= 0) break
                            check(result != Publication.CLOSED && result != Publication.MAX_POSITION_EXCEEDED) {
                                "Publication cannot send: $result"
                            }
                            awaitProgress()
                        }
                        while (received == null) {
                            subscription.poll(handler, 10)
                            awaitProgress()
                        }
                        return checkNotNull(received)
                    }
                }
            }
        }
    } finally {
        // Only the unique directory created by this invocation is eligible for cleanup.
        if (Files.exists(directory)) {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
