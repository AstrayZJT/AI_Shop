# AI Agent 重写学习实施计划书

> 本文是本轮重写的执行主计划；完整数据库、并发和状态恢复设计细节参见 `docs/AI客服多步骤任务编排设计书.md`。

## 当前进度

第一轮结构化 Planner 已于 2026-07-14 完成并通过真实环境验收：实现 LangChain4j Model Gateway、Planner Prompt、LLM/规则双 Planner、PlanValidator、PlanSemanticGuard 和只读预览接口。

本轮第二阶段 Tool Calling 与工具注册表也已完成：实现统一 Tool 抽象、ToolPolicy、AssistantToolRegistry、四个只读业务工具、取消订单无副作用 prepare 工具、计划任务 Orchestrator，以及基于 LangChain4j `ToolSpecification` / `ToolExecutionRequest` 的原生 Function Calling 实验。完整测试共 66 条，并通过真实模型、PostgreSQL 和 pgvector 验收；验收前后订单状态不变。

本轮第三阶段 RAG 链路已完成：实现原文与规范化文本保存、可配置 overlap 切块、chunk 偏移和哈希、批量 Embedding、pgvector Metadata、关键词与向量融合、阈值/去重/TopK/上下文限制、严格 JSON AnswerComposer、citation 白名单、Prompt Injection 边界和 Hit@K/MRR 评测。完整测试共 88 条；真实环境固定 5 个问题均在第一位命中，`Hit@K=1.0`、`MRR=1.0`。

本轮第四阶段上下文与回答合成链路已于 2026-07-16 完成：正式聊天入口统一接入 Context、Planner、Tool、RAG 和 AnswerComposer；实现固定上下文预算、会话摘要上限、可信订单事实分层、跨轮订单指代、模型订单号二次约束和 Tool/RAG 结构复用。旧的关键词路由和直接订单动作聊天路径已移除，完整测试共 101 条。

本轮第五阶段多任务编排与状态机已于 2026-07-16 完成：实现 TaskSorter、TaskConflictAnalyzer、ConditionEvaluator，以及基于 AssistantPlanRun、AssistantTaskRun、PendingAssistantAction 的持久化 start/resume 状态机；正式聊天支持缺订单号补充、取消订单二次确认、用户拒绝、等待过期和服务重启恢复。完整测试共 114 条。

本轮第六阶段 Agent Guardrails 已于 2026-07-16 完成，对应本文“阶段七”：实现后端 ActionPolicyRegistry、PendingAction 查询/确认/拒绝 API、clientRequestId 幂等、`PESSIMISTIC_WRITE` 并发确认保护和统一动作审计；确认时强制复核当前用户、会话、PlanRun 当前任务、动作有效期、订单归属与实时状态。Planner 不可信输入边界升级至 `planner-v1.4`，并补充闭合标签注入、模型伪造订单号、RAG citation 白名单及跨用户攻击测试。完整测试共 126 条。

详细结果参见：

- `docs/AI Agent第一阶段学习总结.md`
- `docs/AI Agent第一阶段真实环境验收报告.md`
- `docs/AI Agent第二阶段学习总结.md`
- `docs/AI Agent第二阶段真实环境验收报告.md`
- `docs/AI Agent第三阶段RAG学习总结.md`
- `docs/AI Agent第三阶段RAG真实环境验收报告.md`
- `docs/第五阶段多任务编排与状态机源码学习文档.md`
- `docs/第六阶段Agent Guardrails源码学习文档.md`

当前 Planner、Tool、RAG、上下文、持久化状态机和取消订单 Guardrails 已接入正式 `/api/assistant/chat`。尚未投入的是支付、退款、确认收货和改地址等其他写动作的确认执行器，以及完整 Agent 评测与链路观测阶段。

## 1. 项目重新定位

这次重写的目标不是继续完善一个完整电商系统，而是把 AI Shop 改造成一个适合学习、面试和源码讲解的 Java Agent 项目。

核心定位：

