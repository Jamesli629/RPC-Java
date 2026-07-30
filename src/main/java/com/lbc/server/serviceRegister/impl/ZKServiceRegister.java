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
 *
 * ZK 服务注册，支持版本/分组
 * 路径结构: /MyRPC/{serviceName}/{version}/{group}/{address}
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
        this.client = CuratorFrameworkFactory.builder().connectString(connectString)
                .sessionTimeoutMs(sessionTimeoutMs).retryPolicy(policy).namespace(rootPath).build();
        this.client.start();
        logger.info("zookeeper 连接成功，地址: {}, 根路径: {}", connectString, rootPath);
    }

    //注册服务到注册中心
    @Override
    public void register(String serviceName, InetSocketAddress serviceAddress, boolean canTry) {
        register(serviceName, serviceAddress, canTry, 1, "default", "default");
    }

    //注册服务到注册中心（带权重）
    @Override
    public void register(String serviceName, InetSocketAddress serviceAddress, boolean canTry, int weight) {
        register(serviceName, serviceAddress, canTry, weight, "default", "default");
    }

    //注册服务到注册中心（带权重、版本、分组）
    @Override
    public void register(String serviceName, InetSocketAddress serviceAddress, boolean canTry,
                         int weight, String version, String group) {
        try {
            // 构建路径: /{serviceName}/{version}/{group}/{address}
            String servicePath = "/" + serviceName;
            String versionPath = servicePath + "/" + version;
            String groupPath = versionPath + "/" + group;
            String addressPath = groupPath + "/" + getServiceAddress(serviceAddress);

            // 创建永久节点（服务名、版本、分组）
            if (client.checkExists().forPath(servicePath) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(servicePath);
            }
            if (client.checkExists().forPath(versionPath) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(versionPath);
            }
            if (client.checkExists().forPath(groupPath) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(groupPath);
            }

            // 临时节点（地址），节点数据保存权重
            if (client.checkExists().forPath(addressPath) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
                        .forPath(addressPath, String.valueOf(weight).getBytes());
            } else {
                client.setData().forPath(addressPath, String.valueOf(weight).getBytes());
            }

            //如果这个服务是幂等性，就增加到节点中
            if (canTry) {
                String retryPath = config.getString("rpc.zk.retry-path", "CanRetry");
                String retryNode = "/" + retryPath + "/" + serviceName;
                if (client.checkExists().forPath(retryNode) == null) {
                    client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(retryNode);
                }
            }
            logger.info("注册服务: {} v{} group:{} @ {} weight:{}", serviceName, version, group,
                    getServiceAddress(serviceAddress), weight);
        } catch (Exception e) {
            logger.warn("注册服务失败: {}", serviceName, e);
        }
    }

    // 注销服务地址
    @Override
    public void unregister(String serviceName, InetSocketAddress serviceAddress) {
        // 注销所有版本/分组下的该地址
        try {
            String servicePath = "/" + serviceName;
            if (client.checkExists().forPath(servicePath) == null) return;

            // 遍历所有版本
            for (String version : client.getChildren().forPath(servicePath)) {
                String versionPath = servicePath + "/" + version;
                // 遍历所有分组
                for (String group : client.getChildren().forPath(versionPath)) {
                    String addressPath = versionPath + "/" + group + "/" + getServiceAddress(serviceAddress);
                    if (client.checkExists().forPath(addressPath) != null) {
                        client.delete().forPath(addressPath);
                        logger.info("注销服务: {} v{} group:{} @ {}", serviceName, version, group,
                                getServiceAddress(serviceAddress));
                    }
                }
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
