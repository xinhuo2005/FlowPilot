# FlowPilot 动态规则编排与灰度发布平台技术升级报告

## 1. 项目背景

随着业务复杂度增加，传统后端系统中大量业务流程直接固化在 Service 层代码中，例如订单创建、风控校验、库存检查、价格计算等流程通常通过大量 `if/else`、模板方法或硬编码调用链进行组织。

这种模式在业务规模较小时实现简单，但随着规则变化频率增加，会逐渐暴露出以下问题：

1. **业务逻辑与执行流程强耦合**  
   组件逻辑与调用顺序混杂在同一代码中，修改业务流程往往需要修改 Java 代码。

2. **规则变更需要重新发布服务**  
   即使只是调整节点顺序或增加一个业务校验，也需要重新编译、部署服务。

3. **不同业务场景产生大量重复流程代码**  
   普通用户、VIP 用户、灰度用户可能只有少量流程差异，但仍需要维护多套逻辑。

4. **复杂流程难以维护**  
   当出现并行节点、条件节点、规则动态切换等需求时，传统代码结构会迅速膨胀。

5. **规则发布缺少版本治理能力**  
   缺乏统一的版本管理、发布、灰度、回滚和执行记录机制。

因此，本项目基于 LiteFlow 提供的组件编排能力，进一步构建一个面向业务规则生命周期管理的轻量级规则治理平台。

项目目标不是重新实现 LiteFlow，而是将 LiteFlow 作为底层规则执行引擎，在其之上补充企业级规则管理能力。

---

# 2. 项目目标

FlowPilot 的核心目标是实现：

> 业务组件与执行流程解耦，并围绕规则版本建立可发布、可灰度、可回滚、可追踪的完整执行链路。

第一阶段系统重点解决以下问题：

- 业务流程动态配置
- 规则版本管理
- 运行时规则切换
- 灰度规则发布
- 在途请求版本一致性
- 执行链路追踪
- 本地规则缓存
- 并发发布控制

项目不追求构建完整 BPM 平台或低代码平台，而是聚焦于 Java 后端系统中常见的短生命周期业务流程编排场景。

---

# 3. 技术架构

## 3.1 技术栈

后端技术栈：

```text
Java 21
Spring Boot
LiteFlow
MySQL
Redis
Caffeine
MyBatis / MyBatis-Plus
JUnit 5
Docker Compose
```

可选增强：

```text
Micrometer
Prometheus
Redis Stream
RocketMQ
```

第一阶段不作为强依赖。

---

## 3.2 总体架构

```text
                    Client
                      │
                      ↓
           FlowExecutionController
                      │
                      ↓
                 RuleResolver
                      │
              ┌───────┴────────┐
              ↓                ↓
          GrayRouter        RuleCache
              │                │
              └───────┬────────┘
                      ↓
                 RuleSnapshot
                      │
                      ↓
              LiteFlowRuleEngine
                      │
          ┌───────────┼───────────┐
          ↓           ↓           ↓
      UserCheck    RiskCheck    StockCheck
          │           │           │
          └───────────┼───────────┘
                      ↓
               ExecutionTrace
                      │
                      ↓
                    MySQL
```

规则发布链路：

```text
Admin
  ↓
RuleController
  ↓
RulePublisher
  ↓
RuleValidator
  ↓
保存规则版本
  ↓
加载 LiteFlow Chain
  ↓
更新当前版本
  ↓
刷新 Rule Cache
```

---

# 4. LiteFlow 在系统中的定位

LiteFlow 在项目中仅作为：

> 业务组件编排与运行时执行引擎。

LiteFlow 负责：

- Component 注册
- Chain 编排
- EL 表达式解析
- THEN / WHEN / IF 等流程执行
- Context 上下文传递
- 节点生命周期执行

FlowPilot 自身负责：

- 规则定义
- 规则版本
- 发布与回滚
- 灰度策略
- Rule Snapshot
- 缓存
- 执行追踪
- 并发发布控制
- 规则状态管理

因此系统结构并不是对 LiteFlow 的简单包装，而是：

```text
LiteFlow
    ↓
Execution Engine

FlowPilot
    ↓
Rule Governance Platform
```

---

# 5. 业务模型设计

第一阶段固定使用“订单风控流程”作为 Demo 业务。

核心 Component：

```text
UserCheckComponent
RiskCheckComponent
StockCheckComponent
VipCheckComponent
PriceCalculateComponent
CreateOrderComponent
NotifyComponent
```

