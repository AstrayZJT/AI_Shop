# AI Shop

一个基于 Spring Boot 3 的 AI 电商平台示例项目，包含客户端、管理端、订单履约、售后流程、知识库 RAG，以及可转人工的 AI 客服。

当前项目已经不是最早的“聊天演示页”了，而是一个继续在同一仓库内扩展出来的完整业务雏形：

- 客户端：注册、登录、商品浏览、购物车、下单、订单查询、地址维护、AI 客服
- 管理端：商品管理、订单履约、退款审核、知识库导入、用户查看、AI 会话接管
- AI 客服：商品推荐、订单查询、订单动作代办、RAG 检索、人工转接、后台人工回复回流

## 1. 当前架构

这个项目现在的形态是：

- 前端分离为两个独立界面
  - 客户端：`/client/index.html`
  - 管理端：`/admin/index.html`
- 后端统一提供 REST API
- 静态前端资源仍然由同一个 Spring Boot 服务托管，方便本地联调

也就是说，它不是“前后端两个仓库”的拆分方式，但已经是“前端页面与后端接口解耦”的结构。

## 2. 技术栈

- Java 21
- Spring Boot 3.5.3
- Spring Web
- Spring Data JPA
- Spring Session JDBC
- PostgreSQL
- pgvector
- LangChain4j
- LangGraph4j
- 原生 HTML / CSS / JavaScript

## 3. 先看最重要的：AI 到底怎么接的

这一段最关键。

项目现在支持两种运行模式：

### 3.1 本地联调模式

当你设置：

```powershell
$env:SHOP_AI_ENABLED="false"
```

系统不会调用真实大模型接口，而是启用本地兜底实现。

对应代码：

- 聊天模型：`src/main/java/com/aishop/config/LocalChatModelConfig.java`
- 向量模型：`src/main/java/com/aishop/config/LocalEmbeddingConfig.java`

这个模式下的特点：

- AI 回复不是远程大模型生成的，而是本地兜底逻辑
- embedding 也不是真实语义向量，而是本地 64 维简化向量
- 向量检索走的是内存向量存储，不是 pgvector
- 但知识库原文、切块文本、embedding 缓存仍然会落业务表，便于联调整个流程

适合场景：

- 先把注册、登录、前端页面、购物车、订单、客服流程跑通
- 没有真实模型 key 时进行本地开发
- 调接口、调页面、调业务逻辑

不适合场景：

- 验证真实大模型回答质量
- 验证真实语义检索效果
- 验证真实 RAG + pgvector 召回质量

一句话概括：

本地联调模式能跑业务，但它不代表真实 AI 能力。

### 3.2 真实 AI 模型模式

当你设置：

```powershell
$env:SHOP_AI_ENABLED="true"
$env:OPENAI_API_KEY="你的 Key"
```

系统会走真实模型配置。

对应代码：

- 模型装配：`src/main/java/com/aishop/config/AiModelConfig.java`
- 业务配置：`src/main/java/com/aishop/config/ShopProperties.java`
- 默认配置：`src/main/resources/application.yml`

当前默认接的是 OpenAI 兼容协议，配置指向阿里云 DashScope：

- `base-url`: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- 聊天模型：`qwen-plus`
- 向量模型：`text-embedding-v4`

也就是说，代码层面用的是 OpenAI 兼容客户端，但你实际可以接的是 DashScope 的兼容接口。

这个模式下的特点：

- 聊天回复会真实调用远程模型
- embedding 会真实调用远程向量模型
- 如果当前向量数据源是 PostgreSQL，则会使用 pgvector 存储向量索引
- 控制台会输出模型请求日志和响应日志

注意两点：

1. `SHOP_AI_ENABLED` 默认值是 `true`
2. `OPENAI_API_KEY` 为空时，应用未必会在启动阶段就报错，但一旦真正调用模型，请求大概率会失败

所以本地开发时建议你明确指定模式，不要靠默认值猜。

