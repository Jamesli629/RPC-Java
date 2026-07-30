package com.lbc.common.config;

/**
 * 配置中心接口（预留扩展）
 * <p>
 * 后续可接入 Nacos、Apollo、ZooKeeper 等配置中心实现动态配置推送。
 * 通过 {@link ConfigManager#setConfigCenter(ConfigCenter)} 设置后，
 * 配置中心的值将覆盖本地配置文件中的值。
 * <p>
 * 使用示例：
 * <pre>
 *     ConfigManager.getInstance().setConfigCenter(new NacosConfigCenter("127.0.0.1:8848"));
 * </pre>
 */
public interface ConfigCenter {

    /**
     * 获取配置项
     *
     * @param key 配置键
     * @return 配置值，如果不存在返回 null
     */
    String getProperty(String key);

    /**
     * 添加配置变更监听器
     *
     * @param key      配置键
     * @param listener 变更监听器
     */
    void addListener(String key, ConfigChangeListener listener);

    /**
     * 配置变更监听器
     */
    @FunctionalInterface
    interface ConfigChangeListener {
        /**
         * 配置变更时回调
         *
         * @param key   配置键
         * @param oldValue 旧值
         * @param newValue 新值
         */
        void onChange(String key, String oldValue, String newValue);
    }
}
