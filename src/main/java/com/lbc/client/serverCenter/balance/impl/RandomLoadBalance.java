package com.lbc.client.serverCenter.balance.impl;

import com.lbc.client.serverCenter.balance.LoadBalance;

import java.util.List;
import java.util.Random;

/**
 * @author Lbc
 * @date 2024/09/25 11:33
 * 随机 负载均衡
 **/
public class RandomLoadBalance implements LoadBalance {
    @Override
    public String balance(List<String> addressList) {
        Random random = new Random();
        int choose = random.nextInt(addressList.size());
        System.out.println("负载均衡选择了" + choose + "服务器");
        return addressList.get(choose);
    }

    @Override
    public void addNode(String node) {

    }

    @Override
    public void delNode(String node) {

    }
}
