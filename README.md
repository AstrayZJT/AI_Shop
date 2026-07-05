# AI Shop

一个基于 Spring Boot 的电商助手示例项目，包含用户登录、商品浏览、会话聊天、订单草稿、知识库导入和 RAG 检索。

当前版本已经接入真实 RAG：

- 原文存数据库
- 文本自动切块
- 向量可写入 pgvector
- 检索时优先走向量召回，再做关键词兜底

## 技术栈

- Java 21
- Spring Boot 3.5.x
- Spring Data JPA
- PostgreSQL
- pgvector
- LangChain4j
- LangGraph4j
- Spring Session JDBC
- Thymeleaf + 原生 HTML/CSS/JS

## 先看这个：项目有两种 AI 运行模式

这部分最重要。

### 1. 本地演示模式

本地演示模式不是“真正的大模型”，只是为了让你在没有远程 Key 的情况下，先把页面、登录、会话、订单、知识库流程跑通。

你必须显式设置：

```powershell
$env:SHOP_AI_ENABLED="false"
```

这一模式下：

- 聊天模型使用本地兜底实现，代码在 [LocalChatModelConfig.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/config/LocalChatModelConfig.java:1)
- 它不会真正调用大模型接口
- 返回内容本质上是一个本地拼接出来的兜底答复，只适合联调
- 向量模型也使用本地 64 维简化 embedding，代码在 [LocalEmbeddingConfig.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/config/LocalEmbeddingConfig.java:1)
- 这个 embedding 也不是真实语义向量，只适合本地开发验证流程

适用场景：

- 前端页面联调
- 登录/注册/会话流程联调
- 订单草稿功能联调
- 不接真实模型时的本地演示

不适用场景：

- 想看真实 AI 回答效果
- 想验证真实语义检索质量
- 想验证真实模型对知识库的理解能力

一句话说透：

本地模式能跑流程，但它不是“真 AI”。

### 2. 真实 AI 模型模式

真实模式才会真正调用大模型接口。

当前项目默认走的是 OpenAI 兼容接口，配置在 [AiModelConfig.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/config/AiModelConfig.java:1) 和 [application.yml](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/resources/application.yml:1)。

当前默认配置是：

- `base-url`: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- 聊天模型：`qwen-plus`
- 向量模型：`text-embedding-v4`

要启用真实模型，你至少要提供：

```powershell
$env:SHOP_AI_ENABLED="true"
$env:OPENAI_API_KEY="你的 Key"
```

注意两点：

- 当前代码里 `SHOP_AI_ENABLED` 默认值就是 `true`
- 也就是说，如果你什么都不配，程序会默认尝试走真实模型

因此：

- 想用真实模型：设置 `OPENAI_API_KEY`
- 不想用真实模型：明确设置 `SHOP_AI_ENABLED=false`

这两个变量不要搞反。

另外，当前配置里：

- `shop.ai.log-requests=true`
- `shop.ai.log-responses=true`

所以在真实模型模式下，控制台会打印模型请求和响应日志。这是正常现象。

## 数据库与 RAG

当前默认数据源是 PostgreSQL，不再是 README 旧版本里写的 “H2 本地默认”。

默认数据库连接配置来自：

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

默认值等价于：

- 主机：`localhost`
- 端口：`5432`
- 数据库：`agentdemo`
- 用户名：`postgres`
- 密码：空

### RAG 数据存在哪里

原文、切块和向量分别存三处：

- 原始全文：`knowledge_documents.content`
- 切块文本：`knowledge_chunks.chunk_text`
- 向量缓存 JSON：`knowledge_chunks.embedding_json`
- pgvector 向量表：默认 `knowledge_embeddings`

相关代码：

- [KnowledgeDocument.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/domain/KnowledgeDocument.java:1)
- [KnowledgeChunk.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/domain/KnowledgeChunk.java:1)
- [KnowledgeService.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/service/KnowledgeService.java:1)
- [KnowledgeIndexSynchronizer.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/service/KnowledgeIndexSynchronizer.java:1)

### pgvector 相关环境变量

如果你希望向量库和业务库分开，可以单独配置：

- `RAG_PGVECTOR_HOST`
- `RAG_PGVECTOR_PORT`
- `RAG_PGVECTOR_DATABASE`
- `RAG_PGVECTOR_USERNAME`
- `RAG_PGVECTOR_PASSWORD`
- `RAG_PGVECTOR_TABLE`

如果不单独配，默认会回退到 `DB_*` 这套配置。

### 启动时会做什么

程序启动后会自动尝试同步知识库索引：

- 读取 `knowledge_chunks`
- 恢复或重建 embedding
- 重新写入向量索引

如果你用的是 PostgreSQL + pgvector，请确保数据库侧已经具备 pgvector 扩展能力。

## 环境变量一览

### 必要数据库变量

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="agentdemo"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD=""
```

### 本地演示模式

```powershell
$env:SHOP_AI_ENABLED="false"
```

### 真实模型模式

```powershell
$env:SHOP_AI_ENABLED="true"
$env:OPENAI_API_KEY="你的 Key"
```

### 可选 RAG 变量

```powershell
$env:RAG_PGVECTOR_HOST="localhost"
$env:RAG_PGVECTOR_PORT="5432"
$env:RAG_PGVECTOR_DATABASE="agentdemo"
$env:RAG_PGVECTOR_USERNAME="postgres"
$env:RAG_PGVECTOR_PASSWORD=""
$env:RAG_PGVECTOR_TABLE="knowledge_embeddings"
```

### 其他可选变量

```powershell
$env:AGENT_SEARCH_MAX_RESULTS="5"
```

## 启动方式

### PowerShell

真实模型模式示例：

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

本地演示模式示例：

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="agentdemo"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD=""
$env:SHOP_AI_ENABLED="false"
mvn spring-boot:run
```

### IDEA

在 `Run/Debug Configurations` 里配置同名环境变量即可，变量名和上面完全一致。

## 默认访问地址

- 首页：`http://localhost:8080/`
- 助手页：`http://localhost:8080/assistant.html`

## 默认账号

启动时会自动初始化演示数据：

- 用户名：`demo`
- 密码：`demo123`

初始化逻辑在 [DataInitializer.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/config/DataInitializer.java:1)。

## 主要功能

- 用户注册、登录、退出、查询当前用户
- 商品列表、详情、搜索
- 智能助手对话
- 会话管理
- 订单草稿生成与确认
- 知识库导入与检索

## 主要接口

### 认证

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

### 商品

- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/products/search`

### 智能助手

- `POST /api/assistant/chat`
- `GET /api/assistant/sessions`
- `POST /api/assistant/sessions`
- `GET /api/assistant/sessions/{id}`
- `GET /api/assistant/sessions/{id}/messages`

### 订单

- `GET /api/orders`
- `GET /api/orders/{id}`
- `POST /api/orders/draft`
- `POST /api/orders/confirm`

### 知识库

- `POST /api/knowledge/import`
- `GET /api/knowledge/search`

## 补充说明

- 当前助手会结合商品、订单和知识库上下文组织回答，逻辑在 [AssistantService.java](C:/Users/86187/Desktop/老桌面/学习笔记/Java学习/大三暑假/ai_shop/src/main/java/com/aishop/service/AssistantService.java:1)
- 订单流程是“先生成草稿，再确认创建正式订单”
- 如果你看到控制台里有模型请求日志，说明现在走的是“真实模型模式”
- 如果你把 `SHOP_AI_ENABLED=false`，那看到的回答效果更像联调兜底，不代表真实 AI 能力

## 构建

```powershell
mvn clean package
```
