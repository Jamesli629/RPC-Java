package com.lbc.server.health;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

/**
 * 健康检查 HTTP 服务端
 *
 * 提供两个端点：
 * - /health — Liveness Probe（进程是否存活）
 * - /ready — Readiness Probe（服务是否就绪）
 *
 * 基于 Netty 实现，轻量无外部依赖。
 */
public class HealthcheckServer {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckServer.class);

    private final int port;
    private final Supplier<HealthStatus> readinessChecker;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel channel;

    /**
     * @param healthCheckPort 健康检查端口
     * @param readinessChecker 就绪状态检查器，返回当前就绪状态
     */
    public HealthcheckServer(int healthCheckPort, Supplier<HealthStatus> readinessChecker) {
        this.port = healthCheckPort;
        this.readinessChecker = readinessChecker;
    }

    /**
     * 启动健康检查服务
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(1);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpResponseEncoder());
                            p.addLast(new HttpRequestDecoder(4096, 8192, 8192, false));
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new HealthcheckHandler(readinessChecker));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            channel = b.bind(port).sync().channel();
            logger.info("健康检查服务启动成功，端口: {}, 端点: /health, /ready", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("健康检查服务启动被中断");
        }
    }

    /**
     * 停止健康检查服务
     */
    public void stop() {
        if (channel != null) {
            channel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("健康检查服务已停止");
    }

    /**
     * HTTP 请求处理器
     */
    private static class HealthcheckHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final Supplier<HealthStatus> readinessChecker;

        HealthcheckHandler(Supplier<HealthStatus> readinessChecker) {
            this.readinessChecker = readinessChecker;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            String uri = req.uri();
            HealthStatus status;
            if ("/health".equals(uri)) {
                // Liveness：进程存活即返回 UP
                status = HealthStatus.UP;
            } else if ("/ready".equals(uri)) {
                // Readiness：由外部检查器判断
                status = readinessChecker != null ? readinessChecker.get() : HealthStatus.UP;
            } else {
                status = HealthStatus.UNKNOWN;
            }

            int statusCode = (status == HealthStatus.UP) ? 200 : 503;
            String body = String.format("{\"status\":\"%s\"}", status.getCode());

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(statusCode),
                    Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warn("健康检查请求处理异常", cause);
            ctx.close();
        }
    }
}
