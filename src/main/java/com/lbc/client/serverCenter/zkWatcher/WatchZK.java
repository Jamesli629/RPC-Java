package com.lbc.client.serverCenter.zkWatcher;

import com.lbc.client.cache.ServiceCache;
import com.lbc.client.serverCenter.balance.impl.ConsistencyHashBalance;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lbc
 * @date 2024/09/22 18:21
 *
 * ZK 事件监听，适配新路径结构: /MyRPC/{serviceName}/{version}/{group}/{address}
 **/
public class WatchZK {

    private static final Logger logger = LoggerFactory.getLogger(WatchZK.class);

    //curator 提供的zookeeper客户端
    private CuratorFramework client;
    //本地缓存
    ServiceCache cache;
    //负载均衡器（增量维护哈希环）
    private ConsistencyHashBalance loadBalance;

    public WatchZK(CuratorFramework client, ServiceCache cache, ConsistencyHashBalance loadBalance) {
        this.client = client;
        this.cache = cache;
        this.loadBalance = loadBalance;
    }

    public void watchToUpdate(String path) throws InterruptedException {
        CuratorCache curatorCache = CuratorCache.build(client, "/");
        curatorCache.listenable().addListener(new CuratorCacheListener() {
            @Override
            public void event(Type type, ChildData childData, ChildData childData1) {
                switch (type.name()) {
                    case "NODE_CREATED":
                        handleNodeCreated(childData1);
                        break;
                    case "NODE_CHANGED":
                        handleNodeChanged(childData, childData1);
                        break;
                    case "NODE_DELETED":
                        handleNodeDeleted(childData);
                        break;
                    default:
                        break;
                }
            }
        });
        //开启监听
        curatorCache.start();
    }

    /**
     * 处理节点创建事件
     * 新路径: /MyRPC/{serviceName}/{version}/{group}/{address}
     * pathList: ["", "MyRPC", serviceName, version, group, address]
     */
    private void handleNodeCreated(ChildData childData1) {
        String path = childData1.getPath();
        String[] pathList = path.split("/");
        // 路径至少要有6段: "" "MyRPC" serviceName version group address
        if (pathList.length < 6) return;

        String serviceName = pathList[2];
        //过滤掉CanRetry白名单节点
        if ("CanRetry".equals(serviceName)) return;

        String version = pathList[3];
        String group = pathList[4];
        String address = pathList[5];

        //将新注册的服务加入到本地缓存中
        cache.addServiceToCache(serviceName, version, group, address);
        //增量添加节点到一致性哈希环
        loadBalance.addNode(address);
        logger.debug("新增服务节点: {} v{} group:{} @ {}", serviceName, version, group, address);
    }

    /**
     * 处理节点变更事件
     */
    private void handleNodeChanged(ChildData childData, ChildData childData1) {
        if (childData1.getData() != null) {
            logger.debug("节点数据变更: {} -> {}", childData.getPath(), new String(childData1.getData()));
        }
    }

    /**
     * 处理节点删除事件
     */
    private void handleNodeDeleted(ChildData childData) {
        String path_d = childData.getPath();
        String[] pathList_d = path_d.split("/");
        // 路径至少要有6段
        if (pathList_d.length < 6) return;

        String serviceName = pathList_d[2];
        //过滤掉CanRetry白名单节点
        if ("CanRetry".equals(serviceName)) return;

        String version = pathList_d[3];
        String group = pathList_d[4];
        String address = pathList_d[5];

        //删除缓存中的服务地址
        cache.delete(serviceName, version, group, address);
        //增量从一致性哈希环移除节点
        loadBalance.delNode(address);
        logger.debug("删除服务节点: {} v{} group:{} @ {}", serviceName, version, group, address);
    }
}
