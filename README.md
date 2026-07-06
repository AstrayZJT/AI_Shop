# AI Shop

一个基于 Spring Boot 3 的 AI 电商平台示例项目，包含客户端、管理端、订单履约、售后流程、知识库 RAG，以及可转人工的 AI 客服。

当前项目已经不是最早的“聊天演示页”了，而是一个继续在同一仓库内扩展出来的完整业务雏形：

- 客户端：注册、登录、商品浏览、场景化选品推荐、购物车、优惠码结算、下单、订单查询、地址维护、AI 客服
- 管理端：商品管理、营销活动管理、订单履约、售后工作台、退款审核、知识库导入、用户查看、AI 会话接管
- AI 客服：商品推荐、订单查询、订单动作代办、优惠活动问答、RAG 检索、人工转接、后台人工回复回流

## 1. 当前架构

这个项目现在的形态是：

- 前端分离为两个独立界面
  - 客户端：`/client/index.html`
  - 管理端：`/admin/index.html`
- 后端统一提供 REST API
- 当前保留两种前端运行方式
  - 集成模式：静态前端资源仍然由同一个 Spring Boot 服务托管，方便本地联调
  - 分离模式：新增 `frontend/` 独立前端工程，可单独用 Vite 启动客户端和管理端

也就是说，它不是“前后端两个仓库”的拆分方式，但已经是“前端页面与后端接口解耦”，并支持独立前端开发服务器的结构。

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
- Vite（独立前端开发服务器）

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
- 商品、购物车、订单、售后、人工接管、知识库导入这些业务流程都是真实后端逻辑，不是只在前端写死的假数据
- 也就是说，这个模式里“假的”主要是模型推理能力和向量检索形态，不是电商流程本身
- 你在控制台里看不到真实远程模型的请求 / 响应日志
- 如果只看“页面能聊、AI 有回复”，并不能说明你已经接上了真模型

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

再强调一遍：

- `SHOP_AI_ENABLED=false` 时，项目是“能联调业务流程的本地假模型模式”
- 它适合开发，不适合拿来证明“项目已经接入真实 AI”

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

这里要特别注意一件事：

- 虽然这里接的是 DashScope 兼容接口
- 但当前项目读取的环境变量名字仍然是 `OPENAI_API_KEY`

也就是说，只要你沿用当前代码，不管你接 OpenAI 兼容服务还是 DashScope 兼容服务，密钥都要放在：

- `OPENAI_API_KEY`

真正决定“有没有启用真实模型 Bean”的开关是：

- `shop.ai.enabled`
- 它通过环境变量 `SHOP_AI_ENABLED` 注入

也就是说，判断当前进程是不是“真 AI 模式”，不要只看 `application.yml` 里写了模型名，更要看你启动进程时到底有没有把这些环境变量带进去。

这个模式下的特点：

- 聊天回复会真实调用远程模型
- embedding 会真实调用远程向量模型
- 如果当前向量数据源是 PostgreSQL，则会使用 pgvector 存储向量索引
- 控制台会输出模型请求日志和响应日志
- AI 客服的商品问答、订单问答、优惠活动问答、知识检索，都会走真实模型推理

注意两点：

1. `SHOP_AI_ENABLED` 默认值是 `true`
2. `OPENAI_API_KEY` 为空时，应用未必会在启动阶段就报错，但一旦真正调用模型，请求大概率会失败

所以本地开发时建议你明确指定模式，不要靠默认值猜。

如果你要排查“为什么我感觉像没接上真模型”，优先按这个顺序查：

1. `SHOP_AI_ENABLED` 是否真的是 `true`
2. `OPENAI_API_KEY` 是否在当前启动进程里可见
3. 你是不是用 IDEA 启动，但只在 PowerShell 里配了变量，没配到 IDEA 的 Run Configuration
4. 控制台有没有真实模型请求日志
5. 数据库、网络、Key 权限是否允许 DashScope 兼容接口正常返回

### 3.3 不要靠“能聊天”判断有没有接上真模型

这一点要单独强调。

现在项目里即使在本地联调模式下，AI 客服页面也可以正常回复、也可以走商品推荐、订单查询、草稿生成这些业务流程。

但这只能说明：

- 页面通了
- 后端接口通了
- 本地兜底逻辑在工作

它完全**不能单独证明**你现在跑的是远程真实模型。

项目已经补了一个专门的运行态检查接口：

- `GET /api/assistant/health`

这个接口会返回当前进程的真实运行态，例如：

