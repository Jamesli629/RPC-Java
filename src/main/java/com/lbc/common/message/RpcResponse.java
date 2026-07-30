package com.lbc.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author Lbc
 * @date 2024/09/16 14:50
 **/

//定义返回信息格式RpcResponse（类似http格式）
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RpcResponse implements Serializable {
    //请求唯一标识，与RpcRequest对应，用于在连接池中匹配请求与响应
    private int channelId;
    //状态码
    private int code;
    //状态信息
    private String message;
    //更新：加入传输数据的类型，以便在自定义序列化器中解析
    private Class<?> dataType;
    //具体数据
    private Object data;
    //构造成功信息
    public static RpcResponse sussess(Object data){
        return RpcResponse.builder()
                .code(200)
                .data(data)
                .dataType(data == null ? null : data.getClass())
                .build();
    }
    //构造失败信息
    public static RpcResponse fail(){
        return RpcResponse.builder().code(500).message("服务器发生错误").build();
    }
}
