# AI Agent 第一阶段学习总结

## 1. 本阶段目标

第一阶段只解决一个问题：

> 用户输入自然语言后，系统如何通过 LangChain4j 调用真实大模型，把消息转换成经过 Java 后端校验的结构化任务计划，并在模型不可用时安全降级。

本阶段不调用商品、订单或知识库 Tool，也不修改任何订单状态。

已经完成：

- LangChain4j Planner Model Gateway。
- System Prompt 与 User Prompt 分离。
- `AssistantPlan`、`AssistantTask`、`TaskCondition`。
- LLM Planner。
- 本地规则 Planner。
- Planner Facade 自动降级。
- PlanValidator 后端硬校验。
- 只读 Planner 预览接口。
- 41 条可重复自动化测试。

## 2. 第一阶段链路

```text
POST /api/assistant/planner/preview
-> AssistantPlannerController
-> PlannerFacade
-> 检查 AI 是否启用、API Key 是否存在
-> LlmAssistantPlanner
-> PlannerPromptFactory
-> LangChain4jPlannerModelGateway
-> ChatModel.chat(ChatRequest)
-> 解析模型 JSON
-> PlanValidator
-> 返回 PlannerResult
```

异常链路：

```text
模型未启用 / Key 缺失 / 请求失败 / 空输出 / 非法 JSON / 非法计划
-> RuleAssistantPlanner
-> PlanValidator
-> 返回结构相同的 PlannerResult
-> source = RULE_FALLBACK
-> fallbackReason 记录降级原因
```

## 3. 为什么使用 LangChain4j

项目不是直接拼 HTTP 请求调用 DashScope，而是通过 LangChain4j 的统一接口：

```java
ChatRequest request = ChatRequest.builder()
        .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
        .temperature(properties.ai().plannerTemperature())
        .maxOutputTokens(properties.ai().plannerMaxOutputTokens())
        .responseFormat(ResponseFormat.JSON)
        .build();

ChatResponse response = chatModel.chat(request);
```

代码位置：

- `src/main/java/com/aishop/assistant/planner/LangChain4jPlannerModelGateway.java`

LangChain4j 在这里提供：

1. 统一的 `ChatModel` 抽象。
2. System、User、AI Message 类型。
3. ChatRequest 参数模型。
4. JSON Response Format。
5. 模型名、TokenUsage 等响应元数据。
6. 后续 Tool Calling 的 ToolSpecification 与 ToolExecutionRequest API。

如果以后从通义千问切换到其他 OpenAI-compatible 模型，Planner 上层代码不需要直接处理供应商 HTTP 协议。

## 4. Model Gateway 的作用

`PlannerModelGateway` 是自定义接口：

```java
public interface PlannerModelGateway {
    PlannerModelReply generatePlan(String systemPrompt, String userPrompt);
}
```

实现类 `LangChain4jPlannerModelGateway` 才依赖 LangChain4j。

这样拆分的原因：

- `LlmAssistantPlanner` 只关心文本如何转换成 `AssistantPlan`。
- 单元测试可以注入 Fake Gateway，不访问网络。
- 模型供应商、超时和请求参数集中在一处。
- 后续可以增加 Function Calling Gateway，而不修改 Validator。

这是 Java 后端中依赖倒置、适配器模式在 Agent 开发里的直接应用。

## 5. 模型配置

配置类：

- `src/main/java/com/aishop/config/ShopProperties.java`
- `src/main/java/com/aishop/config/AiModelConfig.java`

新增配置：

```yaml
shop:
  ai:
    timeout: ${SHOP_AI_TIMEOUT:30s}
    max-retries: ${SHOP_AI_MAX_RETRIES:1}
    planner-max-output-tokens: ${SHOP_AI_PLANNER_MAX_OUTPUT_TOKENS:1800}
    planner-temperature: ${SHOP_AI_PLANNER_TEMPERATURE:0.0}
```

Planner 使用低温度，是因为任务规划更看重结构稳定和可重复性，而不是语言创造性。

运行模式：

| 条件 | Planner 行为 |
| --- | --- |
| `SHOP_AI_ENABLED=false` | 不调用模型，直接规则兜底 |
| AI 开启但没有 `OPENAI_API_KEY` | 不调用模型，返回 `API_KEY_MISSING` |
| AI 开启且 Key 存在 | 调用真实 ChatModel |
| 模型调用或解析失败 | 自动切换规则 Planner |

## 6. 结构化计划模型

主要类型位于：

- `src/main/java/com/aishop/assistant/model`

`AssistantPlan`：

```java
public record AssistantPlan(
        PlanType planType,
        List<AssistantTask> tasks,
        String summary
) {}
```

`AssistantTask`：

```java
public record AssistantTask(
        String taskId,
        AssistantIntent intent,
        AssistantAction action,
        ExecutionMode executionMode,
        Map<String, Object> slots,
        List<String> missingSlots,
        List<String> dependsOn,
        List<TaskCondition> conditions,
        Double confidence,
        String reason
) {}
```

