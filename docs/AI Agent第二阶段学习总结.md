# AI Agent 第二阶段学习总结：Tool Calling 与工具注册表

## 1. 本阶段解决的问题

第一阶段已经能把用户自然语言转换为经过 Java 校验的 `AssistantPlan`。第二阶段继续解决：

> 计划中的 action 如何映射为真实 Java 业务能力，以及如何保证模型只能建议调用工具，不能绕过后端策略直接修改业务数据。

本阶段实现了：

- 统一 Tool 抽象和 Tool Registry。
- 商品搜索、订单查询、物流查询、知识库查询四个只读工具。
- 取消订单的 `prepare_cancel_order` 准备工具。
- 基于 `dependsOn` 的计划任务排序与执行。
- LangChain4j 原生 `ToolSpecification`、`ToolExecutionRequest` 实验链路。
- 未知工具、非法参数、缺少参数、依赖失败和业务异常的结构化结果。
- 66 条自动化测试和真实模型、真实 PostgreSQL、真实 pgvector 验收。

本阶段没有修改订单状态。

## 2. 两条可学习的 Tool Calling 链路

项目同时保留两条链路，是为了分别学习“先规划再执行”和“模型原生选择工具”。两条链路最终都进入同一个 Java Orchestrator 和 Tool Registry。

### 2.1 Planner 驱动链路

```text
POST /api/assistant/tools/plan-preview
-> AssistantToolController
-> PlannerFacade
-> LLM Planner 或 Rule Planner
-> PlanValidator / PlanSemanticGuard
-> AssistantPlan(tasks[])
-> AssistantToolOrchestrator
-> 按 dependsOn 拓扑排序
-> AssistantToolRegistry 根据 action 查找工具
-> tool.prepare() 校验和规范化参数
-> 只读工具 tool.execute()
-> 返回 ToolPlanExecutionResult
```

这条链路适合多任务场景。例如：

```text
推荐 500 元以内的耳机，同时查询退货规则
-> t1 SEARCH_PRODUCT
-> t2 SEARCH_KNOWLEDGE
-> search_products + search_knowledge
```

Planner 负责把需求拆成任务和依赖，Orchestrator 负责按确定性规则执行。

### 2.2 LangChain4j 原生 Function Calling 链路

```text
POST /api/assistant/tools/function-call-preview
-> NativeFunctionCallingService
-> AssistantToolRegistry.specifications()
-> LangChain4jToolCallingModelGateway
-> ChatModel.chat(ChatRequest)
-> ToolChoice.AUTO
-> AIMessage.toolExecutionRequests()
-> Java 解析 ToolExecutionRequest.name/arguments
-> AssistantToolOrchestrator.executeNativeCall()
-> Registry 白名单查找
-> prepare + 风险策略 + execute
-> 返回 FunctionCallingPreviewResult
```

核心代码位于：

- `src/main/java/com/aishop/assistant/function/LangChain4jToolCallingModelGateway.java`
- `src/main/java/com/aishop/assistant/function/NativeFunctionCallingService.java`

请求中真正使用了 LangChain4j Tool Calling API：

```java
ChatRequest request = ChatRequest.builder()
        .messages(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(message))
        .toolSpecifications(tools)
        .toolChoice(ToolChoice.AUTO)
        .temperature(0.0)
        .build();

var response = chatModel.chat(request);
var toolRequests = response.aiMessage().toolExecutionRequests();
```

模型只能返回“建议调用哪个工具和哪些参数”。真正执行仍由 Java 代码决定。

## 3. Tool 抽象

统一接口位于 `src/main/java/com/aishop/assistant/tool/AssistantTool.java`：

```java
public interface AssistantTool {
    ToolPolicy policy();
    ToolSpecification specification();
    PreparedToolCall prepare(ToolContext context, Map<String, Object> arguments);
    ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call);
}
```

四个方法分别回答：

| 方法 | 责任 |
| --- | --- |
| `policy()` | Java 后端声明工具名、action、风险等级和能否自动执行 |
| `specification()` | 给模型看的名称、描述和 JSON 参数 Schema |
| `prepare()` | 不信任模型参数，执行白名单、类型、长度、归属和状态校验 |
| `execute()` | 调用确定性的业务 Service，并返回结构化数据 |

`prepare()` 和 `execute()` 分开是关键设计。对只读工具，两步连续执行；对高风险工具，只运行 `prepare()`，返回预览并停止。

## 4. ToolPolicy 是后端安全边界

`ToolPolicy` 包含：

```java
public record ToolPolicy(
        String name,
        String description,
        AssistantAction action,
        ToolRiskLevel riskLevel,
        boolean autoExecutable
) {}
```

当前风险等级：

| 风险等级 | 含义 | 自动执行 |
| --- | --- | --- |
| `READ_ONLY` | 只读取业务数据 | 可以 |
| `PREPARE_ONLY` | 只准备高风险动作 | 不可以 |