- `mode`：`REMOTE_MODEL` 或 `LOCAL_FALLBACK`
- `provider`：当前模型提供方
- `apiKeyConfigured`：当前进程里有没有拿到 key
- `chatModelName` / `embeddingModelName`：当前真正生效的模型名
- `vectorStoreType`：`PGVECTOR` 或 `IN_MEMORY`
- `knowledgeDocumentCount` / `knowledgeChunkCount` / `indexedSegmentCount`

前台客户端和后台管理端页面里也已经有 AI 运行态面板，会直接显示当前到底是：

- 真实模型
- 本地联调
- pgvector 向量库
- 还是内存向量库

所以最可靠的判断方式不是“能不能聊”，而是：

1. 先看页面里的 AI 运行态面板
2. 再看 `/api/assistant/health`
3. 最后结合控制台里的模型请求日志一起确认

如果页面显示的是：

- `LOCAL_FALLBACK`
- `LOCAL_FALLBACK_CHAT`
- `LOCAL_FAKE_EMBEDDING_64D`
- `IN_MEMORY`

那就说明你现在跑的是本地联调模式，不是真正的远程模型。

如果页面显示的是：

- `REMOTE_MODEL`
- `qwen-plus` 或你自己配置的真实聊天模型
- `text-embedding-v4` 或你自己配置的真实 embedding 模型
- `PGVECTOR`

这时才说明当前进程真正接上了远程模型和持久化向量库。

### 3.4 你的 key 我知不知道

不知道。

这个项目不会把真实 key 写死在代码里，也不应该提交到 git。

当前代码只会从环境变量读取：

- `OPENAI_API_KEY`

你在 IDEA 里填的 Environment Variables，或者你在终端里临时设置的环境变量，只在你的本地进程里生效，不属于仓库内容。

再说直白一点：

- 我不知道你的 key
- 仓库里也不应该保存你的 key
- 真正运行时有没有拿到 key，要看“你启动的那个进程”的环境变量
- 最方便的判断方式就是看页面里的 AI 运行态面板，或者直接访问 `/api/assistant/health`

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

如果你当前接的是 DashScope 兼容接口，也还是填这里，不需要改变量名。

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

如果你只想先把平台业务流程跑通，推荐优先用“本地联调模式”。

如果你要验证：

- 真实模型回答质量
- 真实 embedding
- 真实 pgvector 检索

那就必须切到“真实 AI 模型模式”。

### 集成模式（后端同时托管前端）

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

这一组命令的真实含义是：

- 电商业务走当前后端真实逻辑
- AI 对话走远程模型
- embedding 走远程向量模型
- 如果向量数据源最终解析到 PostgreSQL，就会使用 pgvector
- 页面里的 AI 运行态面板会显示 `真实模型`

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

这一组命令的真实含义是：

- 电商业务仍然完整可跑
- AI 客服仍然可问商品、订单、售后、知识库问题
- 但模型回复和 embedding 是本地兜底
- 页面里的 AI 运行态面板会显示 `本地联调`

### 分离模式（Spring Boot API + 独立前端）

先启动后端：

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="agentdemo"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD=""
$env:SHOP_AI_ENABLED="false"
mvn spring-boot:run
```

再启动前端：

```powershell
cd frontend
npm install
npm run dev
```

默认情况下：

- 前端开发端口：`5173`
- 后端 API：`http://localhost:8080`
- Vite 会把 `/api/*` 自动代理到 Spring Boot
- 如果你没有显式配置 `VITE_BACKEND_TARGET`，当前版本会自动探测 `8080` 和 `8082`，优先连接能访问到的后端

如果你后端不是 `8080`，可以先在 `frontend/.env` 里配置：

```powershell
VITE_BACKEND_TARGET=http://localhost:8082
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

这里是最容易踩坑的地方：

- 你在 PowerShell 里执行过 `$env:OPENAI_API_KEY=...`
- 不代表 IDEA 启动的 Spring Boot 进程也能看到它

如果你是从 IDEA 点运行按钮启动，优先相信 IDEA Run Configuration 里的环境变量，而不是当前终端窗口。

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

集成模式：

- 客户端首页：`http://localhost:8080/`
- 客户端直达：`http://localhost:8080/client/index.html`
- 助手入口：`http://localhost:8080/assistant.html`
- 管理端：`http://localhost:8080/admin/index.html`
- 管理端快捷入口：`http://localhost:8080/admin`
- 客户端快捷入口：`http://localhost:8080/client`

分离模式（默认 Vite 端口）：

