package com.example.taxicodegen;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import trading.OrderPojo;

/** Measures the demonstration lifecycle, including driver startup and cleanup, not IPC latency. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
public class AeronLifecycleBenchmark {
    private final OrderPojo order = new OrderPojo(123, 100500, 10);

    @Benchmark
    public OrderPojo isolatedDriverExchange() {
        return AeronDemoKt.exchangeOrder(order);
    }
}