规则 V1：

```text
THEN(
    userCheck,
    stockCheck,
    priceCalculate,
    createOrder,
    notify
)
```

规则 V2：

```text
THEN(
    userCheck,
    WHEN(
        riskCheck,
        stockCheck
    ),
    priceCalculate,
    createOrder,
    notify
)
```

规则 V3 可进一步扩展：

```text
THEN(
    userCheck,
    WHEN(
        riskCheck,
        stockCheck
    ),
    IF(
        vipCheck,
        vipPriceCalculate,
        priceCalculate
    ),
    createOrder,
    notify
)
```

通过该模型可以验证：

- 串行执行
- 并行执行
- 条件执行
- 规则版本变化
- 灰度发布
- 回滚

---

# 6. 数据模型设计

## 6.1 rule_definition

用于表示逻辑规则本身。

```text
rule_definition
--------------------------------
id
rule_code
rule_name
current_version
status
created_at
updated_at
```

示例：

```text
rule_code = ORDER_FLOW
current_version = 3
status = ENABLED
```

---

## 6.2 rule_version

存储规则每个历史版本。

```text
rule_version
--------------------------------
id
rule_id
version
rule_content
status
checksum
created_by
created_at
published_at
```

状态：

```text
DRAFT
PUBLISHED
ARCHIVED
```

例如：

```text
ORDER_FLOW
├── V1 ARCHIVED
├── V2 PUBLISHED
└── V3 DRAFT
```

灰度期间允许稳定版本与灰度版本同时处于 `PUBLISHED`：

```text
V1 PUBLISHED（由 rule_definition.current_version 指定为稳定版本）
V2 PUBLISHED（由 rule_gray_policy 指定为灰度版本）
```

因此不能用“一个规则只能有一个 PUBLISHED 版本”判断当前稳定版本；版本角色必须由
`rule_definition.current_version` 与活动灰度策略共同确定。

---

## 6.3 rule_gray_policy

保存灰度策略。

```text
rule_gray_policy
--------------------------------
id
rule_id
base_version
gray_version
percentage
status
created_at
updated_at
```

示例：

```text
ORDER_FLOW
baseVersion = 1
grayVersion = 2
percentage = 10
```

表示：

```text
90% → V1
10% → V2
```

---

## 6.4 flow_execution

表示一次完整流程执行。

```text
flow_execution
--------------------------------
execution_id
rule_code
rule_version
routing_key
start_time
end_time
duration_ms
status
error_code
error_message
```

---

## 6.5 flow_execution_node

记录节点级执行信息。

```text
flow_execution_node
--------------------------------
id
execution_id
node_id
start_time
end_time
duration_ms
status
error_message
```

最终可以得到：

```text
Execution #EX001
ORDER_FLOW V2

userCheck        SUCCESS     10ms
riskCheck        SUCCESS     32ms
stockCheck       SUCCESS     18ms
priceCalculate   SUCCESS     12ms
createOrder      SUCCESS     26ms
notify           SUCCESS      8ms
```

---

# 7. 核心模块设计

## 7.1 RuleEngine

为避免业务层直接依赖 LiteFlow API，引入统一执行引擎抽象。

```java
public interface RuleEngine {

    ExecutionResult execute(
        RuleSnapshot snapshot,
        ExecutionContext context
    );

}
```

LiteFlow 对应实现：

```text
LiteFlowRuleEngine
```

好处：

- 隔离底层框架
- 降低系统耦合
- 方便单元测试
- 后续理论上可以替换执行引擎

---

# 8. RuleSnapshot 设计

RuleSnapshot 是系统最核心的数据结构之一。

定义：

> 某次业务请求真正绑定并执行的不可变规则版本。

示例：

```java
public record RuleSnapshot(
    Long ruleId,
    String ruleCode,
    Integer version,
    String ruleContent,
    String checksum
) {}
```

请求进入后：

```text
Request
   ↓
RuleResolver
   ↓
RuleSnapshot V2
   ↓
LiteFlowRuleEngine
```

一旦请求获取 Snapshot，整个执行周期内不再重新查询当前版本。

这样可以保证：

```text
T1 获取 V1
↓
管理员发布 V2
↓
T1 仍然完整执行 V1

T2 新进入
↓
获取 V2
↓
完整执行 V2
```

避免出现：

```text
V1 前半段
+
V2 后半段
```

这种规则污染问题。

---

# 9. 平滑规则切换

