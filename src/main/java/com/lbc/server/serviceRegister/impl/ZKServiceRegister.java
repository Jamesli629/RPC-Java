package com.lbc.server.serviceRegister.impl;

import com.lbc.common.config.ConfigManager;
import com.lbc.server.serviceRegister.ServiceRegister;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author Lbc
 * @date 2024/09/16 16:23
 **/
public class ZKServiceRegister implements ServiceRegister {

    private static final Logger logger = LoggerFactory.getLogger(ZKServiceRegister.class);

    // curator 提供的zookeeper客户端
    private CuratorFramework client;

    private final ConfigManager config = ConfigManager.getInstance();

    //负责zookeeper客户端的初始化，并与zookeeper服务端进行连接
    public ZKServiceRegister() {
        // 从配置中读取 ZK 参数
        String connectString = config.getString("rpc.zk.connect-string", "127.0.0.1:2181");
        String rootPath = config.getString("rpc.zk.root-path", "MyRPC");
        int retryBaseSleepMs = config.getInt("rpc.zk.retry-base-sleep-ms", 1000);
        int retryMaxTimes = config.getInt("rpc.zk.retry-max-times", 3);
        int sessionTimeoutMs = config.getInt("rpc.zk.session-timeout-ms", 40000);

        // 指数时间重试
        RetryPolicy policy = new ExponentialBackoffRetry(retryBaseSleepMs, retryMaxTimes);
        // zookeeper的地址固定，不管是服务提供者还是，消费者都要与之建立连接
        // sessionTimeoutMs 与 zoo.cfg中的tickTime 有关系，
        // zk还会根据minSessionTimeout与maxSessionTimeout两个参数重新调整最后的超时值。默认分别为tickTime 的2倍和20倍
        // 使用心跳监听状态
        this.client = CuratorFrameworkFactory.builder().connectString(connectString)
                .sessionTimeoutMs(sessionTimeoutMs).retryPolicy(policy).namespace(rootPath).build();
        this.client.start();
        logger.info("zookeeper 连接成功，地址: {}, 根路径: {}", connectString, rootPath);
    }

    //注册服务到注册中心
    @Override
    public void register(String serviceName, InetSocketAddress serviceAddress, boolean canTry) {
        try {
            // serviceName创建成永久节点，服务提供者下线时，不删服务名，只删地址
            if (client.checkExists().forPath("/" + serviceName) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath("/" + serviceName);
            }
            // 路径地址，一个/代表一个节点
            String path = "/" + serviceName + "/" + getServiceAddress(serviceAddress);
            // 临时节点，服务器下线就删除节点
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);

            //如果这个服务是幂等性，就增加到节点中
            if (canTry) {
                String retryPath = config.getString("rpc.zk.retry-path", "CanRetry");
                path = "/" + retryPath + "/" + serviceName;
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);
            }
        } catch (Exception e) {
            logger.warn("此服务已存在: {}", serviceName);
        }
    }

    // 注销服务地址
    @Override
    public void unregister(String serviceName, InetSocketAddress serviceAddress) {
        try {
            String path = "/" + serviceName + "/" + getServiceAddress(serviceAddress);
            // 如果节点存在则删除
            if (client.checkExists().forPath(path) != null) {
                client.delete().forPath(path);
                logger.info("注销服务: {} @ {}", serviceName, getServiceAddress(serviceAddress));
            }
        } catch (Exception e) {
            logger.error("注销服务失败: {} @ {}", serviceName, getServiceAddress(serviceAddress), e);
        }
    }

    // 地址 -> XXX.XXX.XXX.XXX:port 字符串
    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() +
                ":" +
                serverAddress.getPort();
    }

    // 字符串解析为地址
    private InetSocketAddress parseAddress(String address) {
        String[] result = address.split(":");
        return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
    }
}
