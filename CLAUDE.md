# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

RPC-Java 是一个用 Java 实现的轻量级 RPC（远程过程调用）框架，支持服务注册与发现、负载均衡、限流、熔断和重试。网络通信基于 Netty，服务注册中心使用 ZooKeeper（Curator 客户端）。

## 构建与运行

项目使用 Maven 构建（`pom.xml`），依赖 Lombok、Netty 4.1.51、Curator 5.1.0（ZooKeeper）、FastJSON、Guava Retrying。

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=AppTest

# 打包
mvn clean package
```

**运行前提**：本地需启动 ZooKeeper，连接地址硬编码为 `127.0.0.1:2181`（见 `ZKServiceCenter.java`）。启动顺序：先运行 `TestServer.main()`，再运行 `TestClient.main()`。

## 架构

### 模块划分（按包结构）

源码位于 `src/main/java/com/lbc/`，分为三个顶层模块：

- **`common`** — 客户端与服务端共享的接口与数据结构，两边都依赖此模块
  - `message/` — RPC 协议：`RpcRequest`（请求）、`RpcResponse`（响应，含 code/message/dataType/data）、`MessageType`（REQUEST=0, RESPONSE=1）
  - `serializer/` — 序列化抽象层：`Serializer` 接口（0=Java原生，1=JSON），`MyEncoder`/`MyDecoder` 是对应的 Netty 编解码器
  - `service/`、`pojo/` — 示例服务接口与实体（`UserService`、`User`）

- **`server`** — 服务端
  - `server/RpcServer` — 服务端接口（`start`/`stop`），有三个实现：`SimpleRPCRPCServer`（BIO 单线程）、`ThreadPoolRPCRPCServer`（线程池）、`NettyRPCRPCServer`（Netty，生产使用）
  - `provider/ServiceProvider` — 本地服务注册表（`Map<interfaceName, instance>`），向 ZK 注册服务，并提供限流器
  - `serviceRegister/` — 服务注册抽象，`ZKServiceRegister` 通过 Curator 写入 ZK 节点
  - `netty/` — Netty 服务端引导与处理器：`NettyServerInitializer` 装配编解码链路，`NettyRPCServerHandler` 接收请求 → 限流 → 反射调用 → 写回响应
  - `rateLimit/` — 接口级限流：`TokenBucketRateLimitImpl` 令牌桶实现，`RateLimitProvider` 管理各接口的限流器

- **`client`** — 客户端
  - `proxy/ClientProxy` — JDK 动态代理 + `InvocationHandler`，拦截接口方法调用，封装为 `RpcRequest` 发出
  - `rpcClient/RpcClient` — 底层通信接口（`sendRequest`），实现类：`NettyRpcClient`（Netty）、`SimpleSocketRpcCilent`（BIO）
  - `serverCenter/` — 服务发现：`ServiceCenter` 接口，`ZKServiceCenter` 实现（Curator 客户端 + 本地 `ServiceCache` + `WatchZK` 监听 ZK 变更）
  - `balance/` — 负载均衡策略：`RandomLoadBalance`、`RoundLoadBalance`、`ConsistencyHashBalance`（默认）
  - `circuitBreaker/` — 熔断器：三态（CLOSED→OPEN→HALF_OPEN），按方法名粒度由 `CircuitBreakerProvider` 管理
  - `retry/` — 基于 Guava Retrying 的重试，仅对 ZK 中 `CanRetry` 白名单里的服务生效
  - `cache/ServiceCache` — 服务地址本地缓存，减少对 ZK 的频繁查询
  - `netty/` — Netty 客户端引导与处理器：`NettyClientHandler` 将响应存入 `AttributeKey`，`sendRequest` 阻塞等待该值

### 调用链路

客户端接口调用 → `ClientProxy.invoke`（构建请求 → 熔断判断 → 重试判断 → 发送）→ `NettyRpcClient.sendRequest`（从 ZK 发现地址 → Netty 写出）→ 服务端 `NettyRPCServerHandler.channelRead0`（限流 → 从 `ServiceProvider` 取实例 → 反射调用 → 写回）→ 客户端 `NettyClientHandler` 接收响应存入 AttributeKey → `sendRequest` 阻塞取出返回。

### 关键设计要点

- **接口即服务标识**：服务名使用接口的全限定名（`method.getDeclaringClass().getName()`），注册与发现都以此为 key。客户端只需持有接口，无需依赖实现类。
- **序列化选型通过字节首字节标识**：`MyEncoder`/``MyDecoder` 约定第一个字节为序列化器编号（0/1），扩展新序列化方式只需实现 `Serializer` 并在 `getSerializerByCode` 注册。
- **幂等性控制**：重试仅对白名单服务开启，白名单存储在 ZK 的 `/CanRetry` 节点下，由 `checkRetry` 检查。
- **限流粒度**：按接口名独立限流，每个接口一个 `TokenBucketRateLimitImpl` 实例（注意：当前令牌桶的 `RATE`/`CAPACITY` 用 `static` 修饰，多实例会共享配置，属于已知缺陷）。
- **服务端实现选择**：`TestServer` 使用 `NettyRPCRPCServer`，旧的 BIO/线程池实现保留在 `server/impl/` 中供对比参考。