### 3.3 你的 key 我知不知道

不知道。

这个项目不会把真实 key 写死在代码里，也不应该提交到 git。

当前代码只会从环境变量读取：

- `OPENAI_API_KEY`

你在 IDEA 里填的 Environment Variables，或者你在终端里临时设置的环境变量，只在你的本地进程里生效，不属于仓库内容。

## 4. 环境变量清单

下面这些名字要和配置文件保持完全一致。

### 4.1 数据库

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="agentdemo"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD=""
```

### 4.2 AI 模型

```powershell
$env:SHOP_AI_ENABLED="true"
$env:OPENAI_API_KEY="你的 Key"
```

如果只想本地联调，不接真实模型：

```powershell
$env:SHOP_AI_ENABLED="false"
```

### 4.3 RAG / pgvector

如果你希望向量库和主业务库共用同一个 PostgreSQL，可以不额外配置，默认会回退到 `DB_*`。

如果你希望单独指定向量库连接，变量名如下：

```powershell
$env:RAG_PGVECTOR_HOST="localhost"
$env:RAG_PGVECTOR_PORT="5432"
$env:RAG_PGVECTOR_DATABASE="agentdemo"
$env:RAG_PGVECTOR_USERNAME="postgres"
$env:RAG_PGVECTOR_PASSWORD=""
$env:RAG_PGVECTOR_TABLE="knowledge_embeddings"
```

### 4.4 其他业务配置

```powershell
$env:AGENT_SEARCH_MAX_RESULTS="5"
$env:AGENT_WEBPAGE_MAX_CHARACTERS="8000"
```

## 5. 本地启动

### 5.1 启动前准备

- 安装 Java 21
- 安装 Maven
- 准备 PostgreSQL
- 如果你要跑真实向量检索，建议数据库启用 pgvector 扩展

### 5.2 PowerShell 启动示例

### 真实 AI 模型模式

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="agentdemo"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD=""
$env:SHOP_AI_ENABLED="true"
$env:OPENAI_API_KEY="你的 Key"
mvn spring-boot:run
```

### 本地联调模式

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="agentdemo"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD=""
$env:SHOP_AI_ENABLED="false"
mvn spring-boot:run
```

### 指定端口启动

如果 `8080` 被占用，可以直接换端口：

```powershell
mvn --% spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

### 5.3 IDEA 启动方式

在 IDEA 的 `Run/Debug Configurations` 里配置 Environment Variables，变量名必须和上面完全一致。

最常见的是这几项：

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SHOP_AI_ENABLED`
- `OPENAI_API_KEY`

如果你在 IDEA 里已经配置好了这些环境变量，直接运行 Spring Boot 主类即可。

### 5.4 看日志

要看完整日志，建议：

- 用 `mvn spring-boot:run` 在终端启动
- 或者在 IDEA 的 Run 窗口里启动

真实 AI 模式下，由于配置里打开了：

- `shop.ai.log-requests=true`
- `shop.ai.log-responses=true`

所以你会在控制台看到模型调用日志，这是正常的。

## 6. 访问地址

默认端口是 `8080`。

- 客户端首页：`http://localhost:8080/`
- 客户端直达：`http://localhost:8080/client/index.html`
- 助手入口：`http://localhost:8080/assistant.html`
- 管理端：`http://localhost:8080/admin/index.html`

如果你改成了 `8082`，把端口替换成 `8082` 即可。

## 7. 默认账号

启动后会自动初始化演示数据：

- 客户端账号：`demo / demo123`
- 管理端账号：`admin / admin123`

对应初始化代码：

- `src/main/java/com/aishop/config/DataInitializer.java`

## 8. 当前业务能力

### 8.1 客户端

- 用户注册、登录、退出
- 个人资料维护
- 商品列表、分类、搜索、详情浏览
- 购物车增删改
- 结算下单
- 订单列表
- 发货前修改收货地址
- 用户侧取消订单
- 用户侧确认收货
- 用户侧申请退款
- AI 客服对话

