package com.lbc.client.serverCenter.balance.impl;

import com.lbc.client.serverCenter.balance.LoadBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * @author Lbc
 * @date 2024/09/25 11:33
 * 随机 负载均衡
 **/
public class RandomLoadBalance implements LoadBalance {
    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalance.class);

    @Override
    public String balance(List<String> addressList) {
        Random random = new Random();
        int choose = random.nextInt(addressList.size());
        logger.debug("随机负载均衡选择了第 {} 个服务器: {}", choose, addressList.get(choose));
        return addressList.get(choose);
    }

    @Override
    public void addNode(String node) {

    }

    @Override
    public void delNode(String node) {

    }
}
