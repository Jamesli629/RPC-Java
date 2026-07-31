package com.lbc.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 请求对象
 *
 * 生产级改进：新增 traceId 字段用于分布式链路追踪
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RpcRequest implements Serializable {
    /** 请求唯一标识，用于在连接池中匹配请求与响应 */
    private int channelId;
    /** 链路追踪 ID，贯穿整条调用链，用于 ELK/SkyWalking 关联 */
    private String traceId;
    /** 请求发送时间戳（毫秒），用于计算 RT */
    private long timestamp = System.currentTimeMillis();
    /** 幂等键，用于重试去重 */
    private String idempotencyKey;
    /** 服务类名，客户端只知道接口 */
    private String interfaceName;
    /** 调用的方法名 */
    private String methodName;
    /** 参数列表 */
    private Object[] params;
    /** 参数类型 */
    private Class<?>[] paramsType;
    /** 服务版本（用于灰度发布、AB测试），为空则使用默认版本 */
    private String version;
    /** 服务分组（用于流量隔离），为空则使用默认分组 */
    private String group;
}
