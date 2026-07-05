# AI Shop

一个电商 + 智能助手的 Spring Boot MVP。

## 技术栈

- Java 21
- Spring Boot 3.5.x
- Spring Data JPA
- H2（本地开发默认）
- PostgreSQL（生产推荐）
- LangChain4j
- LangGraph4j
- Session 登录
- Thymeleaf + 原生 HTML/CSS/JS

## 功能

- 用户注册、登录、退出、当前用户
- 商品列表、搜索、详情
- 智能助手对话
- 会话管理
- 订单草稿生成与确认
- 知识库导入与检索
- 本地开发无需远程 AI Key 也能运行

## 运行

```bash
mvn spring-boot:run
```

默认地址：

- 首页：`http://localhost:8080/`
- 助手页：`http://localhost:8080/assistant.html`

## 默认账号

启动时会自动初始化演示数据。

- 用户名：`demo`
- 密码：`demo123`

## AI 配置

默认使用本地兜底模型，不需要远程 Key。

如果要启用 OpenAI 兼容模型：

```bash
set SHOP_AI_ENABLED=true
set SHOP_AI_API_KEY=your_key_here
```

可选环境变量：

- `SHOP_AI_ENABLED`
- `SHOP_AI_API_KEY`

## 接口

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

## 说明

- 开发环境默认使用 H2。
- 智能助手支持会话记录、RAG 检索和订单草稿。
- 订单流程是“先生成草稿，再确认创建正式订单”。

## 构建

```bash
mvn clean package
```
