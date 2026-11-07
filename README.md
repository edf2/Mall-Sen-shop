# MALL-森 百货商城（多端 · 智能体）

一个前后端分离、多端覆盖、接入大模型智能体（AI 代客服务）的电商综合项目，用于毕业设计与简历项目展示。

## 仓库组成

| 代码 | 路径 | 技术 | 说明 |
| --- | --- | --- | --- |
| Java 后端 | [mall-sen-shop](./)（本仓库，多模块 Maven） | Spring Boot 3.2 / MyBatis / Redis / RabbitMQ / MySQL | 商城后端主服务，含 C 端 / 商家后台 / AI / 支付回调接口 |
| AI 智能体 | `G:\project\PythonProject\mall-sen-shop-AI` | Python / FastAPI / 通义千问 DashScope | 对话式 AI 代客服务，负责聊天、代客下单等 Agent 工具调用 |
| 微信小程序 | `G:\project\WeChatMiniProgramproject\MALL-sen` | uni-app (Vue3) | C 端消费者 APP（uni-app 编译到微信小程序） |
| 商家后台 | `project-sky-admin-vue-ts` | Vue 3 + TypeScript | 商家运营管理后台 |

## Java 后端模块划分

本仓为 Maven 多模块工程，`com.mallsen` 为统一包名：

- `mall-sen-common`：常量、上下文（`BaseContext`）、枚举、异常、通用配置、`Result` 统一返回、工具类、JWT 等公共组件。
- `mall-sen-pojo`：DTO、Entity、VO、异步消息对象（POJO，不依赖 web）。
- `mall-sen-server`：入口模块，含 Controller（`controller/user|admin|aigc|notify`）、Service、Mapper、Interceptor、WebSocket、定时任务、Outbox 组件与单元测试。

详细架构与设计决策见 [docs/architecture.md](./docs/architecture.md)，启动与部署见 [docs/deployment.md](./docs/deployment.md)。

## 技术栈速览

- **后端**：Java 21 / Spring Boot 3.2 / MyBatis + PageHelper / Druid / Redis / RabbitMQ / WebSocket / knife4j(OpenAPI) / JJWT
- **AI**：Python / FastAPI / 通义千问（DashScope 兼容 OpenAI 接口）/ Redis 会话记忆
- **前端**：uni-app（小程序 + H5）/ 商家后台 Vue3 + TS
- **基础设施**：MySQL 8 / Redis 7 / RabbitMQ 3.13（docker-compose 一键拉起）

## 核心设计亮点（面试速览）

- **事务提交后副作用**：Outbox 消息投递、WebSocket 来单推送、缓存失效统一推迟到 `afterCommit`，杜绝「事务回滚却已发出副作用」。
- **可靠异步销量确认**：本地事务表 + 定时兜底（Outbox 模式），配合 Redis 幂等实现 at-least-once。
- **防超卖**：库存预占采用条件 UPDATE 原子扣减，叠加用户级分布式锁串行化，防止重复下单与库存穿透。
- **缓存一致性**：Cache Aside + 提交后失效 + 延迟双删，见 [docs/architecture.md](./docs/architecture.md#7-缓存一致性)。
- **支付安全**：金额由服务端重算（忽略客户端金额）、mock 回调带 token 鉴权、支付策略可切换（Mock / 微信）。
- **参数校验**：`@Valid` + DTO 注解统一校验，友好错误提示。

## 快速开始（简版）

```bash
# 1. 拉取中间件
cp .env.example .env
docker compose up -d

# 2. 初始化数据库（见 deployment 文档）
mysql -uroot -p123456 < mall-sen-shop.sql

# 3. 配置环境变量（JWT 密钥、OSS、AI key 等），见 docs/deployment.md
# 4. 启动后端
mvn -pl mall-sen-server -am spring-boot:run

# 5. 启动 AI 智能体（Python 侧）
uv run python main.py

# 6. 微信小程序用 HBuilderX 导入后运行
```

## 测试

```bash
mvn -pl mall-sen-server test   # 123 个单测（下单/支付/库存/通知/AI等）
```

## 目录（后端）

```
mall-sen-common/      # 公共组件
mall-sen-pojo/        # DTO/Entity/VO/消息
mall-sen-server/      # 应用入口 + Controller/Service/Mapper
  ├─ controller/      # user(小程序C端) admin(商家) aigc(AI) notify(回调)
  ├─ component/       # Outbox 中继、订单异步消费、商品缓存失效器等
  ├─ interceptor/     # JWT 校验 + ThreadLocal(BaseContext) 清理
  └─ websocket/       # 来单提醒 / 催单群发
project-sky-admin-vue-ts/   # 商家后台前端
*.sql                 # 各库初始化脚本
docker-compose.yml    # MySQL/Redis/RabbitMQ 编排
```