### 8.2 管理端

- 仪表盘数据总览
- 商品新增 / 编辑
- 订单状态推进
- 发货时填写物流公司和运单号
- 退款审核
- 用户列表
- 知识库文档导入与检索
- AI 会话列表
- 人工接管队列
- 人工客服回复并结案

### 8.3 AI 客服

当前 AI 客服已经能处理这些常见场景：

- 商品推荐
- 查询最近订单
- 查询具体订单状态、物流、地址
- 生成下单草稿
- 确认草稿生成正式订单
- 取消草稿
- 直接代办取消订单
- 直接代办确认收货
- 直接代办申请退款
- 直接代办修改发货前订单地址
- 查询知识库中的售后、物流、政策内容
- 用户主动要求“转人工”后进入人工接管流程
- 管理端人工回复后，消息会回流到客户端会话

## 9. RAG 是怎么实现的

这里单独讲，因为这是这个项目里最容易被误解的一段。

### 9.1 原文放在哪里

知识库原文存在业务表：

- 表：`knowledge_documents`
- 字段：`content`

实体类：

- `src/main/java/com/aishop/domain/KnowledgeDocument.java`

### 9.2 切块文本放在哪里

导入知识库后，系统会自动切块，切块内容存在：

- 表：`knowledge_chunks`
- 字段：`chunk_text`

实体类：

- `src/main/java/com/aishop/domain/KnowledgeChunk.java`

切块逻辑在：

- `src/main/java/com/aishop/service/KnowledgeService.java`

### 9.3 向量信息放在哪里

向量信息现在分两层：

1. 业务表缓存
   - 表：`knowledge_chunks`
   - 字段：`embedding_json`
   - 用来缓存每个 chunk 的 embedding 数组

2. 检索索引
   - 真实 AI 模式 + PostgreSQL 场景下：写入 pgvector 表，默认表名是 `knowledge_embeddings`
   - 本地联调模式下：走内存向量存储，不写 pgvector

对应代码：

- `src/main/java/com/aishop/service/PgVectorEmbeddingStoreFacade.java`
- `src/main/java/com/aishop/service/InMemoryEmbeddingStoreFacade.java`
- `src/main/java/com/aishop/config/EmbeddingStoreConfig.java`

### 9.4 启动时会发生什么

应用启动后会执行知识索引同步：

- 读取 `knowledge_chunks`
- 检查 `embedding_json` 是否存在、维度是否匹配
- 必要时重新生成 embedding
- 重建当前进程使用的向量检索索引

对应代码：

- `src/main/java/com/aishop/service/KnowledgeIndexSynchronizer.java`

### 9.5 当前 RAG 的真实结论

请直接按下面这条理解：

- `SHOP_AI_ENABLED=false`：只有“本地 embedding + 内存向量检索”，不是生产级 RAG
- `SHOP_AI_ENABLED=true` 且向量数据源为 PostgreSQL：才是“真实远程 embedding + pgvector 检索”

## 10. 用户信息、会话、订单分别存在哪

常用表如下：

- 用户：`app_users`
- 购物车：`carts`
- 购物车项：`cart_items`
- 订单：`orders`
- 订单项：`order_items`
- AI 下单草稿：`pending_order_drafts`
- AI 会话：`assistant_sessions`
- AI 消息：`assistant_messages`
- 知识库原文：`knowledge_documents`
- 知识库切块：`knowledge_chunks`
- pgvector 索引表：`knowledge_embeddings`

用户实体：

- `src/main/java/com/aishop/domain/AppUser.java`

AI 会话新增了 `service_status` 字段，用于区分：

- `ACTIVE`
- `ESCALATED`
- `RESOLVED`

为兼容旧 PostgreSQL 表结构，启动时会自动补齐该列：

- `src/main/java/com/aishop/config/LegacyPostgresSchemaMigrator.java`

## 11. 主要接口

