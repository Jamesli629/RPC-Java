package com.lbc.client.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Lbc
 * @date 2024/09/22 17:34
 **/
public class ServiceCache {
    //key: serviceName 服务名
    //value： addressList 服务提供者列表
    private static Map<String, List<String>> cache = new HashMap<>();

    //添加服务
    public void addServiceToCache(String serviceName, String address) {
        if (cache.containsKey(serviceName)) {
            List<String> addressList = cache.get(serviceName);
            addressList.add(address);
            System.out.println("将name为" + serviceName + "和地址为" + address + "的服务添加到本地缓存中");
        } else {
            List<String> addressList = new ArrayList<>();
            addressList.add(address);
            cache.put(serviceName, addressList);
        }
    }

    //修改服务地址
    public void replaceServiceAddress(String serviceName,String oldAddress,String newAddress){
        if(cache.containsKey(serviceName)){
            List<String> addressList=cache.get(serviceName);
            addressList.remove(oldAddress);
            addressList.add(newAddress);
        }else {
            System.out.println("修改失败，服务不存在");
        }
    }

    //从缓存中取服务地址
    public List<String> getServiceFromCache(String serviceName) {
        if (!cache.containsKey(serviceName)) {
            return null;
        }
        return cache.get(serviceName);
    }

    //从缓存中删除服务地址
    public void delete(String serviceName, String address) {
        if (!cache.containsKey(serviceName)) {
            System.out.println("删除失败，服务" + serviceName + "不存在于缓存中");
            return;
        }
        List<String> addressList = cache.get(serviceName);
        addressList.remove(address);
        System.out.println("将name为" + serviceName + "和地址为" + address + "的服务从本地缓存中删除");
    }

    /*
       在 Java 的 HashMap 和 ArrayList 中，当你对 List 进行修改（如添加或删除元素）时，
    这些修改会直接反映在 Map 中关联的值上，因为 Map 存储的是 List 的引用。
    因此，在你的 delete 方法中，当你调用 addressList.remove(address) 时，
    实际上是直接修改了 cache 中与 serviceName 关联的那个 List 对象。
     */
}
