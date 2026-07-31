package com.lbc.client.serverCenter.balance.impl;

import com.lbc.client.serverCenter.balance.LoadBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lbc
 * @date 2024/09/25 11:40
 * 轮询 负载均衡（线程安全）
 **/
public class RoundLoadBalance implements LoadBalance {
    private static final Logger logger = LoggerFactory.getLogger(RoundLoadBalance.class);

    private final AtomicInteger choose = new AtomicInteger(-1);

    @Override
    public String balance(List<String> addressList) {
        int index = Math.abs(choose.getAndIncrement() % addressList.size());
        logger.debug("轮询负载均衡选择了第 {} 个服务器: {}", index, addressList.get(index));
        return addressList.get(index);
    }

    @Override
    public void addNode(String node) {

    }

    @Override
    public void delNode(String node) {

    }
}