## 9.1 问题场景

当前规则：

```text
V1:
A → B → C
```

线程 T1 已执行：

```text
A → B
```

此时管理员发布：

```text
V2:
A → D → C
```

如果执行过程中动态读取最新版本，则可能导致：

```text
A → B → D → C
```

形成混合执行链。

---

## 9.2 解决方案

版本选择只发生一次：

```text
Request
   ↓
RuleResolver
   ↓
固定 version
   ↓
固定 RuleSnapshot
   ↓
完整执行
```

核心原则：

> Resolve once, execute consistently.

在途请求不参与后续版本切换。

这里不仅要求 Java 层固定 `RuleSnapshot`，还要求 LiteFlow 运行时按版本隔离 Chain。
已加载 Chain 的标识至少包含 `ruleCode + version`，不能在发布时用同一个 `ruleCode`
覆盖旧 Chain。否则即使请求持有 V1 Snapshot，也可能执行被替换后的 V2 Chain。

---

# 10. 规则版本管理

规则生命周期：

```text
DRAFT
  ↓
PUBLISHED
  ↓
ARCHIVED
```

创建新版本：

```text
V1 PUBLISHED
↓
创建 V2
↓
V2 DRAFT
```

发布：

```text
V1 PUBLISHED → ARCHIVED
V2 DRAFT → PUBLISHED
```

currentVersion：

```text
1 → 2
```

灰度不是普通全量发布。开启灰度时：

```text
V1 PUBLISHED（稳定版本，currentVersion = 1）
V2 DRAFT → PUBLISHED（灰度版本）
currentVersion 保持 1
```

灰度转正时才将 `currentVersion` 从 V1 切换到 V2，并归档 V1、停用灰度策略。
停止灰度但不转正时，流量全部回到 V1，V2 从 `PUBLISHED` 转为 `ARCHIVED`。

---

# 11. 规则回滚

回滚并不删除新版本。

例如：

```text
V1 ARCHIVED
V2 PUBLISHED
```

发现 V2 存在问题：

```text
rollback V1
```

最终：

```text
V1 PUBLISHED
V2 ARCHIVED
```

currentVersion：

```text
2 → 1
```

保留所有历史版本。

这样便于：

- 审计
- 问题追踪
- 二次发布
- 版本 Diff

---

# 12. 灰度发布设计

直接全量发布新规则风险较大，因此引入灰度。

示例：

```text
V1：线上稳定规则
V2：新规则
```

初始：

```text
V1 90%
V2 10%
```

随后：

```text
10%
↓
30%
↓
50%
↓
promote
```

活动灰度比例统一限定为 `1~99`。`0%` 使用“停止灰度”表达，`100%` 使用
“灰度转正”表达，避免比例与生命周期状态产生重复语义。

---

## 12.1 灰度算法

不使用 Random。

采用：

```text
hash(routingKey) % 100
```

例如：

```java
int bucket = Math.floorMod(routingKey.hashCode(), 100);

if (bucket < percentage) {
    return grayVersion;
}

return baseVersion;
```

这样可以保证：

```text
同一个 routingKey
↓
固定 bucket
↓
固定规则版本
```

避免：

```text
请求1 → V1
请求2 → V2
请求3 → V1
```

导致用户体验和灰度统计不稳定。

---

# 13. RuleResolver

RuleResolver 负责确定：

> 当前请求到底执行哪个规则版本。

核心过程：

```text
ruleCode + routingKey
       ↓
查询 GrayPolicy
       ↓
存在灰度？
 ┌─────┴─────┐
 ↓           ↓
YES          NO
 ↓           ↓
Hash路由    currentVersion
 ↓           ↓
version
       ↓
RuleCache
       ↓
RuleSnapshot
```

接口示例：

```java
public interface RuleResolver {

    RuleSnapshot resolve(
        String ruleCode,
        String routingKey
    );

}
```

---

# 14. 规则缓存设计

如果每个请求都执行：

```text
Request
 ↓
MySQL
 ↓
查询规则
 ↓
解析
 ↓
执行
```

会形成不必要的数据库和解析开销。

因此引入：

```text
Caffeine Local Cache
```

缓存结构：

```text
Key:
ORDER_FLOW:V2

Value:
RuleSnapshot
```

查询流程：

```text
Request
 ↓
RuleResolver
 ↓
Cache Hit?
 ├─ YES → RuleSnapshot
 │
 └─ NO
     ↓
    MySQL
     ↓
 RuleSnapshot
     ↓
 Cache Put
```

