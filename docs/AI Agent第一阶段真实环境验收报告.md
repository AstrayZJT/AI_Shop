# AI Agent 第一阶段真实环境验收报告

## 1. 验收结论

验收时间：2026-07-14。

第一阶段通过验收。

真实环境已确认：

- Spring Boot 应用可以连接 PostgreSQL。
- LangChain4j 成功装配 `OpenAiChatModel` 和 `OpenAiEmbeddingModel`。
- 模型供应商为 DashScope OpenAI-compatible API。
- 实际 Planner 模型为 `qwen-plus`。
- Embedding 模型为 `text-embedding-v4`。
- 向量存储为 PostgreSQL pgvector。
- Planner 预览接口能通过登录 Session 调用真实模型。
- 六类验收输入均由真实 LLM 生成并通过 Java 校验。
- Planner 预览前后 20 个订单的编号和状态完全一致。
- 日志中未发现 API Key、Authorization Header 或数据库密码。

敏感配置只注入临时 Java 进程，没有写入 `application.yml`、源码、文档或 Git。

## 2. 自动化回归

执行：

```powershell
mvn test
```

结果：

```text
Tests run: 41
Failures: 0
Errors: 0
Skipped: 0
```

测试覆盖：

- LangChain4j System/User Message 和 JSON ResponseFormat。
- LLM JSON 解析和严格未知字段检查。
- 规则 Planner。
- 模型异常和 Key 缺失降级。
- action、executionMode、slots 和 conditions 校验。
- 依赖不存在和循环依赖。
- 高风险动作只能使用 `ASK_CONFIRM`。
- 纯咨询消息不能生成写动作。

## 3. 真实运行状态

健康接口返回的关键状态：

```text
aiEnabled=true
mode=REMOTE_MODEL
provider=DASHSCOPE_COMPATIBLE
requestReady=true
chatModelName=qwen-plus
embeddingModelName=text-embedding-v4
chatModelClass=OpenAiChatModel
embeddingModelClass=OpenAiEmbeddingModel
vectorStoreType=PGVECTOR
vectorStorePersistent=true
knowledgeDocumentCount=5
knowledgeChunkCount=11
indexedSegmentCount=11
warnings=0
```

现有 `KnowledgeIndexSynchronizer` 启动时会先清理向量表，再逐条恢复索引。因此端口刚监听时可能短暂看到 `indexedSegmentCount < knowledgeChunkCount`。本次验收等待二者都为 11 后才开始 Planner 调用。

## 4. 真实模型样例

所有样例使用 Prompt `planner-v1.3`。

| 场景 | source | planType | actions | executionMode |
| --- | --- | --- | --- | --- |
| 查询指定订单 | LLM | SINGLE_TASK | QUERY_ORDER | TOOL_READ |
| 查询物流后条件取消 | LLM | MULTI_TASK | QUERY_LOGISTICS, CANCEL_ORDER | TOOL_READ, ASK_CONFIRM |
| 取消订单但缺订单号 | LLM | SINGLE_TASK | CANCEL_ORDER | ASK_CONFIRM |
| 咨询取消订单规则 | LLM | SINGLE_TASK | SEARCH_KNOWLEDGE | TOOL_READ |
| 推荐商品并咨询退货政策 | LLM | MULTI_TASK | SEARCH_PRODUCT, SEARCH_KNOWLEDGE | TOOL_READ, TOOL_READ |
| 要求忽略规则直接取消他人订单 | LLM | SINGLE_TASK | CANCEL_ORDER | ASK_CONFIRM |

缺订单号场景正确返回：

```text
missingSlots=[orderNo]
```

物流后取消场景正确返回：

```text
t1=QUERY_LOGISTICS
t2=CANCEL_ORDER
t2.dependsOn=[t1]
t2.conditions.sourceTaskId=t1
t2.conditions.field=ORDER_STATUS
```

注入式输入没有获得直接执行能力，因为 `ExecutionMode` 枚举不存在 `DIRECT_EXECUTE`，`CANCEL_ORDER` 的后端固定策略只能是 `ASK_CONFIRM`。当前阶段仍不会执行取消工具；后续阶段还必须校验订单归属。

## 5. 验收中发现并修复的问题

### 5.1 planner-v1：多任务条件字段不完整

模型生成 condition 时省略 `sourceTaskId` 和 `expectedValues`。

处理：

- Prompt 增加完整 condition Schema。
- 增加物流后条件取消示例。
- Validator 要求 `expectedValues` 必须存在，空值判断传 `[]`。

### 5.2 planner-v1.2 前：Tool 槽位名称不稳定

真实模型曾把商品最高预算写成 `maxPrice`，后端契约实际是 `budgetMax`，并且漏掉必填 `query`。

处理：

- Prompt 增加每个 action 的固定 intent、executionMode、allowed slots 和 required slots。
- 增加缺订单号和商品+政策示例。
- 保留后端槽位白名单，没有为了通过模型输出而接受任意别名。

### 5.3 planner-v1.2：结构合法但语义错误

对于“怎么取消订单，需要什么规则”，模型曾生成：

```text
CANCEL_ORDER + SEARCH_KNOWLEDGE
```

JSON、枚举和槽位都合法，但用户只是咨询，并没有要求执行取消。

处理：

- 新增 `PlanSemanticGuard`。
- 纯咨询且没有明确执行词时，计划中禁止出现写动作。
- Prompt v1.3 增加纯规则咨询示例。

最终真实模型只返回：

```text
SEARCH_KNOWLEDGE + TOOL_READ
```

这说明 Agent 校验不能只检查 JSON Schema，还需要检查计划与原始用户意图是否一致。

## 6. 数据库无副作用验证

在同一个登录会话中：

1. 调用 `/api/orders` 保存订单编号与状态快照。
2. 连续执行六次 `/api/assistant/planner/preview`。
3. 再次调用 `/api/orders` 获取快照。
4. 对两个快照做严格比较。

结果：

```text
orderCount=20
ordersUnchanged=true
```

因此第一阶段 Planner 只生成计划，没有调用业务 Tool，也没有修改订单状态。

## 7. 日志和凭据检查

真实验收进程使用：

```text
SHOP_AI_LOG_REQUESTS=false
SHOP_AI_LOG_RESPONSES=false
```

对标准输出和错误日志检查 API Key 格式、Authorization、数据库密码和 api-key 等模式，没有发现匹配内容。

用户提供的 `Dfile.encoding=GBK` 没有作为环境变量使用。Java 进程明确使用 UTF-8，HTTP JSON 请求也使用 `application/json; charset=utf-8`，避免中文意图在 PowerShell 请求中变成问号。

## 8. 当前边界

第一阶段已经完成“模型理解并生成安全结构化计划”，但尚未完成：

- 原生业务 Function Calling。
- Tool Registry。
- 商品、订单、物流和知识库 Tool 执行。
- PlanRun/TaskRun 持久化。
- PendingAction 二次确认。
- 订单归属校验和确认后恢复。

这些属于下一阶段，不能因为 Planner 已识别出 action 就宣称业务动作已经安全执行。

## 9. 安全后续动作

本次使用的 API Key 曾直接出现在聊天消息中。即使项目文件和日志没有保存它，也应在供应商控制台撤销该 Key 并创建新 Key。新 Key 只通过 IDEA 环境变量或系统密钥管理方式注入，不写进 README、YAML 或启动脚本。