> 使用 Java、Spring Boot、LangChain4j、PostgreSQL 和 pgvector，实现一个具备结构化任务规划、工具调用、RAG、上下文记忆、安全确认、流程中断恢复和离线评测能力的电商 AI Agent。

这个项目主要证明以下能力：

1. 能把大模型接入 Java 后端，而不是只调用一次 `chat()`。
2. 能让模型把自然语言解析成结构化任务计划。
3. 能设计 Tool Calling，并控制模型能调用什么、什么时候能调用。
4. 能实现 RAG 文档导入、向量检索、上下文拼接和来源引用。
5. 能处理多任务、任务依赖、参数缺失和跨请求恢复。
6. 能为高风险动作设计权限校验、二次确认和幂等保护。
7. 能通过测试集和指标评估 Agent，而不是使用模型自报的 confidence 证明效果。

项目中的商品、订单、物流和退款只是 Agent 的工具场景，不再追求完整库存、支付、营销、售后和运营系统。

## 2. 实现范围

### 2.1 必须完成的 AI 能力

| 能力 | 计划实现 |
| --- | --- |
| 模型接入 | DashScope OpenAI-compatible API + LangChain4j `ChatModel` |
| Prompt 管理 | System Prompt、Planner Prompt、Answer Prompt 分离并带版本号 |
| 结构化规划 | LLM 输出 `AssistantPlan(tasks[])` |
| Function Calling | 优先使用无副作用的 `submit_assistant_plan`；另外实现只读工具调用实验 |
| 本地兜底 | 模型不可用、超时、JSON 非法时由 `RuleAssistantPlanner` 兜底 |
| 多任务编排 | `dependsOn`、拓扑排序、结构化条件和冲突检测 |
| Tool 系统 | 工具描述、参数 Schema、注册表、调用结果和异常模型 |
| RAG | 文档切块、Embedding、pgvector、关键词与向量混合检索、引用来源 |
| 短期记忆 | 最近消息、会话摘要、未完成计划摘要 |
| 安全护栏 | 动作白名单、风险分级、用户归属校验、Prompt Injection 防护 |
| 中断恢复 | PendingAction、确认接口、PlanRun/TaskRun 状态恢复 |
| 评测 | Planner 测试集、RAG 测试集、Agent 链路集成测试和运行指标 |

### 2.2 只做最小实现的业务能力

| 业务 | 最小实现 |
| --- | --- |
| 用户 | 保留当前登录用户，用于演示数据隔离和越权防护 |
| 商品 | 准备少量商品数据，支持名称、类别、价格查询 |
| 订单 | 保留几个固定状态的测试订单 |
| 物流 | 根据订单返回模拟物流状态和时间线 |
| 取消订单 | 只实现一个可确认、可恢复、可幂等的状态修改动作 |
| 知识库 | 准备退换货、发票、物流、优惠规则等演示文档 |

### 2.3 暂不投入时间的内容

- 不重写完整商城前端，只调整 AI 对话页需要的展示。
- 不实现真实支付、库存扣减、物流平台、退款网关和复杂促销计算。
- 不完善管理后台和运营报表。
- 不为每个电商动作建立完整流程，取消订单跑通后，其他动作只保留扩展接口。
- 不在第一阶段引入微服务、消息队列、分布式事务和 Kubernetes。
- 不为了技术名称强行使用 LangGraph4j；只有工作流节点真正承担职责时才接入。

### 2.4 从 Java 后端到 Agent 开发的能力映射

| 已有 Java 后端经验 | 在 Agent 项目中的对应能力 |
| --- | --- |
| Controller / Service 分层 | Agent API 与应用编排层 |
| DTO、Jackson、Bean Validation | 模型结构化输出解析与校验 |
| Strategy / Factory / Registry | Tool 注册、动作路由和模型供应商切换 |
| 事务、状态字段、幂等 | Agent 中断恢复、PendingAction 和任务状态机 |
| 权限校验 | Tool Guardrails 与最小权限 |
| 数据库查询 | Agent 可调用的确定性业务工具 |
| Elasticsearch / SQL 搜索思路 | RAG 检索、混合召回和重排 |
| 单元测试和集成测试 | Prompt 测试、Tool 测试和 Agent 离线评测 |
| 日志和链路追踪 | Plan、Tool Call、Token、Latency Trace |

