package com.lbc.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 响应对象
 *
 * 生产级改进：新增 traceId 字段用于分布式链路追踪
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RpcResponse implements Serializable {
    /** 请求唯一标识，与 RpcRequest 对应，用于在连接池中匹配请求与响应 */
    private int channelId;
    /** 链路追踪 ID，与请求关联，用于 ELK/SkyWalking 关联 */
    private String traceId;
    /** 状态码 */
    private int code;
    /** 状态信息 */
    private String message;
    /** 传输数据的类型，便于自定义序列化器解析 */
    private Class<?> dataType;
    /** 具体数据 */
    private Object data;
    /** 远端异常全便于排障 */
    private String exceptionClass;
    /** 远端异常消息 */
    private String exceptionMessage;

    public static RpcResponse sussess(Object data) {
        return RpcResponse.builder()
                .code(200)
                .data(data)
                .dataType(data == null ? null : data.getClass())
                .build();
    }

    public static RpcResponse fail() {
        return RpcResponse.builder().code(500).message("服务器发生错误").build();
    }

    /**
     * 构建携带远端异常信息的失败响应，便于客户端排障
     */
    public static RpcResponse failWithException(Throwable e) {
        RpcResponse resp = new RpcResponse();
        resp.setCode(500);
        resp.setMessage("服务器发生错误: " + e.getClass().getName() + ": " + e.getMessage());
        resp.setExceptionClass(e.getClass().getName());
        resp.setExceptionMessage(e.getMessage());
        return resp;
    }
}
