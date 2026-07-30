package com.lbc.client.serverCenter;

import java.net.InetSocketAddress;

//服务中心接口
public interface ServiceCenter {
    //  查询：根据服务名查找地址
    InetSocketAddress serviceDiscovery(String serviceName);

    // 查询：根据服务名、版本、分组查找地址
    default InetSocketAddress serviceDiscovery(String serviceName, String version, String group) {
        // 默认实现忽略版本分组
        return serviceDiscovery(serviceName);
    }

    //判断是否可重试
    boolean checkRetry(String serviceName);
}
