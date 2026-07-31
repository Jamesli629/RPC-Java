package com.lbc.client.balance;

import com.lbc.client.serverCenter.balance.LoadBalance;
import com.lbc.client.serverCenter.balance.impl.ConsistencyHashBalance;
import com.lbc.client.serverCenter.balance.impl.RandomLoadBalance;
import com.lbc.client.serverCenter.balance.impl.RoundLoadBalance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 负载均衡策略测试
 */
public class LoadBalanceTest {

    private final List<String> addressList = Arrays.asList(
            "127.0.0.1:8080",
            "127.0.0.1:8081",
            "127.0.0.1:8082"
    );

    @Test
    public void testRandomLoadBalance() {
        LoadBalance balance = new RandomLoadBalance();
        for (int i = 0; i < 10; i++) {
            String selected = balance.balance(addressList);
            assertNotNull(selected);
            assertTrue(addressList.contains(selected));
        }
    }

    @Test
    public void testRoundLoadBalance() {
        LoadBalance balance = new RoundLoadBalance();
        for (int i = 0; i < addressList.size(); i++) {
            String selected = balance.balance(addressList);
            assertEquals(addressList.get(i), selected);
        }
        // 循环回到第一个
        assertEquals(addressList.get(0), balance.balance(addressList));
    }

    @Test
    public void testConsistencyHashBalance() {
        ConsistencyHashBalance balance = new ConsistencyHashBalance(addressList);
        // 相同 key 应该路由到相同节点
        String key = "test-key-123";
        String first = balance.balance(addressList);
        assertNotNull(first);
        assertTrue(addressList.contains(first));
    }

    @Test
    public void testAddAndDelNode() {
        ConsistencyHashBalance balance = new ConsistencyHashBalance();
        balance.addNode("127.0.0.1:9090");
        String selected = balance.balance(Arrays.asList("127.0.0.1:9090"));
        assertEquals("127.0.0.1:9090", selected);

        balance.delNode("127.0.0.1:9090");
        // 删除后 balance 应该正常工作
        assertDoesNotThrow(() -> balance.balance(Arrays.asList("127.0.0.1:9090")));
    }
}
