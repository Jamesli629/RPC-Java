package com.lbc.client.serverCenter.balance.impl;

import com.lbc.client.serverCenter.balance.LoadBalance;
import com.lbc.common.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Lbc
 * @date 2024/09/25 11:41
 * 一致性哈希算法 负载均衡，虚拟节点数从配置读取
 **/
public class ConsistencyHashBalance implements LoadBalance {
    private static final Logger logger = LoggerFactory.getLogger(ConsistencyHashBalance.class);

    // 虚拟节点的个数，从配置读取
    private final int virtualNum;

    // 虚拟节点分配，key是hash值，value是虚拟节点服务器名称
    private final SortedMap<Integer, String> shards = new TreeMap<>();

    // 真实节点列表
    private final List<String> realNodes = new LinkedList<>();

    //无参构造，节点通过addNode增量添加
    public ConsistencyHashBalance() {
        this.virtualNum = ConfigManager.getInstance()
                .getInt("rpc.client.load-balance.virtual-node-count", 5);
    }

    //带初始服务列表的构造，一次性构建哈希环
    public ConsistencyHashBalance(List<String> serviceList) {
        this.virtualNum = ConfigManager.getInstance()
                .getInt("rpc.client.load-balance.virtual-node-count", 5);
        for (String server : serviceList) {
            addNode(server);
        }
    }

    @Override
    public String balance(List<String> addressList) {
        // 如果哈希环为空，说明尚未初始化，先全量构建
        if (shards.isEmpty()) {
            for (String server : addressList) {
                addNode(server);
            }
        }
        String random = UUID.randomUUID().toString();
        int hash = getHash(random);
        SortedMap<Integer, String> subMap = shards.tailMap(hash);
        Integer key;
        if (subMap.isEmpty()) {
            key = shards.lastKey();
        } else {
            key = subMap.firstKey();
        }
        String virtualNode = shards.get(key);
        return virtualNode.substring(0, virtualNode.indexOf("&&"));
    }

    /**
     * 添加节点
     *
     * @param node
     */
    @Override
    public void addNode(String node) {

        if (!realNodes.contains(node)) {
            realNodes.add(node);
            logger.info("一致性哈希：真实节点[{}] 上线添加", node);
            for (int i = 0; i < virtualNum; i++) {
                String virtualNode = node + "&&VN" + i;
                int hash = getHash(virtualNode);
                shards.put(hash, virtualNode);
                logger.debug("一致性哈希：虚拟节点[{}] hash:{}", virtualNode, hash);
            }
        }

    }

    /**
     * 删除节点
     *
     * @param node
     */
    @Override
    public void delNode(String node) {
        if (realNodes.contains(node)) {
            realNodes.remove(node);
            logger.info("一致性哈希：真实节点[{}] 下线移除", node);
            for (int i = 0; i < virtualNum; i++) {
                String virtualNode = node + "&&VN" + i;
                int hash = getHash(virtualNode);
                shards.remove(hash);
                logger.debug("一致性哈希：虚拟节点[{}] hash:{}", virtualNode, hash);
            }
        }
    }

    private static int getHash(String str) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < str.length(); i++) {
            hash = (hash ^ str.charAt(i)) * p;
        }
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;
        // 如果算出来的值为负数则取其绝对值
        if (hash < 0) {
            hash = Math.abs(hash);
        }
        return hash;
    }
}
