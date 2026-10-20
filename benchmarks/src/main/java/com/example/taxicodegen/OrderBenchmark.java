package com.example.taxicodegen;

import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import trading.OrderDecoder;
import trading.OrderEncoder;
import trading.OrderFlyweight;
import trading.OrderPojo;

@State(Scope.Thread)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {
    "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED", "-Xms256m", "-Xmx256m"
})
public class OrderBenchmark {
    private UnsafeBuffer buffer;
    private OrderPojo pojo;
    private OrderDecoder decoder;
    private OrderFlyweight flyweight;
    private OrderEncoder encoder;

    @Setup
    public void setup() {
        buffer = new UnsafeBuffer(new byte[32]);
        pojo = new OrderPojo(123, 100500, 10);
        decoder = new OrderDecoder();
        flyweight = new OrderFlyweight();
        encoder = new OrderEncoder();
        encoder.wrapAndApplyHeader(buffer, 0).fromPojo(pojo);
    }

    @Benchmark
    public long existingPojoRead() {
        return pojo.getOrderId() + pojo.getPrice() + pojo.getQuantity();
    }

    @Benchmark
    public long decoderRead() {
        decoder.wrapAndApplyHeader(buffer, 0, 32);
        return decoder.orderId() + decoder.price() + decoder.quantity();
    }

    @Benchmark
    public long flyweightRead() {
        flyweight.wrap(buffer, 0, 32);
        return flyweight.getOrderId() + flyweight.getPrice() + flyweight.getQuantity();
    }

    @Benchmark
    public long materializeAndRead(Blackhole blackhole) {
        OrderPojo materialized = decoder.wrapAndApplyHeader(buffer, 0, 32).toPojo();
        // Force the owned object to escape, as it would when handed to downstream work.
        blackhole.consume(materialized);
        return materialized.getOrderId() + materialized.getPrice() + materialized.getQuantity();
    }

    @Benchmark
    public long encode() {
        encoder.wrapAndApplyHeader(buffer, 0).fromPojo(pojo);
        return buffer.getLong(8, java.nio.ByteOrder.LITTLE_ENDIAN);
    }
}
