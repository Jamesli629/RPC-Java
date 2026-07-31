package com.lbc.server.netty.handler;

import com.lbc.common.config.ConfigManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 业务线程池处理器
 *
 * 将后续 Handler 的 channelRead 事件提交到独立业务线程池执行，
 * 避免慢接口阻塞 Netty IO 线程，影响网络吞吐。
 *
 * 注意：此 Handler 不是 @Sharable，每个 channel 创建新实例。
 * 业务线程池作为 static 字段全局共享。
 */
@ChannelHandler.Sharable
public class BusinessThreadPoolHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BusinessThreadPoolHandler.class);

    /**
     * 全局共享的业务线程池。核心线程数、最大线程数、队列容量从配置读取。
     * 使用有界队列 + CallerRunsPolicy 防止任务无限堆积。
     */
    private static final ThreadPoolExecutor BUSINESS_EXECUTOR;

    static {
        int coreSize = 16;
        int maxSize = 64;
        int queueCapacity = 200;
        try {
            ConfigManager config = ConfigManager.getInstance();
            coreSize = config.getInt("rpc.server.business-pool.core-size", 16);
            maxSize = config.getInt("rpc.server.business-pool.max-size", 64);
            queueCapacity = config.getInt("rpc.server.business-pool.queue-capacity", 200);
        } catch (Exception e) {
            // 配置未就绪时使用默认值
        }
        BUSINESS_EXECUTOR = new ThreadPoolExecutor(
                coreSize, maxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "rpc-business-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                // CallerRunsPolicy：队列满时由 IO 线程直接执行，提供背压
                new ThreadPoolExecutor.CallerRunsPolicy());
        logger.info("业务线程池初始化完成: core={}, max={}, queue={}", coreSize, maxSize, queueCapacity);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 将业务处理提交到线程池，IO 线程立即释放
        try {
            BUSINESS_EXECUTOR.execute(() -> {
                try {
                    ctx.fireChannelRead(msg);
                } catch (Exception e) {
                    logger.error("业务处理异常", e);
                    ctx.fireExceptionCaught(e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池和队列都满了，直接在 IO 线程执行（CallerRunsPolicy 不应触发，这里是兜底）
            logger.warn("业务线程池已满，IO 线程降级执行");
            ctx.fireChannelRead(msg);
        }
    }
}
