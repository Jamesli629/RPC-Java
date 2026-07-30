package com.lbc.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置管理器（单例）
 * 启动时从 classpath 加载 rpc.properties，支持配置中心覆盖
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static volatile ConfigManager instance;

    private static final String CONFIG_FILE = "rpc.properties";

    private final Properties properties;

    /** 可选配置中心，预留扩展（Nacos/Apollo） */
    private ConfigCenter configCenter;

    private ConfigManager() {
        properties = new Properties();
        loadFromClasspath();
    }

    /**
     * 获取单例实例
     */
    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    /**
     * 从 classpath 加载配置文件
     */
    private void loadFromClasspath() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("配置文件 {} 加载成功，共 {} 项", CONFIG_FILE, properties.size());
            } else {
                logger.warn("未找到配置文件 {}，将全部使用默认值", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("加载配置文件 {} 失败", CONFIG_FILE, e);
        }
    }

    /**
     * 设置配置中心（可选，预留扩展）
     * 设置后，配置中心的值将覆盖配置文件中的值
     */
    public void setConfigCenter(ConfigCenter configCenter) {
        this.configCenter = configCenter;
        logger.info("已设置配置中心: {}", configCenter.getClass().getSimpleName());
    }

    /**
     * 获取配置中心（可能为 null）
     */
    public ConfigCenter getConfigCenter() {
        return configCenter;
    }

    /**
     * 获取字符串配置项
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值，优先从配置中心获取，其次从配置文件，最后使用默认值
     */
    public String getString(String key, String defaultValue) {
        // 优先从配置中心获取
        if (configCenter != null) {
            String value = configCenter.getProperty(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        // 从配置文件获取
        return properties.getProperty(key, defaultValue);
    }

    /**
     * 获取整数配置项
     */
    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 {} 不是有效整数，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取长整数配置项
     */
    public long getLong(String key, long defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 {} 不是有效长整数，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取浮点数配置项
     */
    public double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 {} 不是有效浮点数，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取布尔配置项
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * 手动设置配置项（用于测试或动态覆盖）
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * 判断是否包含某个配置项
     */
    public boolean containsKey(String key) {
        return properties.containsKey(key) || (configCenter != null && configCenter.getProperty(key) != null);
    }
}