### 11.1 认证

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `PUT /api/auth/profile`

### 11.2 商品与购物车

- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/products/search`
- `GET /api/categories`
- `GET /api/cart`
- `POST /api/cart/items`
- `PATCH /api/cart/items/{itemId}`
- `DELETE /api/cart/items/{itemId}`
- `POST /api/cart/checkout`

### 11.3 订单

- `GET /api/orders`
- `GET /api/orders/{id}`
- `POST /api/orders/draft`
- `GET /api/orders/draft/current`
- `POST /api/orders/confirm`
- `DELETE /api/orders/draft`
- `PATCH /api/orders/{id}/cancel`
- `PATCH /api/orders/{id}/confirm-receipt`
- `PATCH /api/orders/{id}/refund`
- `PATCH /api/orders/{id}/shipping-address`

### 11.4 AI 客服

- `POST /api/assistant/chat`
- `GET /api/assistant/sessions`
- `POST /api/assistant/sessions`
- `GET /api/assistant/sessions/{id}`
- `POST /api/assistant/sessions/{id}/escalate`
- `GET /api/assistant/sessions/{id}/messages`

### 11.5 管理端

- `GET /api/admin/dashboard`
- `GET /api/admin/products`
- `POST /api/admin/products`
- `PUT /api/admin/products/{id}`
- `GET /api/admin/orders`
- `PATCH /api/admin/orders/{id}/status`
- `PATCH /api/admin/orders/{id}/refund-review`
- `GET /api/admin/knowledge/documents`
- `GET /api/admin/knowledge/search`
- `POST /api/admin/knowledge/import`
- `GET /api/admin/users`
- `GET /api/admin/assistant/sessions`
- `GET /api/admin/assistant/escalations`
- `GET /api/admin/assistant/sessions/{id}/messages`
- `POST /api/admin/assistant/sessions/{id}/reply`
- `GET /api/admin/assistant/drafts`

## 12. 开发提示

### 12.1 客户端和管理端同时调试

当前客户端和管理端都跑在同一个服务域名下，共用 session cookie。

如果你在同一个浏览器里同时打开客户端和管理端：

- 登录 `admin` 可能会顶掉 `demo`
- 登录 `demo` 也可能会顶掉 `admin`

本地联调建议：

- 客户端和管理端用不同浏览器
- 或者一个用普通窗口，一个用无痕窗口

### 12.2 8080 端口占用

如果启动时报 `Port 8080 was already in use`，可以：

- 关闭占用进程
- 或者直接改端口启动

示例：

```powershell
mvn --% spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

## 13. 构建

```powershell
mvn clean package
```

快速编译：

```powershell
mvn -q -DskipTests compile
```

## 14. 关键代码位置

- AI 模型接入：`src/main/java/com/aishop/config/AiModelConfig.java`
- 本地 AI 兜底：`src/main/java/com/aishop/config/LocalChatModelConfig.java`
- 本地 embedding：`src/main/java/com/aishop/config/LocalEmbeddingConfig.java`
- AI 客服主逻辑：`src/main/java/com/aishop/service/AssistantService.java`
- 管理端客服接管：`src/main/java/com/aishop/service/AdminService.java`
- RAG 导入与检索：`src/main/java/com/aishop/service/KnowledgeService.java`
- 向量索引同步：`src/main/java/com/aishop/service/KnowledgeIndexSynchronizer.java`
- pgvector 存储：`src/main/java/com/aishop/service/PgVectorEmbeddingStoreFacade.java`
- 自动补旧表结构：`src/main/java/com/aishop/config/LegacyPostgresSchemaMigrator.java`

---

如果你现在要快速判断“我跑起来的是不是真 AI”，最简单的方法只有两个：

1. 看你有没有设置 `SHOP_AI_ENABLED=true` 和 `OPENAI_API_KEY`
2. 看控制台里有没有真实模型请求日志

这两个都满足，才说明当前进程真的在调远程模型。
