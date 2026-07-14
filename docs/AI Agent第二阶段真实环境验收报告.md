# AI Agent 第二阶段真实环境验收报告

## 1. 验收结论

验收时间：2026-07-14。

第二阶段通过验收。

真实环境已确认：

- Spring Boot 成功连接 PostgreSQL 和 pgvector。
- LangChain4j 成功连接 DashScope OpenAI-compatible API。
- 聊天模型为 `qwen-plus`，Embedding 模型为 `text-embedding-v4`。
- Planner 驱动链路可以执行商品和知识库多工具任务。
- LangChain4j 原生 Function Calling 可以生成并执行只读工具请求。
- 取消订单只返回 `PREPARED`，不会修改订单状态。
- 订单查询和物流查询继续使用当前登录用户完成归属校验。
- 日志中未发现 API Key、Authorization Header 或数据库密码。

敏感配置只注入临时进程，没有写入源码、配置文件、文档或 Git。

## 2. 自动化回归

执行：

```powershell
mvn test
```

结果：

```text
Tests run: 66
Failures: 0
Errors: 0
Skipped: 0
```

共 14 个测试类，覆盖第一阶段 Planner 和第二阶段 Tool Calling。

## 3. 真实运行状态

健康检查关键结果：

```text
chatModel=qwen-plus
embeddingModel=text-embedding-v4
vectorStore=PGVECTOR
knowledgeChunks=11
indexedSegments=11
warnings=0
```

验收等待知识库 11 个分段全部建立向量索引后再调用工具，避免启动期索引同步影响结果。

## 4. Planner 驱动工具验收

### 4.1 商品推荐与知识库查询

模型将复合需求拆分为：

```text
SEARCH_PRODUCT
SEARCH_KNOWLEDGE
```

执行结果：

```text
statuses=SUCCEEDED,SUCCEEDED
productCount=1
knowledgeCount=5
```

这证明 Planner 的多任务结构可以进入 Orchestrator，并路由到两个不同业务 Tool。

### 4.2 物流查询与取消准备

计划执行结果：

```text
QUERY_LOGISTICS -> SUCCEEDED
prepare_cancel_order -> PREPARED
requiresConfirmation=true
ordersUnchanged=true
```

取消任务完成了订单归属、状态和参数校验，但没有调用写业务方法。

### 4.3 缺少订单号

用户要求取消订单但没有提供订单号：

```text
action=CANCEL_ORDER
status=NEEDS_INPUT
missingSlots=orderNo
```

系统没有猜测订单号，也没有尝试执行。

## 5. 原生 Function Calling 验收

通过 `POST /api/assistant/tools/function-call-preview` 调用真实模型，模型返回 LangChain4j `ToolExecutionRequest`。

验收结果：

| 用户需求 | 模型工具请求 | Java 执行结果 |
| --- | --- | --- |
| 查询订单 | `query_order` | `SUCCEEDED` |
| 取消可取消订单 | `prepare_cancel_order` | `PREPARED` |
| 推荐商品并查售后规则 | `search_products`, `search_knowledge` | `SUCCEEDED`, `SUCCEEDED` |

工具名由 Registry 白名单匹配；参数 JSON 由 Jackson 严格解析后再次经过工具校验。

## 6. 高风险和越权验收

### 6.1 已发货订单

对 `SHIPPED` 订单调用取消准备工具：

```text
tool=prepare_cancel_order
status=FAILED
reason=订单当前状态不支持取消: SHIPPED
```

### 6.2 用户归属

订单工具只接收 `orderNo`，不接收 `userId`。当前登录用户由后端 `ToolContext` 注入，底层调用：

```java
orderService.findByOrderNo(context.user(), orderNo)
```

越权或不存在订单由现有 `OrderService` 拒绝，Orchestrator 将异常转换为 `FAILED`，不会泄露订单数据。

### 6.3 写操作隔离

验收前后核对订单状态：

```text
ORD-D7D55B21=PROCESSING
ORD-A326676D=SHIPPED
ordersUnchanged=true
```

代码扫描确认第二阶段没有调用 `OrderService.cancelOrder()`，`PrepareCancelOrderTool.execute()` 也会主动抛出安全异常。

## 7. 验收中发现并修复的问题

真实模型曾将“耳机”作为 `category` 参数，而业务数据库中的一级分类是“数码”。如果把模型给出的 category 当作硬过滤条件，会错误返回空结果。

处理方式：

- Tool 描述明确“不确定平台真实分类时省略 category”。
- `query` 保存用户的具体品类和需求。
- 后端只有在候选商品中存在该真实分类时才启用 category 过滤。
- 预算和库存仍使用确定性硬过滤。

这说明 Tool Schema 只能约束参数形状，不能保证模型理解业务枚举；业务语义仍需 Java 代码兜底。

## 8. 日志和凭据检查

真实验收关闭模型请求和响应明文日志。扫描运行日志和 Git 工作区，没有发现：

- API Key 格式。
- `Authorization` Header。
- 数据库密码。
- 将敏感配置写入 YAML、脚本或文档的情况。

临时验收服务运行在 8099，验收完成后已停止并释放端口。

## 9. 当前边界

本阶段尚未实现：

- 工具结果回传模型并生成最终自然语言答案。
- PendingAction 数据库持久化和确认接口。
- 确认后的重新鉴权、状态复检、幂等和真实取消。
- 正式 AI 客服主链路接入。

当前结论应准确表述为：已经实现真实 Function Calling 和受控 Tool 执行基础设施，高风险动作只完成无副作用准备。

## 10. 安全后续动作

用于验收的 API Key 曾出现在聊天消息中，应在供应商控制台撤销并重新生成。新 Key 仅通过 IDEA 环境变量或系统密钥管理注入。