- 前端根入口：`http://localhost:5173/`（独立前端工作台，可查看 AI 运行态和入口导航）
- 客户端：`http://localhost:5173/client/index.html`
- 管理端：`http://localhost:5173/admin/index.html`

如果你改成了 `8082`，把端口替换成 `8082` 即可。

## 7. 默认账号

启动后会自动初始化演示数据：

- 客户端账号：`demo / demo123`
- 管理端账号：`admin / admin123`
- 客服账号：`support1 / support123`

对应初始化代码：

- `src/main/java/com/aishop/config/DataInitializer.java`

## 8. 当前业务能力

### 8.1 客户端

- 用户注册、登录、退出
- 个人资料维护
- 商品列表、分类、搜索、详情浏览、排序
- 商品场景化推荐、推荐理由、选品决策区
- 从商品卡片快速发起 AI 咨询、同类对比、下单草稿
- 购物车增删改
- 购物车优惠码输入、活动推荐、满减 / 折扣估算
- 结算下单并生成待支付订单
- 模拟支付，支持模拟支付、支付宝、微信支付、银行卡等支付方式标签
- 订单列表
- 订单原价、优惠金额、活动码展示
- 支付方式、支付流水、支付时间展示
- 订单履约时间线
- 物流节点追踪
- 发货前修改收货地址
- 用户侧取消订单
- 用户侧确认收货
- 已完成订单商品评价
- 用户侧申请退款
- 用户侧提交回寄物流
- AI 客服对话

### 8.2 管理端

- 仪表盘数据总览
- 商品新增 / 编辑
- 营销活动新增 / 编辑
- 固定金额立减、百分比折扣、门槛金额、到期时间、启停状态管理
- 商品评分和用户评价查看
- 待支付订单查看
- 后台补记支付并推进到待发货
- 订单状态推进
- 订单履约时间线
- 追加物流节点并同步到客户端
- 发货时填写物流公司和运单号
- 退款审核
- 发送退货指引
- 确认收到退货并退款
- 售后工作台（待审核、待回寄、待验收、已完成、已驳回）
- 售后工单按阶段统计、筛选、查看逆向物流和订单时间线
- 用户列表
- 知识库文档导入与检索
- 知识库原文查看、分块明细查看、索引状态查看
- 全量知识索引重建
- AI 运行态面板（真实模型 / 本地联调、向量库类型、索引数量）
- AI 会话列表
- 人工接管队列
- AI 会话认领
- AI 会话待处理 / 客户未读计数
- AI 会话首响时间、认领时间、结案时间
- AI 客服工作台筛选视图（我的待处理、待认领、待回复、首响超时、客户未读、已结案）
- 人工客服认领、分派、转交会话
- AI 会话详情内联查看用户资料、最近订单和关联下单草稿
- 人工客服回复并结案

### 8.3 AI 客服

当前 AI 客服已经能处理这些常见场景：

- 商品推荐
- 商品详情咨询、同类对比、评价口碑参考和下单草稿快捷引导
- 查询最近订单
- 查询具体订单状态、物流、地址、最新履约进度
- 读取最新物流节点并结合订单状态解释下一步
- 查询待支付订单和支付状态
- 直接代办模拟支付
- 查询退款 / 售后进度与回寄物流状态
- 生成下单草稿
- 确认草稿生成正式订单
- 取消草稿
- 直接代办取消订单
- 直接代办确认收货
- 直接代办申请退款
- 直接代办修改发货前订单地址
- 查询当前优惠活动、优惠码、满减门槛和预计优惠
- 查询知识库中的售后、物流、政策内容
- 在客户端展示知识命中来源卡片和下一步建议动作
- 用户主动要求“转人工”后进入人工接管流程
- 管理端可认领人工会话，并持续查看待处理数和客户未读状态
- 管理端人工回复后，消息会回流到客户端会话
- 人工结案后，用户再次发消息会自动回到 AI 在线状态

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
- 营销活动：`promotion_campaigns`
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

当前还补充了这些人工客服运营字段：