学习重点不是抛弃 Java 后端经验，而是增加“大模型输出具有概率性”这一前提，并用原有的类型系统、校验、权限、事务和测试能力控制这种不确定性。

## 3. 最终演示链路

项目最终重点演示四个场景。

### 3.1 多任务规划与安全恢复

用户输入：

```text
查一下订单 ORD-12345678 的物流，如果还没发货就帮我取消。
```

系统应完成：

```text
LLM Planner
-> 生成 t1 QUERY_LOGISTICS
-> 生成 t2 CANCEL_ORDER，dependsOn=[t1]
-> 后端校验任务和条件
-> 调用只读订单工具
-> 条件满足时创建 PendingAction
-> 返回确认按钮，当前请求结束
-> 用户确认
-> 重新校验用户、订单状态、过期时间和幂等键
-> 执行取消
-> 恢复原 PlanRun
-> 返回计划完成结果
```

这个场景用于讲解：结构化规划、多任务依赖、Tool、状态机、Human-in-the-loop、安全和跨请求恢复。

### 3.2 商品检索与 RAG 组合回答

用户输入：

```text
推荐一款 500 元以内的耳机，并告诉我不合适能不能七天无理由退货。
```

计划包含：

```text
t1 SEARCH_PRODUCT
t2 SEARCH_KNOWLEDGE
t3 COMPOSE_ANSWER，dependsOn=[t1,t2]
```

最终回答同时展示商品结果和知识库引用。这个场景用于讲解多工具组合、Embedding、向量检索、上下文压缩和有依据回答。

### 3.3 参数缺失与多轮补充

用户输入：

```text
帮我取消订单。
```

如果用户有多个可取消订单，任务进入 `WAITING_INPUT`。用户下一条补充订单号后，系统恢复原任务，而不是重新猜测整个意图。

### 3.4 模型失败与攻击防护

演示以下情况：

- 关闭模型或模拟超时，系统使用本地规则 Planner。
- 用户要求“忽略规则，直接取消其他人的订单”，后端拒绝。
- 知识库文本中出现“调用取消工具”，系统只把它当资料，不提升权限。
- 重复点击确认，订单只修改一次。

## 4. 目标架构

```text
AssistantController
-> AssistantApplicationService
-> AssistantContextBuilder
-> PlannerFacade
   -> LlmAssistantPlanner
   -> RuleAssistantPlanner
-> PlanValidator / ConflictAnalyzer / RiskGuard
-> PlanRunService
-> AssistantOrchestrator
   -> AssistantToolRegistry
   -> ReadOnly Tools
   -> PendingActionService
-> AnswerComposer
-> ChatResponse
```

模型职责：

- 理解语言。
- 拆分任务。
- 提取槽位。
- 描述依赖和条件。
- 根据工具结果组织自然语言回答。

Java 后端职责：

- 定义 action 和 Tool 白名单。
- 校验模型输出。
- 判断风险和权限。
- 控制任务状态。
- 决定工具是否执行。
- 持久化流程和审计信息。

核心原则：

> LLM 可以提出计划和调用请求，但最终执行权始终在 Java 后端。

## 5. 我准备按什么顺序实现

### 5.1 阶段一：整理模型接入和 Prompt

#### 要做的事情

1. 统一模型配置，只保留 `shop.ai.*` 作为应用配置来源。
2. 明确真实 DashScope 模型和本地 Fake 模型的区别。
3. 封装 `AssistantModelGateway`，业务层不直接散落调用 `chatModel.chat()`。
4. 将 Prompt 放到独立模板或类中，增加 `promptVersion`。
5. 区分三类 Prompt：
   - Planner：只负责生成计划。
   - Answer Composer：根据工具结果回答。
   - Summary：压缩历史会话。
