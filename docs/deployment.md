# MALL-森 商城 · 部署文档

覆盖：环境要求、中间件、数据库初始化、Java 后端、AI 智能体、微信小程序、商家后台的启动与联调。

## 1. 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 后端编译运行 |
| Maven | 3.9+ | 构建（本机已装 4.0.0-rc5） |
| Docker + Compose | 24+ | 拉起 MySQL/Redis/RabbitMQ |
| MySQL | 8.0 | 容器内已配置 utf8mb4 |
| Redis | 7.x | 容器内已配置 AOF |
| RabbitMQ | 3.13 | 容器内已启用 management 插件 |
| Python | 3.11+ | AI 智能体（项目用 uv 管理依赖） |
| HBuilderX | 最新 | 微信小程序（uni-app）编译 |
| Node.js | 18+ | 商家后台 |

## 2. 中间件（docker-compose）

```bash
cd G:\project\JavaProject\mall-sen-shop
copy .env.example .env      # 按需修改账号密码
docker compose up -d        # 启动 mysql / redis / rabbitmq
docker compose ps           # 查看健康状态
```

- MySQL：`localhost:3306`，root / `123456`（可在 `.env` 覆盖）
- Redis：`localhost:6379`
- RabbitMQ：`localhost:5672`，管理台 `http://localhost:15672`（guest/guest）

> 首次启动 `docker compose down -v` 会删除数据卷，谨慎。

## 3. 数据库初始化

按库分别执行仓库根目录的脚本（内容可直接 `source` 或 mysql 命令导入）：

```bash
mysql -uroot -p123456 -h127.0.0.1 < mall-sen-shop.sql          # 主库
mysql -uroot -p123456 -h127.0.0.1 < mall-sen-shop-sales.sql
mysql -uroot -p123456 -h127.0.0.1 < mall-sen-shop-pay.sql
mysql -uroot -p123456 -h127.0.0.1 < mall-sen-shop-purchase.sql
mysql -uroot -p123456 -h127.0.0.1 < mall-sen-shop-inventory.sql
```

## 4. 配置环境变量

后端配置均支持环境变量注入（见 `application.yml` / `application-dev.yml`），至少需要：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD` | localhost/3306/mall-sen-shop/root/123456 | 数据库 |
| `JWT_ADMIN_SECRET_KEY` / `JWT_USER_SECRET_KEY` | 无 | 必须设置，JWT 签名密钥 |
| `AGENT_API_KEY` | change-me | 与 Python Agent 的 `AGENT_API_KEY` 保持一致 |
| `ALIOSS_ACCESS_KEY_ID` / `ALIOSS_ACCESS_KEY_SECRET` | 无 | 商品图片 OSS（不传图可先用占位） |
| `WECHAT_APPID` / `WECHAT_SECRET` | 无 | 小程序登录；mock 支付可不配 |
| `BAIDU_AK` | 无 | 当前配送范围校验已移除，可不配 |

Windows PowerShell 示例：

```powershell
$env:JWT_ADMIN_SECRET_KEY="your-admin-secret"
$env:JWT_USER_SECRET_KEY="your-user-secret"
$env:AGENT_API_KEY="your-agent-key"
```

## 5. 启动 Java 后端

```bash
cd G:\project\JavaProject\mall-sen-shop
mvn -pl mall-sen-server -am spring-boot:run
```

或打包运行：

```bash
mvn -pl mall-sen-server -am package -DskipTests
java -jar mall-sen-server\target\mall-sen-server-1.0-SNAPSHOT.jar
```

- 端口：`8080`
- 接口文档（knife4j）：`http://localhost:8080/doc.html`
- 健康检查：`http://localhost:8080/actuator/health`

## 6. 启动 AI 智能体

```bash
cd G:\project\PythonProject\mall-sen-shop-AI
uv sync                 # 或 pip install -e .
$env:AGENT_API_KEY="your-agent-key"   # 必须与后端一致
uv run python main.py
```

- 端口：`8100`（仅监听 127.0.0.1）
- Swagger：`http://localhost:8100/redoc`
- 健康检查：`http://localhost:8100/health`
- 依赖中间件：Redis（会话记忆）、通义千问 DashScope API Key（`.env` 中 `DASHSCOPE_API_KEY`）

## 7. 启动微信小程序（C 端）

用 HBuilderX 打开 `G:\project\WeChatMiniProgramproject\MALL-sen`：

1. 修改 `src/config` 下的后端 baseURL 为 `http://localhost:8080`；
2. 微信开发者工具导入 `unpackage/dist/dev/mp-weixin` 或使用 HBuilderX「运行到小程序模拟器」；
3. 若小程序真机预览，后端需内网穿透（如 cpolar），并同步修改 baseURL。

## 8. 启动商家后台（Vue3）

```bash
cd G:\project\JavaProject\mall-sen-shop\project-mallsen-admin-vue-ts
npm install
npm run dev
```

- 默认端口 `5173`，代理到 `http://localhost:8080`（见 vite 配置）。
- 登录账号见数据库 `employee` 表（演示环境默认管理员）。

## 9. 联调自测清单

1. 后端 / AI / 小程序 / 后台四个进程全部启动，`/health` 均 OK；
2. 小程序登录 → 浏览商品（走 Redis 缓存）→ 加购 → 下单；
3. 下单后商家后台 WebSocket 收到「来单提醒」；点支付（mock）后收到来单推送；
4. 商家接单 → 派送 → 完成；
5. AI 聊天：问「帮我下单两件 XX」验证 AI 代客下单闭环；
6. 取消订单 → 库存归还、销量回滚、缓存失效（Redis 中 `product_*` key 被删）。

## 10. 常见问题

- **连不上 MySQL**：检查 `docker compose ps` 是否 healthy；`allowPublicKeyRetrieval=true` 已在 JDBC URL 中。
- **JWT 相关 401**：确认 `JWT_ADMIN_SECRET_KEY` / `JWT_USER_SECRET_KEY` 已设置。
- **AI 调用 401**：`AGENT_API_KEY` 两端不一致，或 Python 未读取到环境变量。
- **mock 支付失败**：确认 `sky.pay.mock=true`（默认）且回调携带 `mock-token`。
- **WebSocket 连不上**：小程序/后台连接地址需与后端 ws 端口一致（`ws://host:8080/ws/{sid}`）。