---

# 15. 缓存失效策略

规则发布成功后：

```text
更新 DB
↓
更新 currentVersion
↓
Invalidate Cache
↓
加载新 Snapshot
```

第一阶段采用单节点模式，因此可以直接操作本地缓存。

未来多实例部署时可以扩展：

```text
Redis Pub/Sub
Redis Stream
RocketMQ
```

通知所有节点失效。

---

# 16. 多节点演进设计

未来部署：

```text
              Load Balancer
                   │
       ┌───────────┼───────────┐
       ↓           ↓           ↓
    Node A      Node B      Node C
```

规则发布：

```text
Admin
 ↓
MySQL
 ↓
Publish Event
 ↓
Redis Stream / MQ
 ↓
A / B / C
 ↓
Invalidate Cache
```

同时增加版本号校验兜底：

```text
localVersion != currentVersion
↓
reload
```

避免消息丢失导致节点长期不一致。

---

# 17. 规则校验机制

规则不能直接从 DRAFT 切换为 PUBLISHED。

发布前必须执行：

```text
Rule Content
    ↓
Syntax Check
    ↓
Component Exists Check
    ↓
Load Test
    ↓
Publish
```

例如：

```text
THEN(userCheck, unknownComponent, createOrder)
```

如果：

```text
unknownComponent
```

没有对应 Spring Component，则禁止发布。

---

# 18. 并发发布控制

考虑两个管理员同时发布相同版本。

错误情况：

```text
T1 发布 V2
T2 发布 V2
```

都认为发布成功。

通过数据库状态 CAS：

```sql
UPDATE rule_version
SET status = 'PUBLISHED'
WHERE id = ?
AND status = 'DRAFT';
```

只有：

```text
affectedRows == 1
```

的线程获得状态修改权。

其他线程得到：

```text
affectedRows == 0
```

说明状态已经变化。

进一步可在事务中处理：

```text
旧版本归档
+
新版本发布
+
currentVersion 更新
```

保证数据库状态一致性。

`rule_definition.current_version` 初始为 `NULL`。首次发布不能使用
`current_version = NULL`，必须使用 `current_version IS NULL` 的条件更新；后续发布再使用
`current_version = expectedVersion`。两种路径都必须检查 `affectedRows == 1`。

---

# 19. RulePublisher

RulePublisher 负责规则发布生命周期。

职责：

```text
Validate
↓
State Check
↓
Persist
↓
Load Rule
↓
Switch Version
↓
Refresh Cache
```

避免把发布逻辑散落在 Controller / Service 中。

---

# 20. 执行链路追踪

每次请求生成：

```text
executionId
```

例如：

```text
UUID
```

执行前：

```text
execution → RUNNING
```

执行成功：

```text
SUCCESS
```

执行异常：

```text
FAILED
```

同时节点记录：

```text
node start
↓
process
↓
node end
```

采集：

- 节点名称
- 开始时间
- 结束时间
- 耗时
- 状态
- 异常

为后续：

```text
规则调试
性能分析
Bad Case 定位
```

提供基础数据。

---

# 21. 异常处理机制

系统统一处理以下异常：

```text
RuleNotFoundException

RuleVersionNotFoundException

IllegalRuleStateException

RuleValidationException

RuleLoadException

RuleExecutionException
```

错误响应结构：

```json
{
    "code": "RULE_EXECUTION_FAILED",
    "message": "rule execution failed",
    "executionId": "EX001"
}
```

执行失败后，可以通过 executionId 查询详细节点。

---

# 22. 幂等设计

重点保护以下操作：

```text
重复发布
重复回滚
重复状态切换
```

核心思想是：

> 不依赖 Controller 判断，而依赖数据库状态约束。

例如：

```sql
UPDATE rule_version
SET status = 'ARCHIVED'
WHERE id = ?
AND status = 'PUBLISHED';
```

状态不符合预期：

```text
affectedRows = 0
```

则拒绝执行。

---

# 23. 测试方案

## 23.1 LiteFlow 基础执行测试

验证：

```text
THEN(A,B,C)
```

实际顺序：

```text
A → B → C
```

---

## 23.2 规则版本测试

验证：

```text
V1 → publish
V2 → create
V2 → publish
V1 → archived
```

---

## 23.3 回滚测试

```text
V2 Published
↓
Rollback V1
```

验证：

```text
currentVersion == V1
```

---

# 24. 平滑切换并发测试

