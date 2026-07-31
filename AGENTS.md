# Repository Guidelines

> **语言偏好**：本文件及本仓库的所有文档、注释、提交信息、PR 描述均优先使用中文撰写。代码中的标识符、变量名使用英文，但注释和文档内容使用中文。与用户交流时也使用中文。

## 项目结构 & 模块组织

这是一个基于 Maven 构建的 Java RPC 框架。源码位于 src/main/java/com/lbc/，分为三个顶层模块：

- **common** — 客户端与服务端共享的接口与数据结构
  - message/ — RPC 协议：RpcRequest（请求）、RpcResponse（响应）、MessageType（消息类型枚举）
  - serializer/ — 序列化抽象层：Serializer 接口（0=Java原生，1=JSON），MyEncoder/MyDecoder 为 Netty 编解码器
  - service/、pojo/ — 示例服务接口与实体（UserService、User）
- **server** — 服务端实现
  - server/ — RpcServer 接口及三个实现：SimpleRPCRPCServer（BIO 单线程）、ThreadPoolRPCRPCServer（线程池）、NettyRPCRPCServer（生产使用）
  - provider/ServiceProvider — 本地服务注册表，向 ZK 注册服务
  - serviceRegister/ — ZKServiceRegister 通过 Curator 写入 ZK 节点
  - 
etty/ — Netty 服务端引导与处理器：NettyServerInitializer 装配编解码链路，NettyRPCServerHandler 处理请求
  - ateLimit/ — 接口级限流：TokenBucketRateLimitImpl 令牌桶实现，RateLimitProvider 管理各接口限流器
- **client** — 客户端实现
  - proxy/ClientProxy — JDK 动态代理，拦截接口方法调用封装为 RpcRequest 发出
  - pcClient/ — NettyRpcClient（生产）、SimpleSocketRpcCilent（BIO）
  - serverCenter/ — 服务发现：ZKServiceCenter（Curator 客户端 + ServiceCache + WatchZK 监听 ZK 变更）
  - alance/ — 负载均衡策略：RandomLoadBalance、RoundLoadBalance、ConsistencyHashBalance（默认）
  - circuitBreaker/ — 三态熔断器（CLOSED→OPEN→HALF_OPEN），按方法名粒度由 CircuitBreakerProvider 管理
  - etry/ — 基于 Guava Retrying 的重试，仅对 ZK /CanRetry 白名单中的服务生效
  - cache/ServiceCache — 服务地址本地缓存

测试代码位于 src/test/java/com/lbc/，配置文件位于 src/main/resources/。

## 构建与运行命令

`ash
mvn compile                    # 编译源码
mvn test                       # 运行全部测试
mvn test -Dtest=AppTest        # 运行单个测试类
mvn clean package              # 打包为 JAR
`

**运行前提**：本地需启动 ZooKeeper，连接地址为 127.0.0.1:2181（见 ZKServiceCenter.java）。启动顺序：先运行 TestServer.main()，再运行 TestClient.main()。

## 编码规范 & 命名约定

- 语言：Java，UTF-8 编码（pom.xml 中 project.build.sourceEncoding 已配置）
- 包名：全小写（com.lbc.server、com.lbc.client.balance）
- 类名：大驼峰（RpcRequest、NettyRpcClient、ServiceProvider）
- 接口 + 实现分离：接口放在包级别，实现类置于 impl/ 子包中（如 RpcServer → impl/NettyRPCRPCServer）
- 使用 Lombok（@Data、@Slf4j 等）减少样板代码
- 序列化类型通过 payload 首字节标识（0=Java原生，1=JSON），扩展新序列化方式只需实现 Serializer 接口并在 getSerializerByCode 中注册

## 测试规范

- 测试框架：JUnit 3（xtends TestCase）
- 测试类命名：<ClassName>Test（如 AppTest）
- 测试文件置于 src/test/java/com/lbc/，与主源码包结构保持一致
- 运行方式：mvn test 或 mvn test -Dtest=ClassNameTest

## 提交 & PR 规范

提交信息采用中文前缀约定（参考项目 Git 历史）：

- 新增: — 新功能或能力
- 配置: — 配置变更
- 文档: — 文档更新
- 修复: — Bug 修复
- 记录原始数据: — 原始数据 / 压测记录

每次提交保持原子性，一个提交只做一件事。提交 PR 时需包含清晰的变更描述、关联的 issue 编号，以及对调用链路或 ZK 节点结构的影响说明。
