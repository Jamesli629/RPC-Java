package com.lbc.client.serverCenter.balance.impl;

import com.lbc.client.serverCenter.balance.LoadBalance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lbc
 * @date 2024/09/25 11:40
 * 轮询 负载均衡（线程安全）
 **/
public class RoundLoadBalance implements LoadBalance {
    private final AtomicInteger choose = new AtomicInteger(-1);

    @Override
    public String balance(List<String> addressList) {
        int index = Math.abs(choose.getAndIncrement() % addressList.size());
        System.out.println("负载均衡选择了" + index + "服务器");
        return addressList.get(index);
    }

    @Override
    public void addNode(String node) {

    }

    @Override
    public void delNode(String node) {

    }
}
