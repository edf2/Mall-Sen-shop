# MALL-森 商城 · 架构说明

> 面向毕业设计评审与面试。本文回答三件事：系统怎么划分、为什么选这些技术、关键设计决策是什么。

## 1. 系统总体架构

```
                     ┌──────────────────────────┐
 微信小程序/浏览器    │     Nginx / 反向代理        │
 (uni-app / Vue3)    └────────────┬─────────────┘
                                  │ HTTP / WS(/ws/{sid})
                     ┌────────────▼─────────────┐
                     │   Java 后端  (Spring Boot) │
                     │  user | admin | aigc | notify │
                     │  拦截器(JWT+ThreadLocal)    │
                     └──┬─────┬──────┬──────┬────┘
                        │     │      │      │
                   ┌────▼─┐ ┌─▼───┐ ┌▼────┐ ┌▼───────┐
                   │ MySQL │ │Redis│ │Rabbit│ │WebSocket│
                   └──────┘ └─────┘ └─────┘ └────────┘
                        ▲                      ▲
                        │  HTTP (x-agent-key)  │
                     ┌──┴──────────────────────┴──┐
                     │  AI 智能体  (Python/FastAPI)  │
                     │  通义千问 + 工具调用 + Redis记忆 │
                     └─────────────────────────────┘
```

四端协作关系：

1. **C 端**（微信小程序）：浏览商品、购物车、下单、支付、催单、AI 客服/代客下单。
2. **Java 后端**：业务核心，对外提供 REST 接口；WebSocket 推送来单提醒、催单给商家后台。
3. **AI 智能体**（Python）：对话理解 + 工具调用（查商品、代客下单、改地址、退款等），内部通过共享密钥 `x-agent-key` 调 Java 接口，不直接接触用户数据表。
4. **商家后台**（Vue3+TS）：接单、拒单、派送、库存、报表；订阅 WebSocket 接收新订单提醒。

## 2. 模块划分与技术选型

### 2.1 后端多模块（Maven）

| 模块 | 职责 | 依赖关系 |
| --- | --- | --- |
| `mall-sen-common` | 常量、`BaseContext`(ThreadLocal)、枚举、异常、配置属性、`Result`、JWT/工具类 | 被其他模块依赖 |
| `mall-sen-pojo` | DTO / Entity / VO / MQ 消息 | 仅依赖 common |
| `mall-sen-server` | 启动 + Controller / Service / Mapper / 组件 / 定时任务 / 测试 | 依赖 common、pojo |

分层上：`Controller → Service → Mapper`，`Controller` 只做参数接收与结果封装（统一 `Result`），业务全部收敛在 Service；事务边界在 Service 方法上通过 `@Transactional` 或 `TransactionTemplate` 显式控制。

### 2.2 技术选型理由

| 技术 | 选型理由 |
| --- | --- |
| Java 21 + Spring Boot 3.2 | 长期支持版本、虚拟线程等新特性；Boot 3 配合 Jakarta 命名空间与内置测试栈，生态成熟、招工口径主流 |
| MyBatis + PageHelper | 复杂 SQL 可控（分页、批量、条件扣减），比 JPA 更贴合本项目对 SQL 的精细控制需求 |
| MySQL 8 + Druid | 关系数据核心存储 + 连接池监控 |
| Redis 7 | 商品缓存、`product:sales` 实时销量 ZSet、分布式锁、MQ 消费幂等标记 |
| RabbitMQ | 订单支付后的异步销量确认、库存预警；配合本地 Outbox 保证不丢 |
| WebSocket | 商家端实时来单提醒 / 客户催单（1=来单、2=催单） |
| knife4j (OpenAPI3) | 接口文档，评审演示方便 |
| FastAPI + 通义千问 | 智能体快速搭建，DashScope 兼容 OpenAI 协议，工具调用（Function Call）能力强 |
| uni-app | 一套代码同时输出小程序/H5，降低多端成本 |

## 3. 关键设计决策

### 3.1 事务提交后的副作用（Outbox + WebSocket + 缓存失效）

**问题**：支付成功后需要「发 MQ 确认销量」和「WebSocket 推送来单提醒」。若在事务内直接做，一旦事务回滚，消息/推送已经发出，会产生与数据库不一致的副作用。

**方案**：
- 写入本地 `mq_outbox` 表与订单状态更新放同一事务；
- 事务 `afterCommit` 后即时投递一次（`TransactionSynchronization`），`MqOutboxRelayTask` 每 5s 定时兜底重试；
- 消费端以 Redis `SETNX` 做幂等，保证 **at-least-once 且不重复累加销量**。

同样的原则统一应用于：
- **WebSocket 来单提醒**（`OrderServiceImpl.paySuccess`）：已改为 `afterCommit` 内推送，避免推送一个事务回滚后并不存在的订单；
- **缓存失效**：删除商品缓存统一在 `afterCommit` 后执行（见 3.7）。

> 面试话术：这是「先落库、成功后再做副作用」的可靠消息范式（本地事务消息表 / Outbox Pattern），核心解决分布式场景「库更新与消息投递」的原子性问题。

### 3.2 下单流程与防超卖

流程：`地址校验（事务外）→ 用户级分布式锁（Redis SETNX，60s TTL，Lua 原子释放）→ 事务内：条件扣减库存（product.stock >= quantity）→ 落订单 → 落明细 → 清购物车`。

