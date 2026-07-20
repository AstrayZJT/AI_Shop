# AI Agent 校招面试准备计划

## 1. 准备目标

本计划以通过 Java 后端和 AI Agent 方向校招面试为目标，不要求背诵所有源码，而要求做到：

1. 能够在 3 分钟内讲清 AI Shop 的完整请求链路。
2. 能够结合源码解释 Planner、Tool、DAG、状态机、RAG 和安全确认机制。
3. 能够回答设计取舍、异常处理、并发安全和当前实现边界。
4. 能够完成常见 Java 后端问题和基础算法题。
5. 能够说明每一个简历指标的来源、统计口径和复现方式。

## 2. 项目主线

面试时统一使用下面的主链路：

```text
POST /api/assistant/chat
    -> ContextBuilder
    -> PlannerFacade
    -> LLM / RuleFallback
    -> Jackson 结构化解析
    -> PlanValidator
    -> PlanSemanticGuard
    -> ConversationPlanResolver
    -> AssistantStateMachine
    -> TaskSorter / ConflictAnalyzer
    -> ConditionEvaluator
    -> ToolRegistry
    -> Tool 或 PendingAction
    -> 用户确认后恢复
    -> 实时校验并执行
    -> RAG / AnswerComposer / AgentTrace
```

项目的核心表达是：

> LLM 负责理解和规划，Java 后端负责验证和控制；只读动作可以自动执行，高风险动作必须持久化、确认和重新校验。

## 3. 每日学习方法

每天建议投入 2 到 3 小时，固定使用以下循环：

1. 合上文档主动回忆 20 分钟。
2. 打开源码定位入口、核心判断和异常分支 40 分钟。
3. 将源码内容整理成面试回答 30 分钟。
4. 运行测试或手写伪代码 20 分钟。

不要每天重新通读全部学习笔记。重点是从记忆中提取知识，再回源码验证。

## 4. 第一阶段：AI 项目主线（第 1-7 天）

### 第 1 天：完整请求链路

任务：

- 从 `AssistantController` 追踪到 `AssistantService` 和 `AssistantAgentService`。
- 画出用户输入到最终回答的完整流程图。
- 合上源码连续讲解 5 分钟。

验收：能够说明每个阶段的输入、输出和失败处理方式。

### 第 2 天：Planner 和结构化输出

重点：

- 为什么让 LLM 输出 `AssistantPlan`。
- `intent`、`action`、`slots`、`missingSlots` 的区别。
- `ResponseFormat.JSON`、Jackson 严格解析和 `PlanValidator` 的关系。
- 模型输出非法 JSON、非法 Action 或调用失败时如何规则降级。

重点源码：

- `PlannerFacade`
- `LlmAssistantPlanner`
- `PlannerPromptFactory`
- `PlanValidator`
- `PlanSemanticGuard`

### 第 3 天：Tool Calling 和 Tool Registry

重点：

- 原生 Function Calling 返回的是什么。
- 为什么主流程使用 Planner-Executor 分离。
- Spring 如何收集 `AssistantTool` Bean。
- Registry 如何按工具名称和 Action 建立白名单映射。
- `READ_ONLY` 与 `PREPARE_ONLY` 的区别。

面试回答必须包含：

> Function Calling 适合简单无状态工具；Planner 适合需要依赖、条件、确认和跨请求恢复的业务流程。

### 第 4 天：DAG 和条件分支

使用下面的案例练习：

```text
t1：查询物流
t2：dependsOn=[t1]
t2：订单状态属于可取消状态时准备取消
```

必须讲清：

- `dependsOn` 表示前置任务和执行顺序。
- `conditions` 表示前置结果是否满足业务条件。
- `TaskSorter` 使用拓扑排序。
- `ConditionEvaluator` 执行白名单字段和操作符判断。
- `TaskConflictAnalyzer` 在工具执行前检查互斥写操作。

### 第 5 天：状态机和跨请求恢复

重点状态：

```text
PENDING
RUNNING
WAITING_INPUT
WAITING_CONFIRMATION
SUCCEEDED
FAILED
SKIPPED
EXPIRED
```

分别模拟：

- 缺少订单号后追问并恢复。
- 用户确认后恢复取消流程。
- 用户拒绝操作。
- PendingAction 超时。
- 服务重启或持久化上下文清空后继续执行。

必须解释为什么不阻塞 HTTP 线程，以及 `PlanRun`、`TaskRun`、`PendingAction` 分别保存什么。

### 第 6 天：安全确认和并发

按下面的顺序讲确认接口：

```text
登录用户校验
    -> session 归属校验
    -> PendingAction 行锁
    -> 用户归属校验
    -> clientRequestId 幂等校验
    -> 状态和有效期校验
    -> 重新查询订单
    -> 订单归属和实时状态校验
    -> 执行业务操作
    -> 写入审计
```

必须主动说明当前边界：

> 当前悲观锁锁定的是 `pending_assistant_actions` 中的一条待确认记录，主要保证同一个 PendingAction 不被重复确认。生产环境还可以对 `shop_orders` 增加 `@Version` 或 `FOR UPDATE`，处理不同 PendingAction 并发操作同一订单的情况。

### 第 7 天：RAG 和评测

完整复习：