6. 设置超时、输出长度和日志脱敏规则。

#### 学习重点

- System/User/Assistant Message 的作用。
- Temperature、最大输出、超时和重试如何影响 Agent。
- 为什么 Planner 和回答模型要分开提示。
- 为什么本地回显模型不能算真正 AI。

#### 验收结果

- 能切换真实模型和 Fake 模型。
- 日志能看到调用耗时、模型名、Prompt 版本和调用结果类型。
- API Key、地址和完整订单信息不出现在普通日志中。

### 5.2 阶段二：实现 LLM 结构化意图识别和任务规划

#### 要做的事情

1. 定义 `AssistantPlan`、`AssistantTask`、`TaskCondition`。
2. 定义固定枚举 `AssistantIntent`、`AssistantAction`。
3. 实现 Planner Prompt，让模型支持单任务和多任务。
4. 优先尝试通过 `submit_assistant_plan` Function Calling 提交计划。
5. 如果供应商嵌套 Schema 兼容性不稳定，则使用严格 JSON + Jackson。
6. 实现 `PlanValidator`：
   - action 白名单。
   - taskId 唯一。
   - dependsOn 合法。
   - 禁止循环依赖。
   - 槽位长度和类型限制。
   - 一次最多 5 个任务。
7. 实现 `RuleAssistantPlanner`，迁移现有关键词规则作为故障兜底。

#### 学习重点

- 普通聊天、结构化输出和 Function Calling 的区别。
- 模型生成 JSON 为什么仍然不能直接信任。
- confidence 为什么不是准确率。
- Prompt、Schema 和后端校验如何共同约束模型。

#### 验收结果

- 典型输入能稳定生成合法 `tasks[]`。
- 多目标输入能拆成多个任务。
- 非法 JSON、未知 action 和模型超时能安全降级。
- 当前阶段只输出计划，不执行订单写操作。

### 5.3 阶段三：实现 Tool Calling 和工具注册表

> 状态：已于 2026-07-14 完成，对应本轮 Goal 的“第二阶段”。

#### 要做的事情

1. 定义统一工具接口：

```java
public interface AssistantTool {
    AssistantAction action();
    PreparedToolCall prepare(ToolContext context, Map<String, Object> slots);
    ToolResult execute(ToolContext context, PreparedToolCall call);
}
```

2. 实现最小工具集合：
   - `SearchProductTool`
   - `QueryOrderTool`
   - `QueryLogisticsTool`
   - `SearchKnowledgeTool`
   - `PrepareCancelOrderTool`
3. 实现 `AssistantToolRegistry`，根据 action 查找工具。
4. 为工具定义名称、描述、参数 Schema、风险等级和返回结构。
5. 做一个独立的只读 Function Calling 实验，让模型选择查询工具并观察原始 tool request/result。
6. 正式主链路仍由 Orchestrator 决定是否执行；高风险工具只允许 `prepare()`，不能自动调用真实取消方法。

#### 学习重点

- Tool definition、ToolExecutionRequest 和 Tool result 的完整协议。
- 模型“选择工具”和 Java“执行工具”是两个阶段。
- 工具描述如何影响模型选错工具。
- 为什么高风险业务方法不能直接暴露为自动执行的 `@Tool`。

#### 验收结果

- 能在日志或调试页看到模型选择了哪个工具、传入什么参数。
- 非白名单工具不能执行。
- Tool 参数会经过后端 DTO 和业务规则校验。
- 模型无法直接取消订单。
- 已实现 `GET /api/assistant/tools`、`POST /api/assistant/tools/plan-preview` 和 `POST /api/assistant/tools/function-call-preview`。
- 真实模型成功选择并执行订单、商品、知识库查询工具，取消订单只返回 `PREPARED`。
- 完整自动化测试 66 条全部通过，真实验收前后订单状态一致。

### 5.4 阶段四：重写 RAG 链路

> 状态：已于 2026-07-14 完成，对应本轮 Goal 的“第三阶段”。

#### 要做的事情