构造器强制规定：只有 `READ_ONLY` 才能设置 `autoExecutable=true`。这条规则写在 Java 类型中，不依赖 Prompt，也不采用模型自报的 confidence。

## 5. ToolSpecification 与严格参数 Schema

每个工具都提供 LangChain4j `ToolSpecification`。例如商品搜索：

```java
ToolSpecification.builder()
        .name("search_products")
        .description("按关键词、分类和预算搜索在售商品。只读取商品信息，不创建订单。")
        .parameters(JsonObjectSchema.builder()
                .addStringProperty("query", "商品关键词或用户需求")
                .addStringProperty("category", "可选平台一级分类")
                .addNumberProperty("budgetMin", "可选最低预算")
                .addNumberProperty("budgetMax", "可选最高预算")
                .required("query")
                .additionalProperties(false)
                .build())
        .strict(true)
        .build();
```

Schema 帮助模型生成正确参数，但不能替代后端校验。`ToolArguments` 仍会检查：

- 是否出现未知字段。
- 必填字符串是否为空。
- 字符串是否超过长度限制。
- 数字能否转换为 `BigDecimal`。
- 最低预算是否大于最高预算。

原因是模型输出、客户端请求和历史上下文都属于不可信输入。

## 6. AssistantToolRegistry

代码位置：`src/main/java/com/aishop/assistant/tool/AssistantToolRegistry.java`。

Spring 会把所有 `AssistantTool` Bean 注入 Registry。Registry 在启动时建立两张不可变映射：

```text
AssistantAction -> AssistantTool
tool name       -> AssistantTool
```

action 映射服务于 Planner 链路，name 映射服务于原生 Function Calling 链路。注册时发现重复 action 或重复 name 会直接启动失败，避免同一动作被不确定地路由到两个实现。

Registry 还统一向模型导出 `List<ToolSpecification>`。模型能看到哪些工具，由后端注册表决定，而不是由模型自由发明。

## 7. 五个工具如何实现

### 7.1 SearchProductTool

对应 action：`SEARCH_PRODUCT`，风险等级：`READ_ONLY`。

处理过程：

1. 校验 `query/category/budgetMin/budgetMax` 参数。
2. 调用现有 `ProductService.search(query)`。
3. 过滤无库存商品和预算区间。
4. `category` 只有匹配平台真实分类时才作为硬过滤条件。
5. 最多返回 5 个结构化 `ProductItem`。

将分类作为“软提示”是实际验收中发现的问题：模型可能把“耳机”放进 category，但数据库一级分类是“数码”。若直接硬过滤，模型参数看似合理，结果却为空。

### 7.2 QueryOrderTool

对应 action：`QUERY_ORDER`，风险等级：`READ_ONLY`。

工具调用：

```java
orderService.findByOrderNo(context.user(), orderNo)
```

当前登录用户来自 `ToolContext`，而不是模型参数。订单归属校验复用现有 `OrderService`，模型不能传入 userId 来扩大查询范围。

### 7.3 QueryLogisticsTool

对应 action：`QUERY_LOGISTICS`，风险等级：`READ_ONLY`。

它先通过当前用户和订单号查询订单，再生成结构化物流概要和时间线。因此不存在“只知道其他人的订单号就能查物流”的独立旁路。

### 7.4 SearchKnowledgeTool

对应 action：`SEARCH_KNOWLEDGE`，风险等级：`READ_ONLY`。

工具复用现有 `KnowledgeService.search(query, maxResults)`，返回命中的标题、原文片段、来源和分数。它只负责 Retrieval，不负责让模型自由扩写最终答案。

### 7.5 PrepareCancelOrderTool

对应 action：`CANCEL_ORDER`，风险等级：`PREPARE_ONLY`，`autoExecutable=false`。

`prepare()` 会：

1. 校验只能接收 `orderNo` 和可选 `note`。
2. 使用当前登录用户查询订单，完成归属校验。
3. 检查订单状态只能是 `PENDING_PAYMENT`、`CONFIRMED` 或 `PROCESSING`。
4. 返回订单号、当前状态、预计影响和 `requiresConfirmation=true`。

`execute()` 永远抛出 `SecurityException`：

```java
throw new SecurityException("prepare_cancel_order 是 PREPARE_ONLY 工具，禁止自动执行取消订单");
```

本阶段没有任何新代码调用 `OrderService.cancelOrder()`。后续必须实现 PendingAction 持久化、用户二次确认、确认时重新鉴权和重新校验状态，才能真正取消。

## 8. Orchestrator 如何执行多任务

代码位置：`src/main/java/com/aishop/assistant/orchestration/AssistantToolOrchestrator.java`。

执行步骤：