核心测试场景：

```text
T1
↓
Resolve V1
↓
执行 A
↓
等待

Main Thread
↓
Publish V2

T2
↓
Resolve V2
↓
执行

释放 T1
```

最终：

```text
T1 == V1
T2 == V2
```

用于证明：

> 在途请求不受规则发布影响。

---

# 25. 灰度稳定性测试

同一个 routingKey：

```text
请求 100 次
```

要求：

```text
全部命中相同版本
```

大量用户：

```text
10000 users
```

设置：

```text
percentage = 10
```

统计实际灰度用户比例。

目标：

```text
约 10%
```

允许一定 Hash 分布误差。

---

# 26. 并发发布测试

使用：

```text
CountDownLatch
ExecutorService
```

模拟多个线程同时发布。

验证：

```text
最终只有一个合法状态迁移成功
```

数据库：

```text
currentVersion
```

保持唯一确定值。

---

# 27. 性能测试

重点对比：

```text
No Cache
vs
Caffeine Cache
```

测试指标：

```text
QPS
P50
P95
P99
DB Query Count
```

测试场景：

```text
固定 ORDER_FLOW
并发执行
```

压测工具可选择：

```text
JMeter
wrk
```

压测结果必须使用真实测试数据，不提前设定。

---

# 28. 项目目录建议

```text
com.flowpilot
├── FlowPilotApplication.java
├── controller
├── application
├── domain
│   ├── rule
│   │   ├── model
│   │   ├── service
│   │   └── repository
│   ├── gray
│   │   ├── model
│   │   ├── service
│   │   └── repository
│   └── execution
│       ├── model
│       ├── service
│       └── repository
├── engine
├── cache
├── component
├── infrastructure
│   ├── persistence
│   ├── config
│   └── util
├── dto
└── exception
```

具体类名和子目录以《FlowPilot 数据库表、包结构与核心接口定义》中的完整目录树为准。

---

# 29. 两周开发路线

## 第一阶段：基础执行

Day 1：

```text
LiteFlow
Component
Chain
Context
FlowExecutor
```

Day 2：

```text
Spring Boot
MySQL
Caffeine
项目骨架
数据表
```

Redis 不作为第一阶段核心链路依赖，仅在后续多实例缓存同步确有需要时接入。

Day 3：

```text
规则 CRUD
版本模型
```

Day 4：

```text
DB Rule
↓
LiteFlow
↓
动态执行
```

---

## 第二阶段：规则治理

Day 5：

```text
规则校验
发布
```

Day 6：

```text
版本切换
回滚
```

Day 7：

```text
RuleSnapshot
Caffeine Cache
```

Day 8：

```text
平滑切换
并发验证
```

---

## 第三阶段：增强

Day 9：

```text
灰度路由
```

Day 10：

```text
Execution Trace
```

Day 11：

```text
异常
状态 CAS
幂等
```

Day 12：

```text
JUnit
并发测试
```

Day 13：

```text
Docker Compose
压测
```

Day 14：

```text
README
架构图
简历
项目复盘
```

---

# 30. MVP 完成标准

如果时间紧张，最低必须完成：

```text
LiteFlow 动态执行
+
规则版本
+
发布/回滚
+
RuleSnapshot
+
平滑切换
+
灰度路由
```

只要这部分完成，项目技术主线已经成立。

以下属于增强：

```text
Execution Trace
Cache
Docker
压测
```

---

# 31. 第一阶段明确不实现

为了控制项目周期，以下功能暂不实现：

```text
可视化拖拽编排

完整 RBAC

多租户

Kubernetes

分布式配置中心

RocketMQ

复杂脚本沙箱

AI 自动生成规则

Prometheus + Grafana

复杂规则 Diff
```

避免项目从：

```text
规则治理平台
```

膨胀成：

```text
低代码 BPM 平台
```

---

# 32. 主要技术亮点

项目最终应形成四个主要技术亮点。

## 亮点一：业务逻辑与流程解耦

基于 LiteFlow：

```text
Component
+
EL Chain
```

把稳定业务能力与频繁变化的业务流程分离。

---

## 亮点二：版本快照和平滑热切换

基于：

```text
Immutable RuleSnapshot
```

在请求入口固定规则版本。

实现：

```text
旧请求 → 旧版本执行完成
新请求 → 新版本
```

避免运行过程中规则污染。

---

## 亮点三：稳定灰度路由

基于：

```text
routingKey Hash
```