1. 保留并整理现有知识文档导入功能。
2. 明确文档处理流程：读取、清洗、切块、Metadata、Embedding、存储。
3. 使用 pgvector 保存向量，保留原文 chunk 和文档来源。
4. 实现关键词 + 向量混合检索。
5. 设置 topK、最低分数、去重和上下文最大长度。
6. `SearchKnowledgeTool` 返回结构化结果，而不是直接生成答案。
7. `AnswerComposer` 只根据命中的上下文回答规则问题，并返回 citations。
8. 增加 Prompt Injection 分隔和知识文本不可信声明。

#### 学习重点

- Embedding 和聊天模型解决的问题不同。
- Chunk 大小和 overlap 对召回的影响。
- 向量相似度高不代表答案一定正确。
- Retrieval、Augmentation、Generation 三个阶段如何对应代码。
- Recall@K、命中率和回答正确率的区别。

#### 验收结果

- 能从文档原文定位到 chunk、向量记录和最终引用。
- “七天无理由”类问题能命中正确规则。
- 没有可靠知识命中时明确说明不确定，不编造政策。
- 真实 pgvector 环境 5 条固定评测全部在第一位命中，Hit@K 和 MRR 均为 1.0。
- 未知问题在模型调用前返回 `NO_EVIDENCE`，模型引用上下文外 chunk 时由 Java 拒绝。

### 5.5 阶段五：实现上下文、记忆和回答合成

> 状态：已于 2026-07-16 完成，对应本轮 Goal 的“第四阶段”。

#### 要做的事情

1. 实现 `AssistantContextBuilder`。
2. 上下文只包含：
   - 当前用户消息。
   - 最近若干轮会话。
   - 会话摘要。
   - 当前用户拥有的必要订单摘要。
   - 未完成计划摘要。
   - 工具和 RAG 返回结果。
3. 超过上下文预算时先压缩历史，不无限拼接消息。
4. 分离事实数据和自然语言描述，避免把模型总结当数据库事实。
5. 实现 `AnswerComposer`，输入结构化工具结果，输出用户回答和来源。

#### 学习重点

- 短期记忆、长期记忆和业务状态的区别。
- Context Window 为什么不是越长越好。
- 会话摘要为什么只能帮助理解，不能替代订单数据库。
- 如何降低多轮对话中的信息污染。

#### 验收结果

- 用户第二轮使用“这个订单”时能关联上一轮目标。
- 历史消息增长后 Prompt 仍有固定上限。
- 模型不能通过历史摘要伪造订单归属或状态。
- 正式 `POST /api/assistant/chat` 已统一接入 Planner、Tool、RAG 和结构化回答合成。
- 完整自动化测试 101 条全部通过。

### 5.6 阶段六：实现多任务编排和状态机

> 状态：已于 2026-07-16 完成，对应本轮 Goal 的“第五阶段”。

#### 要做的事情

1. 实现 `TaskSorter`，对 `dependsOn` 做拓扑排序。
2. 实现 `TaskConflictAnalyzer`，识别同一订单取消+支付等冲突。
3. 实现 `ConditionEvaluator`，只支持白名单字段和操作符。
4. 实现 `AssistantOrchestrator.start()` 和 `resume()`。
5. 使用三类持久化状态：
   - `AssistantPlanRun`
   - `AssistantTaskRun`
   - `PendingAssistantAction`
6. 只完整实现以下状态：

```text
PENDING
RUNNING
WAITING_INPUT
WAITING_CONFIRMATION
SUCCEEDED
SKIPPED
FAILED
EXPIRED
```

7. 实现缺槽位后的下一轮恢复。
8. 实现取消订单确认后的跨请求恢复。

#### 学习重点

- Agent Loop 与普通 Service 顺序调用的区别。
- 多任务依赖和条件分支如何表达。
- 为什么等待用户不能阻塞 HTTP 线程。
- Checkpoint、业务状态表和会话历史各自保存什么。

#### 验收结果

