package com.lbc.common.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * RPC 框架统一指标采集（基于 Micrometer）
 *
 * 采集维度：
 * - rpc.server.requests：服务端请求数、耗时（Timer）
 * - rpc.server.errors：服务端错误计数（Counter）
 * - rpc.client.requests：客户端请求数、耗时（Timer）
 * - rpc.client.timeouts：客户端超时计数（Counter）
 * - rpc.circuit.breaker.state：熔断器状态（Gauge）
 * - rpc.rate.limit.rejects：限流拒绝计数（Counter）
 *
 * 使用方式：框架内部直接调用静态方法记录指标，由接入方决定如何导出（Prometheus/日志/JMX 等）。
 */
public class RpcMetrics {
    private static final Logger logger = LoggerFactory.getLogger(RpcMetrics.class);

    private static final MeterRegistry REGISTRY = new SimpleMeterRegistry();

    // ===== 指标名称常量 =====
    private static final String SERVER_REQUEST_TIMER = "rpc.server.requests";
    private static final String SERVER_ERROR_COUNTER = "rpc.server.errors";
    private static final String CLIENT_REQUEST_TIMER = "rpc.client.requests";
    private static final String CLIENT_TIMEOUT_COUNTER = "rpc.client.timeouts";
    private static final String CLIENT_ERROR_COUNTER = "rpc.client.errors";
    private static final String RATE_LIMIT_COUNTER = "rpc.rate.limit.rejects";
    private static final String CIRCUIT_BREAKER_STATE = "rpc.circuit.breaker.state";
    private static final String CIRCUIT_BREAKER_OPEN = "rpc.circuit.breaker.open";

    private static boolean enabled = true;

    private RpcMetrics() {
    }

    /**
     * 启用/禁用指标采集（默认启用）
     */
    public static void setEnabled(boolean enabled) {
        RpcMetrics.enabled = enabled;
    }

    /**
     * 获取全局 MeterRegistry，供接入方绑定 Prometheus 等导出器
     */
    public static MeterRegistry getRegistry() {
        return REGISTRY;
    }

    /**
     * 记录服务端请求
     *
     * @param interfaceName 接口名
     * @param methodName    方法名
     * @param durationMs    耗时（毫秒）
     * @param success       是否成功
     */
    public static void recordServerRequest(String interfaceName, String methodName,
                                            long durationMs, boolean success) {
        if (!enabled) {
            return;
        }
        try {
            Timer.builder(SERVER_REQUEST_TIMER)
                    .description("服务端请求耗时")
                    .tag("interface", interfaceName)
                    .tag("method", methodName)
                    .tag("result", success ? "success" : "error")
                    .publishPercentiles(0.5, 0.9, 0.99)
                    .register(REGISTRY)
                    .record(durationMs, TimeUnit.MILLISECONDS);

            if (!success) {
                Counter.builder(SERVER_ERROR_COUNTER)
                        .description("服务端错误计数")
                        .tag("interface", interfaceName)
                        .tag("method", methodName)
                        .register(REGISTRY)
                        .increment();
            }
        } catch (Exception e) {
            logger.warn("记录服务端指标异常", e);
        }
    }

    /**
     * 记录客户端请求
     *
     * @param interfaceName 接口名
     * @param methodName    方法名
     * @param durationMs    耗时（毫秒）
     * @param result        结果：success / timeout / error
     */
    public static void recordClientRequest(String interfaceName, String methodName,
                                            long durationMs, String result) {
        if (!enabled) {
            return;
        }
        try {
            Timer.builder(CLIENT_REQUEST_TIMER)
                    .description("客户端请求耗时")
                    .tag("interface", interfaceName)
                    .tag("method", methodName)
                    .tag("result", result)
                    .publishPercentiles(0.5, 0.9, 0.99)
                    .register(REGISTRY)
                    .record(durationMs, TimeUnit.MILLISECONDS);

            if ("timeout".equals(result)) {
                Counter.builder(CLIENT_TIMEOUT_COUNTER)
                        .description("客户端超时计数")
                        .tag("interface", interfaceName)
                        .tag("method", methodName)
                        .register(REGISTRY)
                        .increment();
            } else if ("error".equals(result)) {
                Counter.builder(CLIENT_ERROR_COUNTER)
                        .description("客户端错误计数")
                        .tag("interface", interfaceName)
                        .tag("method", methodName)
                        .register(REGISTRY)
                        .increment();
            }
        } catch (Exception e) {
            logger.warn("记录客户端指标异常", e);
        }
    }

    /**
     * 记录限流拒绝
     *
     * @param interfaceName 被限流的接口名
     */
    public static void recordRateLimitReject(String interfaceName) {
        if (!enabled) {
            return;
        }
        try {
            Counter.builder(RATE_LIMIT_COUNTER)
                    .description("限流拒绝计数")
                    .tag("interface", interfaceName)
                    .register(REGISTRY)
                    .increment();
        } catch (Exception e) {
            logger.warn("记录限流指标异常", e);
        }
    }

    /**
     * 记录熔断器状态变化
     *
     * @param serviceName 服务名
     * @param state       当前状态：CLOSED / OPEN / HALF_OPEN
     */
    public static void recordCircuitBreakerState(String serviceName, String state) {
        if (!enabled) {
            return;
        }
        try {
            Gauge.builder(CIRCUIT_BREAKER_STATE, () -> {
                        // 返回状态对应的数值：CLOSED=0, HALF_OPEN=1, OPEN=2
                        switch (state) {
                            case "CLOSED":
                                return 0;
                            case "HALF_OPEN":
                                return 1;
                            case "OPEN":
                                return 2;
                            default:
                                return -1;
                        }
                    })
                    .description("熔断器状态（0=关闭, 1=半开, 2=开启）")
                    .tag("service", serviceName)
                    .register(REGISTRY);

            if ("OPEN".equals(state)) {
                Counter.builder(CIRCUIT_BREAKER_OPEN)
                        .description("熔断器触发次数")
                        .tag("service", serviceName)
                        .register(REGISTRY)
                        .increment();
            }
        } catch (Exception e) {
            logger.warn("记录熔断器指标异常", e);
        }
    }

    /**
     * 输出当前所有指标的摘要（可定期调用打印到日志）
     */
    public static String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== RPC Metrics Summary ==========\n");
        for (Meter meter : REGISTRY.getMeters()) {
            if (meter instanceof Timer) {
                Timer timer = (Timer) meter;
                sb.append(String.format("[%s] count=%d, avg=%.2fms, p50=%.2fms, p99=%.2fms%n",
                        timer.getId().toString(),
                        timer.count(),
                        timer.mean(TimeUnit.MILLISECONDS),
                        timer.percentile(0.5, TimeUnit.MILLISECONDS),
                        timer.percentile(0.99, TimeUnit.MILLISECONDS)));
            } else if (meter instanceof Counter) {
                Counter counter = (Counter) meter;
                sb.append(String.format("[%s] count=%.0f%n",
                        counter.getId().toString(),
                        counter.count()));
            }
        }
        sb.append("==========================================");
        return sb.toString();
    }
}