实现稳定分桶。

解决 Random 路由下：

```text
同一用户多次请求命中不同版本
```

的问题。

---

## 亮点四：并发状态迁移

通过：

```text
数据库条件 UPDATE
+
事务
```

实现发布、回滚状态 CAS。

减少多个管理员并发操作导致的非法状态。

---

# 33. 项目可扩展方向

后续如果继续维护，可逐步加入：

## 多实例规则同步

```text
Redis Stream / RocketMQ
```

---

## 规则 Diff

展示：

```text
V1:
THEN(A,B,C)

V2:
THEN(A,D,C)
```

变化：

```text
-B
+D
```

---

## 发布审批

```text
DRAFT
↓
REVIEW
↓
PUBLISHED
```

---

## 自动回滚

根据：

```text
异常率
P99
规则失败率
```

超过阈值：

```text
Gray V2
↓
自动关闭
↓
V1
```

---

## Prometheus

增加：

```text
rule_execution_total

rule_execution_failed_total

rule_execution_duration

node_execution_duration
```

---

# 34. 项目风险

## LiteFlow 学习成本

风险：

```text
前期花过多时间研究源码
```

处理方式：

第一阶段只理解：

```text
Component
Chain
Context
FlowExecutor
动态 Rule
```

不要从源码底层开始阅读。

---

## 功能膨胀

风险：

看到成熟规则平台后不断增加：

```text
权限
审批
前端
脚本
监控
多租户
```

处理方式：

严格围绕：

```text
版本
灰度
平滑切换
执行追踪
```

开发。

---

## 开源能力与自研能力边界不清

面试时必须明确：

LiteFlow 提供：

```text
组件执行与规则编排
```

FlowPilot 提供：

```text
规则生命周期治理
```

---

# 35. 面试介绍口径

项目介绍建议控制在 1 分钟左右：

“这个项目是一个基于 LiteFlow 的动态业务规则编排和灰度发布平台。主要解决的是传统业务流程硬编码在 Service 中，流程修改需要重新发版的问题。

底层我使用 LiteFlow 负责 Component 和 Chain 的实际执行，在上层自己实现了规则版本管理、发布回滚和灰度治理。

比较核心的设计是 RuleSnapshot。每个请求进入时先解析并固定一个具体规则版本，后续整个执行过程都使用这个 Snapshot，所以即使运行过程中发布了新规则，在途请求也不会受到影响，新请求才会进入新版本。

另外灰度发布通过 routingKey Hash 做稳定分桶，保证同一灰度主体持续命中同一规则版本；发布和回滚则使用数据库条件更新控制并发状态迁移。

最后还记录了 executionId 和节点级执行耗时，用于规则执行问题定位和性能分析。”

---

# 36. 简历描述参考

**FlowPilot 动态业务规则编排与灰度发布平台**

技术栈：

```text
Java 21、Spring Boot、LiteFlow、MySQL、Redis、Caffeine、JUnit5、Docker
```

项目描述：

基于 LiteFlow 构建动态业务规则编排平台，将业务 Component 与流程定义解耦，支持规则运行时发布与切换。

设计规则版本模型及发布/回滚状态流转，通过不可变 RuleSnapshot 在请求入口固定执行版本，保证规则热切换期间在途请求完整执行原版本，避免执行链出现新旧规则混用。

实现基于 routingKey Hash 的稳定灰度路由，支持指定比例流量进入新版本，并基于数据库条件更新控制并发发布与状态迁移。

引入本地规则缓存降低数据库访问与规则加载开销，同时记录 executionId、节点执行状态及耗时，支持执行链路追踪及异常定位。

---

# 37. 最终技术定位

FlowPilot 不应被描述为：

```text
LiteFlow Demo
```

也不应该描述为：

```text
自己实现了规则引擎
```

准确定位应该是：

> 基于 LiteFlow 执行引擎构建的动态业务规则治理平台。

核心关系：

```text
              FlowPilot
                  │
      ┌───────────┼────────────┐
      ↓           ↓            ↓
   Version       Gray       Execution
      │           │            │
      └───────────┼────────────┘
                  ↓
             RuleSnapshot
                  ↓
               LiteFlow
                  ↓
              Component
```

项目真正需要体现的能力不是功能数量，而是：

```text
规则版本治理
运行时一致性
灰度发布
并发控制
缓存设计
执行追踪
```

这几个模块完成以后，该项目已经能够作为一个完整的 Java 后端校招项目使用。
