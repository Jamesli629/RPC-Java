package com.lbc.client.proxy;

import com.lbc.client.circuitBreaker.CircuitBreaker;
import com.lbc.client.circuitBreaker.CircuitBreakerProvider;
import com.lbc.client.retry.GuavaRetry;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.client.rpcClient.impl.NettyRpcClient;
import com.lbc.client.serverCenter.ServiceCenter;
import com.lbc.client.serverCenter.ZKServiceCenter;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class ClientProxy implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientProxy.class);

    //传入参数service接口的class对象，反射封装成一个request
    private RpcClient rpcClient;
    private ServiceCenter serviceCenter;
    private CircuitBreakerProvider circuitBreakerProvider;

    public ClientProxy() {
        try {
            serviceCenter = new ZKServiceCenter();
            rpcClient = new NettyRpcClient(serviceCenter);
            circuitBreakerProvider = new CircuitBreakerProvider();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("初始化ZK连接失败，ClientProxy构造中断", e);
        } catch (Exception e) {
            throw new RuntimeException("ClientProxy初始化失败", e);
        }
    }

    //jdk动态代理，每一次代理对象调用方法，都会经过此方法增强（反射获取request对象，socket发送到服务端）
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //构建request
        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args).paramsType(method.getParameterTypes()).build();
        //获取熔断器
        CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(method.getName());
        //判断熔断器是否允许请求经过
        if (!circuitBreaker.allowRequest()) {
            //这里可以针对熔断做特殊处理，返回特殊值
            return null;
        }

        // 根据方法返回类型选择同步或异步路径
        if (method.getReturnType() == CompletableFuture.class) {
            // 异步路径：返回 CompletableFuture<Object>
            return sendAsync(request, circuitBreaker);
        } else {
            // 同步路径（向后兼容）：阻塞获取结果
            return sendSync(request, circuitBreaker);
        }
    }

    /**
     * 异步发送请求
     */
    private CompletableFuture<Object> sendAsync(RpcRequest request, CircuitBreaker circuitBreaker) {
        CompletableFuture<RpcResponse> future;
        if (serviceCenter.checkRetry(request.getInterfaceName())) {
            future = new GuavaRetry().sendServiceWithRetryAsync(request, rpcClient);
        } else {
            future = rpcClient.sendRequestAsync(request);
        }
        return future.thenApply(response -> {
            // 上报熔断器状态
            reportCircuitBreaker(response, circuitBreaker);
            return response != null ? response.getData() : null;
        });
    }

    /**
     * 同步发送请求（向后兼容）
     */
    private Object sendSync(RpcRequest request, CircuitBreaker circuitBreaker) {
        RpcResponse response;
        if (serviceCenter.checkRetry(request.getInterfaceName())) {
            response = new GuavaRetry().sendServiceWithRetry(request, rpcClient);
        } else {
            response = rpcClient.sendRequest(request);
        }
        //记录response的状态，上报给熔断器
        reportCircuitBreaker(response, circuitBreaker);
        return response != null ? response.getData() : null;
    }

    /**
     * 上报熔断器状态
     */
    private void reportCircuitBreaker(RpcResponse response, CircuitBreaker circuitBreaker) {
        if (response == null) {
            return;
        }
        if (response.getCode() == 200) {
            circuitBreaker.recordSuccess();
        }
        if (response.getCode() == 500) {
            circuitBreaker.recordFailure();
        }
    }

    public <T> T getProxy(Class<T> clazz) {
        Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, this);
        return (T) o;
    }
}