1. 为本次请求创建包含当前用户和 traceId 的 `ToolContext`。
2. 根据 `dependsOn` 使用 Kahn 算法拓扑排序。
3. 依赖失败时返回 `SKIPPED_DEPENDENCY`。
4. Planner 已声明缺少槽位时返回 `NEEDS_INPUT`。
5. Registry 没有对应工具时返回 `NOT_SUPPORTED`。
6. 调用工具 `prepare()`。
7. 非自动执行工具返回 `PREPARED`，不调用 `execute()`。
8. 自动执行前再次检查风险等级必须为 `READ_ONLY`。
9. 调用 `execute()`，成功返回 `SUCCEEDED`，异常返回 `FAILED`。

结构化状态包括：

```text
SUCCEEDED
PREPARED
NEEDS_INPUT
NOT_SUPPORTED
FAILED
SKIPPED_DEPENDENCY
```

这比用异常或自然语言表示所有情况更适合后续状态机、前端展示和评测。

## 9. 为什么没有让 LangChain4j 自动执行 Java 方法

LangChain4j 支持把带 `@Tool` 的对象交给 AI Service 自动调用，但本项目没有把 `OrderService.cancelOrder()` 直接暴露给模型。原因是：

- 模型可能误识别意图或生成错误参数。
- Tool Calling 成功只代表协议成功，不代表用户授权成功。
- 写操作需要登录用户、资源归属、当前状态、有效期、幂等键和二次确认。
- Prompt 中写“不要误操作”不能代替后端安全控制。

因此这里使用 LangChain4j 生成 `ToolExecutionRequest`，再由自研 Registry 和 Orchestrator 执行。这保留了 Function Calling 的智能选择能力，也保留了 Java 后端对业务副作用的最终控制权。

## 10. 调试接口

三个接口都要求登录：

| 接口 | 用途 | 是否调用模型 |
| --- | --- | --- |
| `GET /api/assistant/tools` | 查看后端注册的工具策略 | 否 |
| `POST /api/assistant/tools/plan-preview` | Planner 生成计划后执行只读/准备工具 | 视配置而定 |
| `POST /api/assistant/tools/function-call-preview` | 真实模型原生选择工具实验 | 是 |

`plan-preview` 请求沿用 Planner 的 message、conversationSummary 和 recentMessages。`function-call-preview` 当前只接收 message。

这些是学习和验收接口，尚未接入正式 `POST /api/assistant/chat` 回答链路。

## 11. 自动化测试

当前完整测试套件共 66 条，0 失败、0 错误、0 跳过。第二阶段新增测试覆盖：

- Registry 重复 action/name 拒绝。
- 非只读工具不能配置自动执行。
- 商品预算、分类软提示和库存过滤。
- 订单、物流和知识库查询。
- 取消订单只准备、不执行。
- 已发货订单不能准备取消。
- 订单归属异常能够传递为失败结果。
- 缺槽位、未知工具、非法 JSON 参数。
- 多任务依赖排序和依赖失败跳过。
- LangChain4j `ToolSpecification` 和 `ToolExecutionRequest` 协议。

执行命令：

```powershell
mvn test
```

## 12. 当前边界

第二阶段已经完成“模型选择工具，Java 安全执行只读能力”，但还没有完成：

- Tool 结果送回模型后的最终客服答案合成。
- 正式 `/api/assistant/chat` 主链路接入。
- PlanRun、TaskRun 和 ToolCall 持久化。
- PendingAction 二次确认和跨请求恢复。
- 真实取消订单执行。
- 条件表达式的完整运行时求值。

因此简历当前可以写“实现 LangChain4j Function Calling、Tool Registry、工具风险分级和高风险动作 prepare 机制”，不能写“已实现完整 Human-in-the-loop 状态恢复”。

## 13. 推荐源码阅读顺序

```text
1. assistant/tool/ToolPolicy.java
2. assistant/tool/AssistantTool.java
3. assistant/tool/AssistantToolRegistry.java
4. assistant/tool/ToolArguments.java
5. assistant/tool/tools/SearchProductTool.java
6. assistant/tool/tools/QueryOrderTool.java
7. assistant/tool/tools/PrepareCancelOrderTool.java
8. assistant/orchestration/AssistantToolOrchestrator.java
9. assistant/function/LangChain4jToolCallingModelGateway.java
10. assistant/function/NativeFunctionCallingService.java
11. assistant/web/AssistantToolController.java
12. src/test/java/com/aishop/assistant/tool
13. src/test/java/com/aishop/assistant/orchestration
14. src/test/java/com/aishop/assistant/function
```

读完后应能回答：

1. `ToolSpecification` 和 `ToolExecutionRequest` 分别是什么？
2. 为什么模型选择工具后不能直接调用业务 Service？
3. `prepare()` 与 `execute()` 分离解决了什么问题？
4. 用户身份为什么放在 `ToolContext`，而不是模型 slots？
5. Tool Registry 如何同时服务 Planner 和 Function Calling？
6. 为什么 confidence 不能决定高风险动作是否执行？
7. PendingAction 下一阶段还要补哪些能力？