- “查物流，如果未发货就取消”完整跑通。
- 服务重启后仍能找到待确认动作。
- 条件不满足时取消任务变为 `SKIPPED`，不会产生确认动作。
- 缺订单号时进入 `WAITING_INPUT`，下一轮补充后从原任务恢复，不重新调用 Planner。
- 取消订单进入 `WAITING_CONFIRMATION`，确认、拒绝和过期均有持久化状态。
- 完整自动化测试 114 条全部通过。
- H2 临时环境正式 HTTP 链路验收通过：取消请求先等待确认，确认后同一 PlanRun 恢复成功，订单由 `PENDING_PAYMENT` 变为 `CANCELLED`。

### 5.7 阶段七：实现安全确认和 Agent Guardrails

> 状态：已于 2026-07-16 完成，对应本轮 Goal 的“第六阶段”。

#### 要做的事情

1. 后端 `ActionPolicyRegistry` 固定每个 action 的风险等级。
2. 取消订单必须创建 PendingAction。
3. 确认时重新校验：
   - 当前登录用户。
   - 订单归属。
   - 订单实时状态。
   - PendingAction 状态和有效期。
   - clientRequestId 幂等键。
4. 增加并发确认保护，确保重复点击只执行一次。
5. 增加 Prompt Injection 测试和跨用户订单测试。
6. 记录 plannerSource、action、tool、确认用户、执行结果和失败原因。

#### 学习重点

- Guardrails 不是在 Prompt 中写一句“请安全”。
- 模型输出、用户输入、RAG 文本都属于不可信输入。
- Human-in-the-loop 解决什么问题，又不能替代什么校验。
- Agent 权限应小于应用后端权限。

#### 验收结果

- 即使模型选错 action，也不能越过后端策略。
- 即使用户知道他人订单号，也不能查询或取消。
- 过期、重复、并发确认都不会重复修改订单。
- 相同 `clientRequestId` 并发确认时，第二个事务等待行锁并在首个事务提交后返回持久化结果，业务执行器只调用一次。
- 跨用户 HTTP 确认返回 `403`，PendingAction 和订单状态均保持不变。
- H2 正式 HTTP 链路完成 `PENDING_PAYMENT -> PENDING -> EXECUTED -> CANCELLED`，相同幂等键重放标记为 `idempotentReplay=true`，订单取消时间线只有一条。
- 完整自动化测试 126 条全部通过。

### 5.8 阶段八：评测、可观测性和面试材料

#### 要做的事情

1. 建立 `planner-cases.jsonl`，准备 40 到 60 条人工标注意图样本。
2. 统计：
   - intent 准确率。
   - action Exact Match。
   - slots Exact Match。
   - 多任务拆分正确率。
   - 规则兜底率。
3. 建立 RAG 问题集，统计 Recall@K 和引用命中情况。
4. 记录一次 Agent 请求的 trace：plan、task、tool、latency、result、token usage。
5. 增加 Stub ChatModel 测试，CI 不调用真实模型。
6. 准备 4 个固定演示脚本和失败案例。
7. 更新 README、架构图、源码讲解、简历描述和面试问答。

#### 学习重点

- Agent 如何测试，为什么不能只手工聊几句。
- 确定性单元测试和概率性模型评测如何分开。
- 如何根据失败样本调整 Prompt、Schema、Tool 描述和规则。
- 如何在面试中准确区分“已经实现”和“设计上可以扩展”。

#### 验收结果

- 所有简历指标都能从测试集或运行数据得到。
- 可以沿着 trace 解释一次请求为什么选择某个工具。
- 可以现场演示模型失败、RAG 命中、安全拒绝和确认恢复。

## 6. LangGraph4j 的安排

当前项目虽然已经引入 LangGraph4j，但实际图只有 `start -> end`，不能作为 Agent 编排亮点。

计划分两步学习：

1. 先用普通 Spring Service 实现 Planner、Validator、Tool Registry 和状态机，确保真正理解每一步。
2. 基础链路稳定后，再把单次请求内的流程改造成一个小型 LangGraph4j 图：

