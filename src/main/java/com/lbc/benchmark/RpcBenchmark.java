package com.lbc.benchmark;

import com.lbc.client.proxy.ClientProxy;
import com.lbc.common.config.ConfigManager;
import com.lbc.common.pojo.User;
import com.lbc.common.service.UserService;
import com.lbc.server.provider.ServiceProvider;
import com.lbc.common.service.Impl.UserServiceImpl;
import com.lbc.server.server.RpcServer;
import com.lbc.server.server.impl.NettyRPCRPCServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/**
 * RPC-Java 性能压测工具
 * <p>
 * 用法：
 * <pre>
 *   java com.lbc.benchmark.RpcBenchmark [targetQps] [threadCount] [testSeconds]
 * </pre>
 * 示例：
 * <pre>
 *   java com.lbc.benchmark.RpcBenchmark 100 50 30
 * </pre>
 */
public class RpcBenchmark {

    // ===== 配置参数 =====
    private static final int DEFAULT_TARGET_QPS = 100;
    private static final int DEFAULT_THREAD_COUNT = 50;
    private static final int DEFAULT_WARMUP_SECONDS = 5;
    private static final int DEFAULT_TEST_SECONDS = 30;

    // ===== 统计指标 =====
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failCount = new LongAdder();
    private final LongAdder timeoutCount = new LongAdder();
    private final LongAdder totalCount = new LongAdder();
    private final ConcurrentLinkedDeque<Long> rtSamples = new ConcurrentLinkedDeque<>();

    // ===== 控制标志 =====
    private volatile boolean warmingUp = true;
    private volatile boolean testing = false;
    private volatile boolean stopped = false;

    public static void main(String[] args) {
        int targetQps = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_TARGET_QPS;
        int threadCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_THREAD_COUNT;
        int testSeconds = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_TEST_SECONDS;

        RpcBenchmark benchmark = new RpcBenchmark();
        benchmark.run(targetQps, threadCount, DEFAULT_WARMUP_SECONDS, testSeconds);
    }