- `assigned_admin_id`
- `assigned_at`
- `first_support_reply_at`
- `resolved_at`
- `last_customer_message_at`
- `last_support_message_at`
- `support_unread_count`
- `customer_unread_count`

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
- `GET /api/products/{id}/reviews`
- `GET /api/products/search`
- `GET /api/categories`
- `GET /api/promotions`
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
- `PATCH /api/orders/{id}/pay`
- `PATCH /api/orders/{id}/cancel`
- `PATCH /api/orders/{id}/confirm-receipt`
- `PATCH /api/orders/{id}/refund`
- `PATCH /api/orders/{id}/shipping-address`
- `POST /api/orders/{orderId}/items/{itemId}/review`

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
- `GET /api/admin/promotions`
- `POST /api/admin/promotions`
- `PUT /api/admin/promotions/{id}`
- `GET /api/admin/orders`
- `PATCH /api/admin/orders/{id}/status`
- `PATCH /api/admin/orders/{id}/refund-review`
- `GET /api/admin/knowledge/documents`
- `GET /api/admin/knowledge/search`
- `POST /api/admin/knowledge/import`
- `GET /api/admin/users`
- `GET /api/admin/reviews`
- `GET /api/admin/assistant/sessions`
- `GET /api/admin/assistant/escalations`
- `GET /api/admin/assistant/sessions/{id}/messages`
- `POST /api/admin/assistant/sessions/{id}/claim`
- `POST /api/admin/assistant/sessions/{id}/reply`
- `GET /api/admin/assistant/drafts`

## 12. 开发提示

### 12.1 客户端和管理端同时调试

当前客户端和管理端都跑在同一个服务域名下，也仍然共用浏览器 cookie。

但当前版本已经把登录态拆成了两套 scope：

- 客户端：`/api/auth/*?scope=customer`
- 管理端：`/api/auth/*?scope=admin`

所以你现在可以在同一个浏览器里同时保持：

- 客户端登录 `demo`
- 管理端登录 `admin`

本地联调建议：

- 直接同浏览器双开客户端和管理端
- 或者一个普通窗口、一个无痕窗口做多账号测试

如果你使用独立前端工程：

- 开发时可以先访问 `http://localhost:5173/` 查看工作台和运行态
- 再进入 `http://localhost:5173/client/index.html`
- 或者直接进入 `http://localhost:5173/admin/index.html`
- `/api/*` 会由 Vite 代理到 Spring Boot
- 后端默认允许 `5173` / `4173` 端口跨域携带 cookie
- 当前 `frontend/` 工程会优先尝试你手动配置的 `VITE_BACKEND_TARGET`；如果没配，就自动探测本机 `8080` / `8082`

### 12.2 客户端商品推荐怎么理解

客户端商品目录里的“为你推荐 / 通勤轻便 / 专注办公 / 手机拍照 / 影音娱乐”是前端本地选品体验，不是每次都调用大模型。

它主要根据这些信息做本地打分：

- 当前搜索关键词
- 当前分类
- 用户偏好摘要
- 商品名称、描述、分类、价格、库存
- 当前选择的推荐场景

点击商品区里的“问 AI 适不适合我”“让 AI 比较同类商品”“生成下单草稿”时，才会把问题写入 AI 客服输入框并调用 `/api/assistant/chat`。

另外，客户端加载商品时会优先要求 `/api/products` 成功；如果 `/api/categories` 临时失败，页面会自动降级成无分类浏览，并显示提示。

### 12.3 8080 端口占用

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
- 前端跨域与分离开发支持：`src/main/java/com/aishop/config/FrontendCorsConfig.java`
- AI 客服主逻辑：`src/main/java/com/aishop/service/AssistantService.java`
- 优惠活动与优惠码：`src/main/java/com/aishop/service/PromotionService.java`
- 商品评价与口碑：`src/main/java/com/aishop/service/ProductReviewService.java`
- 管理端客服接管：`src/main/java/com/aishop/service/AdminService.java`
- RAG 导入与检索：`src/main/java/com/aishop/service/KnowledgeService.java`
- 向量索引同步：`src/main/java/com/aishop/service/KnowledgeIndexSynchronizer.java`
- pgvector 存储：`src/main/java/com/aishop/service/PgVectorEmbeddingStoreFacade.java`
- 自动补旧表结构：`src/main/java/com/aishop/config/LegacyPostgresSchemaMigrator.java`
- 客户端前端逻辑：`src/main/resources/static/scripts/client-app.js`
- 管理端前端逻辑：`src/main/resources/static/scripts/admin-app.js`
- 前后台共享样式：`src/main/resources/static/styles/portal.css`
- 独立前端工程：`frontend/`

---

如果你现在要快速判断“我跑起来的是不是真 AI”，最简单的方法只有两个：

1. 看你有没有设置 `SHOP_AI_ENABLED=true` 和 `OPENAI_API_KEY`
2. 看页面里的 AI 运行态面板或 `/api/assistant/health`
3. 再看控制台里有没有真实模型请求日志

至少前两条都对得上，才说明当前进程大概率真的在调远程模型。
