package com.lbc.server.health;

/**
 * 健康状态枚举
 */
public enum HealthStatus {
    /** 存活且就绪 */
    UP("UP"),
    /** 存活但未就绪（如正在下线） */
    DOWN("DOWN"),
    /** 未知 */
    UNKNOWN("UNKNOWN");

    private final String code;

    HealthStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
