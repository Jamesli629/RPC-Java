package com.lbc.client.serverCenter;

import com.lbc.client.cache.ServiceCache;
import com.lbc.client.serverCenter.balance.impl.ConsistencyHashBalance;
import com.lbc.client.serverCenter.zkWatcher.WatchZK;
import com.lbc.common.config.ConfigManager;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author Lbc
 * @date 2024/09/16 16:16
 **/
public class ZKServiceCenter implements ServiceCenter {
    private static final Logger logger = LoggerFactory.getLogger(ZKServiceCenter.class);

    // curator 提供的zookeeper客户端
    private CuratorFramework client;
    //serviceCache
    private ServiceCache cache;
    //负载均衡器（单一实例，通过addNode/delNode增量维护）
    private ConsistencyHashBalance loadBalance;

    private final ConfigManager config = ConfigManager.getInstance();

    //负责zookeeper客户端的初始化，并与zookeeper服务端进行连接
    public ZKServiceCenter() throws InterruptedException {
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

        //初始化负载均衡器和本地缓存
        loadBalance = new ConsistencyHashBalance();
        cache = new ServiceCache();
        //加入zookeeper事件监听器
        WatchZK watcher = new WatchZK(client, cache, loadBalance);
        //监听启动
        watcher.watchToUpdate(rootPath);
    }

    //根据服务名（接口名）返回地址
    @Override
    public InetSocketAddress serviceDiscovery(String serviceName) {
        try {
            //先从本地缓存中找
            List<String> addressList = cache.getServiceFromCache(serviceName);
            //如果找不到，再去zookeeper中找
            //这种情况基本不会发生，或者说只会出现在初始化阶段
            if (addressList == null) {
                addressList = client.getChildren().forPath("/" + serviceName);
            }

            //防护：服务不存在或地址列表为空
            if (addressList == null || addressList.isEmpty()) {
                logger.warn("服务{}没有可用的服务地址", serviceName);
                return null;
            }

            // 读取权重（从 ZK 节点数据）
            List<Integer> weights = getWeights(serviceName, addressList);

            // 负载均衡得到地址（带权重）
            String address = loadBalance.balance(addressList, weights);
            return parseAddress(address);
        } catch (Exception e) {
            logger.error("服务发现失败，服务名: {}", serviceName, e);
        }
        return null;
    }

    /**
     * 读取服务地址对应的权重列表
     */
    private List<Integer> getWeights(String serviceName, List<String> addressList) {
        List<Integer> weights = new java.util.ArrayList<>();
        for (String addr : addressList) {
            try {
                String path = "/" + serviceName + "/" + addr;
                byte[] data = client.getData().forPath(path);
                if (data != null && data.length > 0) {
                    weights.add(Integer.parseInt(new String(data)));
                } else {
                    weights.add(1); // 默认权重为 1
                }
            } catch (Exception e) {
                weights.add(1); // 读取失败时默认权重为 1
            }
        }
        return weights;
    }

    @Override
    public boolean checkRetry(String serviceName) {
        boolean canRetry = false;
        try {
            String retryPath = config.getString("rpc.zk.retry-path", "CanRetry");
            List<String> serviceList = client.getChildren().forPath("/" + retryPath);
            for (String service : serviceList) {
                //如果列表中有该服务
                if (service.equals(serviceName)) {
                    logger.info("服务{}在白名单上，可进行重试", serviceName);
                    canRetry = true;
                }
            }
        } catch (Exception e) {
            logger.error("检查重试白名单失败，服务名: {}", serviceName, e);
        }
        return canRetry;
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
        if (result.length < 2) {
            logger.error("服务地址格式错误，无法解析: {}", address);
            return null;
        }
        try {
            return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
        } catch (NumberFormatException e) {
            logger.error("服务地址端口解析失败: {}", address, e);
            return null;
        }
    }
}