- **防超卖**：`deductStock` 使用 `UPDATE ... SET stock = stock - n WHERE id = ? AND stock >= n`，受影响行数为 0 即库存不足抛异常整体回滚；
- **防重复下单**：同一用户并发提交用分布式锁串行化；
- **防篡改**：订单金额、明细金额全部由服务端按 `product.price × number` 重算，忽略客户端 `amount`；
- **订单号**：时间戳(ms) + 随机数 + 进程内自增，DB 唯一索引兜底，冲突自动重试（最多 5 次）；
- **预占库存的归还**：取消 / 拒单 / 退款统一走 `StockReleaseService.release()`，已支付订单同时回滚销量与 Redis 销量 ZSet。

### 3.3 库存账实一致（流水记录）

每次扣减/归还都插入 `inventory_record` 流水，`stockBefore/stockAfter` 取**扣减后的最新值**反推，避免并发下基于旧值的脏快照，保证对账「账实相符」。

### 3.4 支付体系与安全

- 支付策略路由 `PaymentServiceRouter`：`sky.pay.mock=true` 走 `MockPaymentService`（联调），否则走微信支付；
- **金额服务端重算**：客户端金额一律忽略；
- **mock 回调鉴权**：`/notify/mock/paySuccess` 必须携带 `mock-token`，且用 `MessageDigest.isEqual` 常量时间比较防时序攻击；
- **幂等回调**：`paySuccess` 检测 `pay_status=PAID` 直接返回，避免重复扣库存/重复推送；
- **真实微信回调**：原裸奔（不验签）路由已删除，接入前需企业认证并实现「验签 + 解密」。

### 3.5 ThreadLocal 用户上下文（BaseContext）

- 拦截器 `JwtTokenUserInterceptor` 解析 JWT 后写入 `BaseContext`；
- 在 `afterCompletion`（请求结束回调）中清理，防止线程池复用导致「跨请求身份串号」。

> 面试话术：ThreadLocal 必须配「用后即清」，本项目在拦截器 `afterCompletion` 兜底清理，避免内存泄漏与脏数据。

### 3.6 AI 智能体与后端的安全边界

- 智能体会话历史、对话数据存放在 Python 侧 Redis，与业务库隔离；
- Java → Python 调用 `/agent/**` 需携带 `x-agent-key`（共享密钥），Python 侧 `secrets.compare_digest` 校验；
- AI 代客下单复用与用户下单一致的库存预占/金额重算/事务模板，业务规则不因入口不同而漂移。

### 3.7 缓存一致性（Cache Aside + 延迟双删）

**现状**：C 端商品列表按分类聚合缓存为 `product_{categoryId}`，读取时从 Redis 合并实时销量 `product:sales`。

**变更后策略**（`ProductCacheEvictor` 统一收敛）：
1. 数据变更（下单扣库存、取消归还、支付确认销量、库存调整、后台改商品）后**先写库、提交后再删缓存**（Cache Aside 语义），不在事务内删；
2. **延迟双删**：第一次删除后延迟 1s 再删一次，补偿「读线程在写库与删缓存间隙把旧库值回写缓存」的脏读窗口；
3. 删除失败仅记日志，缓存最多短暂读到旧值，由下一次变更兜底。

**粒度说明**：当前缓存粒度是「分类列表」，失效按分类整删。更细粒度的方向是「商品维度缓存 + 分类列表由聚合得到」，代价是读路径复杂度上升；对毕业项目当前粒度 + 提交后失效 + 延迟双删已足够，也是可讲清楚的方案。

> 面试话术：缓存一致性没有银弹。本项目采用 Cache Aside 语义 + 提交后失效 + 延迟双删，把「极端并发下的脏读窗口」压缩到毫秒级；更高要求可上 Canal 订阅 binlog 异步失效，或把库存这类高频变化量改为 Redis 直读不回写 DB 缓存。

### 3.8 参数校验

- 新增 `spring-boot-starter-validation`；
- `OrdersSubmitDTO` 用 `@NotNull` 等注解 + Controller `@Valid` 触发校验；
- `GlobalExceptionHandler` 捕获 `MethodArgumentNotValidException` 返回第一条友好错误，而不是笼统的 UNKNOWN_ERROR。

## 4. 事务边界与并发要点汇总

| 场景 | 手段 |
| --- | --- |
| 下单串行化 | Redis 分布式锁（SETNX + Lua 释放 + 提交后释放） |
| 防超卖 | 条件 UPDATE 原子扣减 |
| 重复回调 / 重复投递 | 状态幂等 + Redis SETNX |
| 异步可靠性 | Outbox 本地表 + afterCommit 即时投递 + 定时兜底 |
| 副作用时序 | 全部后移到 afterCommit |
| 脏读窗口 | 提交后失效 + 延迟双删 |
| 长网络 IO | 放事务外（如地址配送校验）避免长事务占连接 |

## 5. 遗留问题与演进方向

- 商家后台 `clearCache("product_*")` 用 `KEYS` 通配删除，生产大 key 场景应改 SCAN 或精确 key；
- `product:sales` 是 Redis 与 DB 双写，极端崩溃下可能短时不一致，可改为以 DB 为准 + 定时回刷；
- 真实微信支付验签回调待企业资质后接入；
- 单机部署无高可用，可演进为 Nginx + 多实例 + Redis 哨兵。

## 6. 参考文件

- 部署：`docs/deployment.md`
- 数据库脚本：`mall-sen-shop.sql`（及 sales / pay / purchase / inventory 分库脚本）
- 中间件编排：`docker-compose.yml`
