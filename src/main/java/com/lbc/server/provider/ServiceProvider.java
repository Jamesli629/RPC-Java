package com.lbc.server.provider;

import com.lbc.server.rateLimit.provider.RateLimitProvider;
import com.lbc.server.serviceRegister.ServiceRegister;
import com.lbc.server.serviceRegister.impl.ZKServiceRegister;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;


//本地服务存放器
public class ServiceProvider {
    //集合中存放服务的实例
    private Map<String, Object> interfaceProvider;

    private int port;
    private String host;
    //注册服务类
    private ServiceRegister serviceRegister;
    //限流器
    private RateLimitProvider rateLimitProvider;

    public ServiceProvider(String host, int port) {
        //需要传入服务端自身的网络地址
        this.host = host;
        this.port = port;
        this.interfaceProvider = new HashMap<>();
        this.serviceRegister = new ZKServiceRegister();
        this.rateLimitProvider = new RateLimitProvider();
    }

    public void provideServiceInterface(Object service, boolean canRetry) {
        provideServiceInterface(service, canRetry, 1, "default", "default");
    }

    public void provideServiceInterface(Object service, boolean canRetry, int weight) {
        provideServiceInterface(service, canRetry, weight, "default", "default");
    }

    public void provideServiceInterface(Object service, boolean canRetry, int weight, String version, String group) {
        String serviceName = service.getClass().getName();
        Class<?>[] interfaceName = service.getClass().getInterfaces();

        for (Class<?> clazz : interfaceName) {
            //本机的映射表
            interfaceProvider.put(clazz.getName(), service);
            //在注册中心注册服务（带权重、版本、分组）
            serviceRegister.register(clazz.getName(), new InetSocketAddress(host, port), canRetry, weight, version, group);
        }
    }

    /**
     * 注销所有已注册的服务（优雅下线时调用）
     */
    public void unregisterAll() {
        InetSocketAddress address = new InetSocketAddress(host, port);
        for (String interfaceName : interfaceProvider.keySet()) {
            try {
                serviceRegister.unregister(interfaceName, address);
            } catch (Exception e) {
                // 注销失败不影响其他服务注销
            }
        }
    }

    public Object getService(String interfaceName) {
        return interfaceProvider.get(interfaceName);
    }

    public RateLimitProvider getRateLimitProvider() {
        return rateLimitProvider;
    }

}
