package com.lbc.client.serverCenter.balance.impl;

import com.lbc.client.serverCenter.balance.LoadBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 加权轮询负载均衡（Nginx 平滑加权轮询算法）
 * <p>
 * 普通加权轮询会出现流量突刺（如权重 5:1 时，前 5 次全部打到同一节点）。
 * 平滑加权轮询算法在每一轮中动态调整当前权重，使流量均匀分散。
 * <p>
 * 算法：
 * 1. 每个节点维护 currentWeight，初始为 0
 * 2. 每次选择时：currentWeight += effectiveWeight
 * 3. 选择 currentWeight 最大的节点
 * 4. 被选中的节点：currentWeight -= totalWeight
 * 5. 重复
 *
 * @author Lbc
 */
public class WeightedRoundLoadBalance implements LoadBalance {

    private static final Logger logger = LoggerFactory.getLogger(WeightedRoundLoadBalance.class);

    // 节点 -> 有效权重
    private final ConcurrentHashMap<String, Integer> effectiveWeights = new ConcurrentHashMap<>();
    // 节点 -> 当前权重
    private final ConcurrentHashMap<String, AtomicInteger> currentWeights = new ConcurrentHashMap<>();
    // 总权重
    private final AtomicInteger totalWeight = new AtomicInteger(0);

    @Override
    public String balance(List<String> addressList) {
        // 无权重信息时退化为普通轮询
        if (effectiveWeights.isEmpty() || addressList.size() <= 1) {
            return addressList.get(Math.abs(Thread.currentThread().hashCode()) % addressList.size());
        }
        return doBalance(addressList, null);
    }

    @Override
    public String balance(List<String> addressList, List<Integer> weights) {
        if (weights == null || weights.isEmpty() || addressList.size() != weights.size()) {
            return balance(addressList);
        }
        return doBalance(addressList, weights);
    }

    private String doBalance(List<String> addressList, List<Integer> weights) {
        // 同步有效权重（如果有外部传入的权重）
        if (weights != null) {
            for (int i = 0; i < addressList.size(); i++) {
                String node = addressList.get(i);
                int weight = weights.get(i);
                Integer oldWeight = effectiveWeights.put(node, weight);
                currentWeights.computeIfAbsent(node, k -> new AtomicInteger(0));
                if (oldWeight != null) {
                    totalWeight.addAndGet(weight - oldWeight);
                } else {
                    totalWeight.addAndGet(weight);
                }
            }
        }

        // 平滑加权轮询核心算法
        // 1. currentWeight += effectiveWeight
        for (String node : addressList) {
            AtomicInteger cw = currentWeights.get(node);
            Integer ew = effectiveWeights.get(node);
            if (cw != null && ew != null) {
                cw.addAndGet(ew);
            }
        }

        // 2. 选择 currentWeight 最大的节点
        String selected = null;
        int maxWeight = Integer.MIN_VALUE;
        for (String node : addressList) {
            AtomicInteger cw = currentWeights.get(node);
            if (cw != null && cw.get() > maxWeight) {
                maxWeight = cw.get();
                selected = node;
            }
        }

        // 3. 选中节点 currentWeight -= totalWeight
        if (selected != null) {
            AtomicInteger cw = currentWeights.get(selected);
            if (cw != null) {
                cw.addAndGet(-totalWeight.get());
            }
            logger.debug("加权轮询选择了: {}", selected);
        }

        return selected != null ? selected : addressList.get(0);
    }

    @Override
    public void addNode(String node) {
        // 默认权重为 1
        effectiveWeights.putIfAbsent(node, 1);
        currentWeights.computeIfAbsent(node, k -> new AtomicInteger(0));
        totalWeight.incrementAndGet();
    }

    @Override
    public void delNode(String node) {
        Integer weight = effectiveWeights.remove(node);
        currentWeights.remove(node);
        if (weight != null) {
            totalWeight.addAndGet(-weight);
        }
    }
}