```text
原文导入
    -> 文本规范化
    -> 句子边界切块和 overlap
    -> Embedding
    -> pgvector
    -> 关键词与向量召回
    -> 分数融合和阈值过滤
    -> Citation 校验
    -> 模型回答
```

掌握以下问题：

- 为什么需要 overlap。
- 为什么要混合关键词和向量召回。
- `Hit@K` 和 `MRR` 的区别。
- 没有可靠证据时为什么拒答。
- Citation 如何防止模型引用未提供的 Chunk。
- Hit@K/MRR 从 0.4 提升到 1.0 的具体原因。

## 5. 第二阶段：Java 后端基础（第 8-14 天）

### 第 8 天：Spring

复习 IoC、依赖注入、Bean 生命周期、自动装配、AOP、事务代理和 `@Transactional` 失效场景。

### 第 9 天：数据库

复习 B+Tree、联合索引、事务隔离级别、MVCC、悲观锁、乐观锁和慢查询分析。

结合项目解释 `session_id` 查询消息、PendingAction 行锁和订单归属校验。

### 第 10 天：Redis

复习常用数据结构、缓存穿透、击穿、雪崩、缓存一致性、分布式锁和 Lua 原子操作。

### 第 11 天：Java 并发

复习 `synchronized`、`volatile`、CAS、AQS、线程池、ThreadLocal 和接口幂等。

### 第 12 天：RabbitMQ 和分布式

复习消息可靠性、重复消费、消息幂等、死信队列和分布式事务基本思想。

### 第 13 天：算法

每天练习 2 道，优先掌握：

```text
数组与哈希、双指针、二分、栈和队列、链表、树的 DFS/BFS、滑动窗口、回溯
```

### 第 14 天：后端项目串联

准备两个项目的固定表达：

- 本地生活项目：认证、缓存、分布式锁、库存扣减和消息队列。
- AI Shop：Planner、Tool、状态机、安全确认、RAG 和评测。

统一按照下面的顺序回答：

```text
业务背景
    -> 我的职责
    -> 核心方案
    -> 遇到的问题
    -> 解决方式
    -> 结果和边界
```

## 6. 第三阶段：面试实战（第 15-21 天）

### 第 15 天：自我介绍

准备 30 秒、1 分钟和 3 分钟三个版本，突出 Java 后端基础以及从后端转向 Agent 开发的经历。

### 第 16 天：AI 项目深挖

练习：

- 为什么不用直接 Function Calling。
- Planner 输出错误怎么办。
- `dependsOn` 和 `conditions` 的区别。
- 为什么需要状态机。
- 多任务执行到一半如何恢复。

### 第 17 天：安全和并发追问

练习：

- 模型越权怎么办。
- 模型伪造订单号怎么办。
- 跨用户确认如何拦截。
- 重复点击确认如何幂等。
- 订单已经发货后如何处理。

### 第 18 天：指标追问

准备每个指标的四项信息：

```text
数据来源、统计公式、复现方式、适用边界
```

必须明确：57 条是规则回归基线，RAG 的 1.0 来自 5 条固定评测，127 条是自动化测试数量，不是线上成功率。

### 第 19 天：算法模拟

每题限时 30-40 分钟，按以下顺序完成：

```text
分析思路
    -> 说明复杂度
    -> 编写代码
    -> 测试边界条件
```

### 第 20 天：完整模拟面试

完整模拟：

```text
自我介绍
    -> 项目介绍
    -> AI Agent 深挖
    -> Java 基础
    -> 数据库和 Redis
    -> 算法题
    -> 反问面试官
```

建议录音，重点检查是否主线清楚、是否夸大指标、是否能说明实现边界。

### 第 21 天：查漏补缺

只复习回答不完整和源码定位失败的问题，不再重新通读全部文档。

## 7. 高频面试题

1. 为什么使用 Planner-Executor，而不是直接 Function Calling？
2. LLM 返回非法 JSON 或错误 Action 时怎么办？
3. `intent`、`action` 和 `executionMode` 有什么区别？
4. `dependsOn` 和 `conditions` 如何配合？
5. 多任务执行一半如何恢复？
6. 取消订单为什么需要 PendingAction？
7. 并发确认如何保证业务操作只执行一次？
8. 订单确认时已经发货怎么办？
9. RAG 为什么使用混合召回？
10. Hit@K 和 MRR 如何计算？
11. Citation 如何防止模型编造来源？
12. 57 条 Planner 数据和 127 条测试分别证明什么？

## 8. 最终验收标准

达到以下标准后，开始正式投递：

- 不看文档讲完一次完整 AI 请求链路。
- 5 分钟讲清取消订单的确认、恢复和失败流程。
- 能画出 Planner、DAG 和状态机之间的关系。
- 能指出每个安全校验所在的代码层。
- 能解释 RAG 指标的来源和限制。
- 能说出至少两个当前实现边界及生产化改进方案。
- 能完成常见数组、链表、树和滑动窗口算法题。
- 能用 1 分钟讲清项目亮点，用 3 分钟讲清完整项目。

## 9. 面试时间分配

```text
AI 项目讲解       40%
Java 后端基础     25%
数据库与 Redis    15%
算法              15%
自我介绍与 HR 问题 5%
```
