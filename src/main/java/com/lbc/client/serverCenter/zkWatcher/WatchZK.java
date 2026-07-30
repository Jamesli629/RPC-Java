package com.lbc.client.serverCenter.zkWatcher;

import com.lbc.client.cache.ServiceCache;
import com.lbc.client.serverCenter.balance.impl.ConsistencyHashBalance;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;

/**
 * @author Lbc
 * @date 2024/09/22 18:21
 **/
public class WatchZK {
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
                // 第一个参数：事件类型（枚举）
                // 第二个参数：节点更新前的状态、数据
                // 第三个参数：节点更新后的状态、数据
                // 创建节点时：节点刚被创建，不存在 更新前节点 ，所以第二个参数为 null
                // 删除节点时：节点被删除，不存在 更新后节点 ，所以第三个参数为 null
                // 节点创建时没有赋予值 create /curator/app1 只创建节点，在这种情况下，更新前节点的 data 为 null，获取不到更新前节点的数据
                switch (type.name()) {
                    case "NODE_CREATED":// 监听器第一次执行时节点存在也会触发次事件
                        //获取更新的节点的路径
                        String path = new String(childData1.getPath());
                        //按照格式 ，读取
                        String[] pathList = path.split("/");
                        if (pathList.length <= 2) break;
                        else {
                            String serviceName = pathList[1];
                            //过滤掉CanRetry白名单节点，只处理服务地址节点
                            if ("CanRetry".equals(serviceName)) break;
                            String address = pathList[2];
                            //将新注册的服务加入到本地缓存中
                            cache.addServiceToCache(serviceName, address);
                            //增量添加节点到一致性哈希环
                            loadBalance.addNode(address);
                        }
                        break;
                    case "NODE_CHANGED":// 节点更新
                        if (childData.getData() != null) {
                            System.out.println("修改前的数据: " + new String(childData.getData()));
                        } else {
                            System.out.println("节点第一次赋值!");
                        }
                        System.out.println("修改后的数据: " + new String(childData1.getData()));
                        break;
                    case "NODE_DELETED":// 节点删除
                        String path_d = new String(childData.getPath());
                        //按照格式 ，读取
                        String[] pathList_d = path_d.split("/");
                        if (pathList_d.length <= 2) break;
                        else {
                            String serviceName = pathList_d[1];
                            //过滤掉CanRetry白名单节点
                            if ("CanRetry".equals(serviceName)) break;
                            String address = pathList_d[2];
                            //删除缓存中的服务地址
                            cache.delete(serviceName, address);
                            //增量从一致性哈希环移除节点
                            loadBalance.delNode(address);
                        }
                        break;
                    default:
                        break;

                }
            }
        });
        //开启监听
        curatorCache.start();
    }
}
