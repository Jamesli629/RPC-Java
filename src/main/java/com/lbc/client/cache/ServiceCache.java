package com.lbc.client.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Lbc
 * @date 2024/09/22 17:34
 *
 * 本地服务缓存，支持复合 key（serviceName:version:group）
 **/
public class ServiceCache {

    private static final Logger logger = LoggerFactory.getLogger(ServiceCache.class);

    //key: serviceName:version:group 复合结构
    //value： addressList 服务提供者列表
    private static Map<String, List<String>> cache = new HashMap<>();

    //添加服务
    public void addServiceToCache(String serviceName, String address) {
        addServiceToCache(serviceName, "default", "default", address);
    }

    //添加服务（带版本/分组）
    public void addServiceToCache(String serviceName, String version, String group, String address) {
        String key = buildKey(serviceName, version, group);
        if (cache.containsKey(key)) {
            List<String> addressList = cache.get(key);
            addressList.add(address);
            logger.debug("将name为{} v{} group:{} 地址为{}的服务添加到本地缓存中", serviceName, version, group, address);
        } else {
            List<String> addressList = new ArrayList<>();
            addressList.add(address);
            cache.put(key, addressList);
        }
    }

    //修改服务地址
    public void replaceServiceAddress(String serviceName, String oldAddress, String newAddress) {
        replaceServiceAddress(serviceName, "default", "default", oldAddress, newAddress);
    }

    //修改服务地址（带版本/分组）
    public void replaceServiceAddress(String serviceName, String version, String group,
                                      String oldAddress, String newAddress) {
        String key = buildKey(serviceName, version, group);
        if (cache.containsKey(key)) {
            List<String> addressList = cache.get(key);
            addressList.remove(oldAddress);
            addressList.add(newAddress);
        } else {
            logger.warn("修改失败，服务{}不存在", serviceName);
        }
    }

    //从缓存中取服务地址
    public List<String> getServiceFromCache(String serviceName) {
        return getServiceFromCache(serviceName, "default", "default");
    }

    //从缓存中取服务地址（带版本/分组）
    public List<String> getServiceFromCache(String serviceName, String version, String group) {
        String key = buildKey(serviceName, version, group);
        if (!cache.containsKey(key)) {
            return null;
        }
        return cache.get(key);
    }

    //从缓存中删除服务地址
    public void delete(String serviceName, String address) {
        delete(serviceName, "default", "default", address);
    }

    //从缓存中删除服务地址（带版本/分组）
    public void delete(String serviceName, String version, String group, String address) {
        String key = buildKey(serviceName, version, group);
        if (!cache.containsKey(key)) {
            logger.warn("删除失败，服务{}不存在于缓存中", serviceName);
            return;
        }
        List<String> addressList = cache.get(key);
        addressList.remove(address);
        logger.debug("将name为{} v{} group:{} 地址为{}的服务从本地缓存中删除", serviceName, version, group, address);
    }

    /**
     * 构建复合 key
     */
    private String buildKey(String serviceName, String version, String group) {
        return serviceName + ":" + version + ":" + group;
    }
}
