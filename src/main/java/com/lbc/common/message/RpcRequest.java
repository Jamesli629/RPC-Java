package com.lbc.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author Lbc
 * @date 2024/09/16 14:48
 **/


//定义请求信息格式RpcRequest
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RpcRequest implements Serializable {
    //请求唯一标识，用于在连接池中匹配请求与响应
    private int channelId;
    //服务类名，客户端只知道接口
    private String interfaceName;
    //调用的方法名
    private String methodName;
    //参数列表
    private Object[] params;
    //参数类型
    private Class<?>[] paramsType;
    //服务版本（用于灰度发布、AB测试），为空则使用默认版本
    private String version;
    //服务分组（用于流量隔离），为空则使用默认分组
    private String group;
}
