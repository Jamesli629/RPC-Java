package com.lbc.common.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量 SPI 加载器
 *
 * 基于 Java ServiceLoader，支持按名称获取实现类。
 * 使用方式：
 * 1. 在 META-INF/services/接口全限定名 文件中列出实现类
 * 2. 调用 SpiLoader.load(接口.class) 获取所有实现
 * 3. 调用 SpiLoader.get(接口.class, "名称") 获取指定实现
 */
public class SpiLoader {
    private static final Logger logger = LoggerFactory.getLogger(SpiLoader.class);

    private static final Map<Class<?>, Map<String, Object>> CACHE = new ConcurrentHashMap<>();

    /**
     * 加载指定接口的所有 SPI 实现
     *
     * @param service 接口 Class
     * @param <T>     接口类型
     * @return 实现类 Map（key=实现类 simpleName，value=实例）
     */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> load(Class<T> service) {
        Map<String, Object> rawMap = CACHE.computeIfAbsent(service, key -> {
            Map<String, Object> implMap = new LinkedHashMap<>();
            try {
                ServiceLoader<Object> loader = (ServiceLoader<Object>) ServiceLoader.load(key);
                for (Object impl : loader) {
                    String name = impl.getClass().getSimpleName();
                    // 去掉末尾的 "Impl" 或 "Serializer" 等后缀，简化名称
                    name = name.replaceAll("(Impl|Serializer|Balance|Cluster)$", "");
                    // 首字母小写
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                    implMap.put(name, impl);
                    logger.debug("SPI 加载: {} -> {}", name, impl.getClass().getName());
                }
            } catch (Exception e) {
                logger.warn("SPI 加载失败: {}", service.getName(), e);
            }
            return implMap;
        });
        Map<String, T> result = new LinkedHashMap<>(rawMap.size());
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            result.put(entry.getKey(), (T) entry.getValue());
        }
        return result;
    }

    /**
     * 获取指定名称的 SPI 实现
     *
     * @param service 接口 Class
     * @param name    实现名称
     * @param <T>     接口类型
     * @return 实现实例，未找到返回 null
     */
    public static <T> T get(Class<T> service, String name) {
        Map<String, T> implMap = load(service);
        return implMap.get(name);
    }

    /**
     * 获取所有 SPI 实现名称
     */
    public static <T> Set<String> getNames(Class<T> service) {
        return load(service).keySet();
    }

    /**
     * 清除缓存（用于测试或热重载）
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