    /**
     * 执行压测
     */
    public void run(int targetQps, int threadCount, int warmupSeconds, int testSeconds) {
        System.out.println("============================================================");
        System.out.println("              RPC-Java 性能压测");
        System.out.println("============================================================");
        System.out.printf("目标 QPS: %d%n", targetQps);
        System.out.printf("工作线程: %d%n", threadCount);
        System.out.printf("预热时长: %ds%n", warmupSeconds);
        System.out.printf("测试时长: %ds%n", testSeconds);
        System.out.println("------------------------------------------------------------");

        // 1. 启动服务端
        startServer();

        // 2. 创建客户端代理
        ClientProxy clientProxy = new ClientProxy();
        UserService userService = clientProxy.getProxy(UserService.class);

        // 3. 等待链路就绪
        waitForReady(userService);

        // 4. 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "benchmark-worker");
            t.setDaemon(true);
            return t;
        });

        // 5. 计算每个线程的发送间隔（微秒）
        long intervalPerThread = threadCount * 1_000_000L / targetQps;

        System.out.println("------------------------------------------------------------");
        System.out.println("预热中...");
        warmingUp = true;
        testing = false;

        // 启动工作线程
        CountDownLatch startLatch = new CountDownLatch(1);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> workerLoop(userService, intervalPerThread, startLatch));
        }

        // 同步启动所有线程
        startLatch.countDown();

        // 预热阶段
        sleepSeconds(warmupSeconds);

        // 6. 正式测试阶段
        System.out.println("预热完成，开始正式测试...");
        resetStats();
        warmingUp = false;
        testing = true;

        sleepSeconds(testSeconds);

        // 7. 停止
        stopped = true;
        testing = false;
        executor.shutdownNow();

        // 8. 输出报告
        printReport(targetQps, threadCount, warmupSeconds, testSeconds);
    }

    /**
     * 启动服务端（后台线程）
     */
    private void startServer() {
        System.out.println("正在启动服务端...");
        Thread serverThread = new Thread(() -> {
            UserService userService = new UserServiceImpl();
            ServiceProvider provider = new ServiceProvider("127.0.0.1", 9999);
            provider.provideServiceInterface(userService, true);
            RpcServer server = new NettyRPCRPCServer(provider);
            server.start(9999);
        }, "benchmark-server");
        serverThread.setDaemon(true);
        serverThread.start();

        // 等待服务端就绪
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("服务端启动完成");
    }

    /**
     * 等待链路就绪（发送探测请求）
     */
    private void waitForReady(UserService userService) {
        System.out.println("等待链路就绪...");
        for (int i = 0; i < 10; i++) {
            try {
                User user = userService.getUserByUserId(1);
                if (user != null) {
                    System.out.println("链路就绪");
                    return;
                }
            } catch (Exception e) {
                // 忽略，继续重试
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("警告：链路探测未成功，继续压测...");
    }

    /**
     * 工作线程主循环
     */
    private void workerLoop(UserService userService, long intervalPerThread, CountDownLatch startLatch) {
        try {
            startLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        long nextSendTime = System.nanoTime();

        while (!stopped) {
            // 发送请求
            long startTime = System.nanoTime();
            totalCount.increment();

            try {
                User user = userService.getUserByUserId(1);
                long endTime = System.nanoTime();
                long rtMicros = (endTime - startTime) / 1000;

                if (user != null) {
                    if (!warmingUp) {
                        successCount.increment();
                        rtSamples.add(rtMicros);
                    }
                } else {
                    if (!warmingUp) {
                        timeoutCount.increment();
                    }
                }
            } catch (Exception e) {
                if (!warmingUp) {
                    failCount.increment();
                }
            }

            // 控制发送速率
            nextSendTime += intervalPerThread * 1000L;
            long sleepNanos = nextSendTime - System.nanoTime();
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
    }

    /**
     * 重置统计数据
     */
    private void resetStats() {
        successCount.reset();
        failCount.reset();
        timeoutCount.reset();
        totalCount.reset();
        rtSamples.clear();
    }

    /**
     * 输出压测报告
     */
    private void printReport(int targetQps, int threadCount, int warmupSeconds, int testSeconds) {
        long success = successCount.sum();
        long fail = failCount.sum();
        long timeout = timeoutCount.sum();
        long total = success + fail + timeout;

        // 计算 RT 分位值
        List<Long> sortedRt = new ArrayList<>(rtSamples);
        Collections.sort(sortedRt);

        double avgRt = 0;
        long p50Rt = 0, p90Rt = 0, p99Rt = 0, maxRt = 0, minRt = 0;

        if (!sortedRt.isEmpty()) {
            avgRt = sortedRt.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
            p50Rt = sortedRt.get((int) (sortedRt.size() * 0.50)) / 1000;
            p90Rt = sortedRt.get((int) (sortedRt.size() * 0.90)) / 1000;
            p99Rt = sortedRt.get((int) (sortedRt.size() * 0.99)) / 1000;
            maxRt = sortedRt.get(sortedRt.size() - 1) / 1000;
            minRt = sortedRt.get(0) / 1000;
        }

        double actualQps = (double) success / testSeconds;
        double successRate = total > 0 ? (double) success / total * 100 : 0;

        // 资源信息
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        int availableProcessors = runtime.availableProcessors();

        System.out.println();
        System.out.println("============================================================");
        System.out.println("              RPC-Java 性能压测报告");
        System.out.println("============================================================");
        System.out.printf("测试方法: getUserByUserId%n");
        System.out.printf("目标 QPS: %d%n", targetQps);
        System.out.printf("工作线程: %d%n", threadCount);
        System.out.printf("预热时长: %ds%n", warmupSeconds);
        System.out.printf("测试时长: %ds%n", testSeconds);
        System.out.println("------------------------------------------------------------");
        System.out.printf("总请求数: %d%n", total);
        System.out.printf("成功数:   %d%n", success);
        System.out.printf("失败数:   %d%n", fail);
        System.out.printf("超时数:   %d%n", timeout);
        System.out.printf("成功率:   %.2f%%%n", successRate);
        System.out.println("------------------------------------------------------------");
        System.out.printf("实际 QPS: %.1f%n", actualQps);
        System.out.printf("平均 RT:  %.2fms%n", avgRt);
        System.out.printf("P50 RT:   %dms%n", p50Rt);
        System.out.printf("P90 RT:   %dms%n", p90Rt);
        System.out.printf("P99 RT:   %dms%n", p99Rt);
        System.out.printf("最大 RT:  %dms%n", maxRt);
        System.out.printf("最小 RT:  %dms%n", minRt);
        System.out.println("------------------------------------------------------------");
        System.out.printf("CPU 核心: %d%n", availableProcessors);
        System.out.printf("使用内存: %dMB / %dMB%n", usedMemory, maxMemory);
        System.out.println("============================================================");
    }

    /**
     * 休眠指定秒数
     */
    private void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
