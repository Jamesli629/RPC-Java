package com.lbc.server.server.work;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.server.provider.ServiceProvider;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;


@AllArgsConstructor
public class WorkThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WorkThread.class);

    private Socket socket;
    private ServiceProvider serviceProvide;

    @Override
    public void run() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            //读取客户端传过来的request
            RpcRequest rpcRequest = (RpcRequest) ois.readObject();
            //反射调用服务方法获取返回值
            RpcResponse rpcResponse = getResponse(rpcRequest);
            //向客户端写入response
            oos.writeObject(rpcResponse);
            oos.flush();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("WorkThread处理请求失败", e);
        }
    }

    private RpcResponse getResponse(RpcRequest rpcRequest) {
        //得到服务名
        String interfaceName = rpcRequest.getInterfaceName();
        //得到服务端相应服务实现类
        Object service = serviceProvide.getService(interfaceName);
        //反射调用方法
        Method method = null;
        try {
            method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamsType());
            Object invoke = method.invoke(service, rpcRequest.getParams());
            return RpcResponse.sussess(invoke);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("方法执行错误，接口: {}, 方法: {}", interfaceName, rpcRequest.getMethodName(), e);
            return RpcResponse.fail();
        }
    }
}