```text
load_context
-> plan
-> validate
-> route
   -> clarify
   -> execute_read_tools
   -> create_pending_action
-> compose_answer
-> end
```

数据库中的 PlanRun、TaskRun 和 PendingAction 仍是跨请求恢复的业务事实来源。LangGraph4j checkpoint 用于学习和保存图运行状态，但不替代订单权限、订单状态和幂等记录。

只有完成非空图、条件边和 checkpoint 恢复后，简历中才写“使用 LangGraph4j 构建状态图”；否则只写 LangChain4j Tool Calling 和自研状态编排。

## 7. 代码组织计划

```text
com.aishop.assistant
├── application
│   ├── AssistantApplicationService
│   └── AssistantActionService
├── model
│   ├── AssistantPlan
│   ├── AssistantTask
│   ├── TaskCondition
│   └── enums
├── planner
│   ├── PlannerFacade
│   ├── LlmAssistantPlanner
│   ├── RuleAssistantPlanner
│   └── PlannerPromptFactory
├── context
│   ├── AssistantContext
│   └── AssistantContextBuilder
├── validation
│   ├── PlanValidator
│   ├── TaskConflictAnalyzer
│   ├── TaskSorter
│   └── RiskGuard
├── orchestration
│   ├── AssistantOrchestrator
│   └── ConditionEvaluator
├── tool
│   ├── AssistantTool
│   ├── AssistantToolRegistry
│   └── tools
├── rag
│   ├── KnowledgeRetriever
│   └── RagContextBuilder
├── response
│   └── AnswerComposer
└── evaluation
    └── PlannerEvaluator
```

现有 `OrderService`、`ProductService` 和 `KnowledgeService` 作为工具背后的业务适配器使用，不重写无关业务。

## 8. 第一轮实际编码范围

为了避免一次改动过大，第一轮只完成以下内容：

1. `AssistantPlan`、`AssistantTask`、枚举和 JSON Schema。
2. `LlmAssistantPlanner` 与 `RuleAssistantPlanner`。
3. `PlanValidator`。
4. Planner Prompt 和 15 条固定测试用例。
5. 新增调试接口或开发日志，能查看原始模型输出、解析结果和兜底原因。
6. 暂时不执行任何业务 Tool，不改订单状态。

第一轮完成的判断标准：

```text
用户消息
-> 真实模型生成结构化计划
-> Java 成功解析
-> 后端校验
-> 失败时规则兜底
-> 测试能够重复验证
```

完成这一轮后，再进入 Tool Calling，不会同时修改 Planner、RAG、状态机和前端。

## 9. 最终交付物

项目重写完成后应包含：

1. 可运行的 Java Agent 客服主链路。
2. 一套真实模型和本地 Stub 的双模式配置。
3. 结构化 Planner、Tool Registry、RAG、记忆、状态恢复和 Guardrails 源码。
4. 至少一个高风险动作的完整 Human-in-the-loop 实现。
5. Planner 与 RAG 离线评测集及结果。
6. Agent 请求 trace 和调试信息。
7. AI 架构设计、源码链路、开发日志和面试教程。
8. 与真实代码一致的简历项目描述。

## 10. 面试定位

最终不把项目描述成“完整电商平台”，而是描述成：

> 面向电商客服场景的 Java AI Agent：使用 LangChain4j 接入通义千问，将用户请求解析为结构化多任务计划，通过工具注册表完成订单查询、商品检索和知识库检索；结合 pgvector 实现带来源引用的 RAG，并通过 PlanRun/TaskRun 状态机处理参数补充和任务依赖。针对取消订单等写操作设计 PendingAction Human-in-the-loop 机制，确认后重新校验用户归属、订单状态、有效期和幂等键，再恢复原任务执行；同时建立离线评测集分析规划准确率和 RAG 召回效果。

最重要的面试原则：

> 业务可以是最小实现，但 AI 链路必须真实、可运行、可测试、可解释，所有写在简历上的能力都能结合源码和演示证明。