这里使用 Java enum，而不是任意字符串。模型如果生成未知 action，Jackson 解析或 PlanValidator 会拒绝该计划。

`executionMode` 也不包含 `DIRECT_EXECUTE`。高风险动作只能是 `ASK_CONFIRM`，从类型层面减少危险路径。

## 7. Prompt 如何设计

代码位置：

- `src/main/java/com/aishop/assistant/planner/PlannerPromptFactory.java`

Prompt 版本：

```text
planner-v1.3
```

System Prompt 定义：

- Planner 的角色。
- 可使用的枚举。
- JSON 输出结构。
- 最大任务数。
- 高风险动作规则。
- 缺少参数不能猜测。
- 用户输入和历史属于不可信数据。

User Prompt 不直接把文本拼到系统指令后，而是先序列化成 JSON，再放入：

```text
<untrusted_input>
...
</untrusted_input>
```

这不能单独解决 Prompt Injection，但可以明确指令和数据边界。真正的权限控制仍然由 Java Validator 和后续 RiskGuard 完成。

## 8. LLM Planner 如何解析结果

代码位置：

- `src/main/java/com/aishop/assistant/planner/LlmAssistantPlanner.java`

处理步骤：

1. 调用 Model Gateway。
2. 检查模型输出是否为空。
3. 限制原始输出不超过 32 KB。
4. 使用 Jackson 反序列化为 `AssistantPlan`。
5. 非法 JSON 抛出带错误码的 `PlannerException`。
6. 保留原始输出，方便开发阶段查看模型失败原因。

当前采用严格 JSON：模型返回 Markdown 代码块时也会失败并进入兜底，不使用正则偷偷截取 JSON。

这样设计能暴露 Prompt 或模型兼容性问题，不会让“看起来解析成功”掩盖输出不稳定。

## 9. PlanValidator 校验什么

代码位置：

- `src/main/java/com/aishop/assistant/validation/PlanValidator.java`

主要校验：

1. 一次最多 5 个任务。
2. `SINGLE_TASK` 只能有一个任务。
3. taskId 格式和唯一性。
4. intent 与 action 是否匹配。
5. action 与 executionMode 是否匹配。
6. Tool 槽位白名单。
7. 必填槽位是否存在，或者是否明确放入 missingSlots。
8. 槽位值类型与长度。
9. dependsOn 引用是否存在。
10. 是否存在循环依赖。
11. condition 是否只引用直接依赖任务。
12. 条件字段、操作符和订单状态是否合法。
13. confidence 是否在 0 到 1 之间。

结构校验之后还会调用 `PlanSemanticGuard`。它负责检查“结构合法但语义危险”的计划，例如用户只询问“怎么取消订单”，模型却同时生成了 `CANCEL_ORDER`。这类计划会被拒绝并进入规则兜底。

模型即使返回：

```json
{
  "action": "CANCEL_ORDER",
  "executionMode": "TOOL_READ"
}
```

也会被后端拒绝，因为 `CANCEL_ORDER` 的固定执行模式只能是 `ASK_CONFIRM`。

## 10. 本地规则 Planner

代码位置：

- `src/main/java/com/aishop/assistant/planner/RuleAssistantPlanner.java`

它不是为了替代大模型，而是提供：

- 模型关闭时的可运行模式。
- 模型超时时的故障降级。
- 模型输出非法时的结构化兜底。
- 后续离线对比 LLM 与规则结果的基线。

规则 Planner 和 LLM Planner 最终生成同一个 `AssistantPlan`，所以下游不需要维护两套接口。

已经支持的基础规则包括：

- 订单和物流查询。
- 查询物流后条件取消。
- 支付、退款、确认收货和改地址。
- 商品推荐。
- 商品推荐与售后规则多任务。
- 优惠活动。
- 转人工。
- 普通聊天。

所有订单写动作只生成 `ASK_CONFIRM` 计划，本阶段不会真正执行。

## 11. PlannerFacade 为什么重要

代码位置：

- `src/main/java/com/aishop/assistant/planner/PlannerFacade.java`

Facade 统一决定使用哪个 Planner：

```text
AI disabled -> Rule
Key missing -> Rule
LLM success + validation success -> LLM
LLM call failed -> Rule
LLM JSON invalid -> Rule
LLM plan violates policy -> Rule
```

返回值包含：

```text
plan
source
promptVersion
fallbackReason
rawModelOutput
modelName
inputTokens
outputTokens
```

面试时可以强调：系统不是简单的 `try/catch` 后返回一句固定话，而是切换到一个遵守相同 Schema 的规则 Planner，因此下游任务编排接口保持不变。

## 12. 预览接口

接口：

```text
POST /api/assistant/planner/preview
```

代码位置：

- `src/main/java/com/aishop/assistant/web/AssistantPlannerController.java`

请求示例：

```json
{
  "message": "查一下订单 ORD-12345678 的物流，如果还没发货就取消",
  "conversationSummary": null,
  "recentMessages": []
}
```

该接口要求用户已登录，只生成和校验计划，不执行 Tool，不修改业务数据。

响应中重点观察：

- `source=LLM`：真实模型计划通过校验。
- `source=RULE_FALLBACK`：使用了本地兜底。
- `fallbackReason`：降级原因。
- `tasks`：模型拆分出的结构化任务。
- `rawModelOutput`：模型原始 JSON，仅用于当前学习和调试阶段。

## 13. 自动化测试

测试目录：

- `src/test/java/com/aishop/assistant`

当前共 41 条测试：

| 测试类 | 覆盖内容 |
| --- | --- |
| `LangChain4jPlannerModelGatewayTest` | LangChain4j Message、JSON 格式、参数和 TokenUsage |
| `LlmAssistantPlannerTest` | JSON 解析、空输出、非法 JSON、Markdown 代码块 |
| `PlannerFacadeTest` | AI 关闭、Key 缺失、模型失败和非法计划降级 |
| `PlannerPromptFactoryTest` | 不可信输入边界和历史数量限制 |
| `RuleAssistantPlannerTest` | 10 类典型用户意图和多任务规则 |
| `PlanValidatorTest` | 任务数、重复 ID、依赖环、危险模式、非法槽位和条件 |
| `PlanSemanticGuardTest` | 纯咨询禁止写动作、明确执行请求允许进入确认计划 |

执行：

```powershell
mvn test
```

结果：

```text
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试使用 Fake ChatModel 或 Fake Gateway，不访问真实 API，因此 CI 和本地可以稳定重复运行。

本次执行环境没有配置 `OPENAI_API_KEY`，因此没有发起需要计费的 DashScope 请求。真实模型链路可以在设置 `SHOP_AI_ENABLED=true` 和 `OPENAI_API_KEY` 后，通过同一个 Planner 预览接口验证；是否返回 `source=LLM` 以模型输出通过后端校验为准。

另外使用 Java 22、H2 和 `SHOP_AI_ENABLED=false` 启动完整 Spring Boot 应用，登录后调用预览接口，得到：

```text
source=RULE_FALLBACK
fallbackReason=AI_DISABLED
planType=MULTI_TASK
actions=QUERY_LOGISTICS,CANCEL_ORDER
第二个任务 dependsOn=t1
```

这次冒烟验证覆盖了配置绑定、Spring Bean 注入、登录 Session、Controller 和完整规则降级链路。临时验证服务完成后已经关闭。

## 14. 当前没有完成什么

以下内容属于后续阶段：

- 尚未把 Planner 接入 `/api/assistant/chat` 正式主链路。
- 尚未实现业务 Tool Calling。
- 尚未执行商品、订单或知识库工具。
- 尚未实现 PlanRun/TaskRun 持久化。
- 尚未实现 PendingAction。
- 尚未实现确认后恢复。
- 尚未实现原生 `submit_assistant_plan` Function Calling。

当前使用的是 LangChain4j ChatModel + 严格 JSON 结构化规划。只有后续真正接入 ToolSpecification/ToolExecutionRequest 后，才在简历里写原生 Function Calling。

## 15. 推荐源码阅读顺序

```text
1. assistant/model/AssistantPlan.java
2. assistant/model/AssistantTask.java
3. assistant/planner/PlannerPromptFactory.java
4. assistant/planner/LangChain4jPlannerModelGateway.java
5. assistant/planner/LlmAssistantPlanner.java
6. assistant/validation/PlanValidator.java
7. assistant/planner/RuleAssistantPlanner.java
8. assistant/planner/PlannerFacade.java
9. assistant/web/AssistantPlannerController.java
10. src/test/java/com/aishop/assistant
```

读完后应能回答：

1. LangChain4j 在项目中具体负责什么？
2. Planner Prompt 为什么和 Answer Prompt 分开？
3. 模型 JSON 为什么还要用 Java Validator？
4. 模型失败后为什么规则 Planner 仍能接入同一下游？
5. confidence 为什么不能直接决定是否执行？
6. 当前为什么还不能宣称实现了完整 Tool Calling Agent？

## 16. 下一阶段

下一阶段重点是 Tool Calling 与工具注册表：

1. 定义 `AssistantTool`、`PreparedToolCall` 和 `ToolResult`。
2. 实现商品搜索、订单查询、物流查询和知识库查询四个只读工具。
3. 使用 LangChain4j ToolSpecification/ToolExecutionRequest 做原生 Function Calling 实验。
4. 主链路由 Java Orchestrator 控制 Tool 是否执行。
5. 高风险工具只允许 prepare，不直接修改订单。

第一阶段提供“模型理解并生成计划”，第二阶段才开始提供“模型选择工具并由 Java 安全执行”。
