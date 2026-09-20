# FlowPilot

FlowPilot 是一个基于 Java 21、Spring Boot、LiteFlow 和 MySQL 的动态规则编排与灰度发布平台。
它解决的不是“如何写一个规则表达式”，而是规则进入生产环境后如何安全地创建版本、校验、发布、
灰度、回滚、平滑切换和追踪执行现场。

## 核心能力

- 动态编排：支持 LiteFlow 串行、并行等 EL 规则，规则内容存储在数据库中。
- 不可变执行快照：一次请求只解析一次 `RuleSnapshot`，执行过程中不会重新读取当前版本。
- 版本化 Chain：Chain ID 使用 `ruleCode + version`，新版本发布不会覆盖在途请求使用的旧 Chain。
- 安全发布：规则预校验与预加载在事务前完成，状态迁移通过行锁、预期状态和 CAS 更新保护。
- 稳定灰度：根据 `routingKey` 做确定性哈希，同一个用户持续命中同一版本。
- 完整生命周期：支持首次发布、升级发布、回滚、开启灰度、调整比例、停止灰度和灰度转正。
- 执行追踪：记录执行版本、状态、耗时、错误，以及每个节点的状态、耗时和异常。
- 并发保障：自动化测试覆盖并发发布和平滑切换，在途 V1 与新请求 V2 不会串版本。

## 架构

```mermaid
flowchart LR
    API[REST API] --> APP[Application Service]
    APP --> RESOLVE[RuleResolver]
    RESOLVE --> GRAY[GrayRouter]
    RESOLVE --> CACHE[Caffeine Cache]
    RESOLVE --> DB[(MySQL)]
    APP --> SNAPSHOT[Immutable RuleSnapshot]
    SNAPSHOT --> ENGINE[LiteFlowRuleEngine]
    ENGINE --> CHAIN[Versioned Chain]
    CHAIN --> NODE[Business Components]
    APP --> TRACE[Execution Trace]
    NODE --> TRACE
    TRACE --> DB
```

一次执行的关键路径：

```text
executionId → resolve once → fixed RuleSnapshot → versioned Chain → components → trace
```

## 技术栈

- Java 21
- Spring Boot 3.5
- LiteFlow 2.16
- Spring JDBC
- MySQL 8 / H2（测试）
- Caffeine
- JUnit 5、AssertJ、MockMvc

## 快速启动

前置要求：JDK 21、Docker（或本地 MySQL）。

### 1. 启动 MySQL

```bash
docker compose up -d
```

首次创建容器时，`src/main/resources/db/schema.sql` 会自动初始化表结构。

### 2. 配置数据库并启动应用

PowerShell：

```powershell
$env:FLOWPILOT_DB_URL="jdbc:mysql://localhost:3306/flowpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:FLOWPILOT_DB_USERNAME="root"
$env:FLOWPILOT_DB_PASSWORD="flowpilot"
.\mvnw.cmd spring-boot:run
```

应用默认监听 `http://localhost:8080`。

### 3. 跑通真实链路

创建规则：

```bash
curl -X POST http://localhost:8080/api/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleCode":"ORDER_FLOW","ruleName":"订单流程","description":"订单风控与创建"}'
```

创建 V1：

```bash
curl -X POST http://localhost:8080/api/rules/ORDER_FLOW/versions \
  -H "Content-Type: application/json" \
  -d '{"ruleContent":"THEN(userCheck,stockCheck,createOrder)","createdBy":"demo"}'
```

发布 V1：

```bash
curl -X POST http://localhost:8080/api/rules/ORDER_FLOW/versions/1/publish
```

执行规则：

```bash
curl -X POST http://localhost:8080/api/flows/ORDER_FLOW/execute \
  -H "Content-Type: application/json" \
  -d '{"routingKey":"customer-10001","variables":{"userValid":true,"stockAvailable":true}}'
```

响应中的 `executionId` 可用于查询完整链路：

```bash
curl http://localhost:8080/api/executions/{executionId}
```

## API 一览

| 方法 | 路径 | 作用 |
|---|---|---|
| `POST` | `/api/rules` | 创建规则 |
| `GET` | `/api/rules/{ruleCode}` | 查询规则 |
| `POST` | `/api/rules/{ruleCode}/versions` | 创建草稿版本 |
| `GET` | `/api/rules/{ruleCode}/versions` | 查询版本历史 |
| `POST` | `/api/rules/{ruleCode}/versions/{version}/publish` | 发布版本 |
| `POST` | `/api/rules/{ruleCode}/rollback` | 回滚版本 |
| `POST` | `/api/rules/{ruleCode}/gray` | 开启灰度 |
| `PUT` | `/api/rules/{ruleCode}/gray` | 调整灰度比例 |
| `DELETE` | `/api/rules/{ruleCode}/gray` | 停止灰度 |
| `POST` | `/api/rules/{ruleCode}/gray/promote` | 灰度转正 |
| `GET` | `/api/rules/{ruleCode}/gray` | 查询活动灰度策略 |
| `POST` | `/api/flows/{ruleCode}/execute` | 执行规则 |
| `GET` | `/api/executions/{executionId}` | 查询执行与节点明细 |

活动灰度比例只接受 `1~99`。`0` 使用停止灰度接口，`100` 使用灰度转正接口，避免状态语义含糊。

## 测试

```powershell
.\mvnw.cmd clean test
```

当前测试覆盖：领域约束、缓存、稳定哈希路由、LiteFlow 串行/并行执行、发布与回滚、灰度生命周期、
并发发布、HTTP 完整链路、在途请求平滑切换、成功与失败执行追踪。

## 关键设计取舍

1. 发布切换的是数据库中的稳定版本指针，不修改已生成的执行快照。
2. 每个规则版本对应独立 LiteFlow Chain，避免热更新覆盖旧 Chain。
3. 发布、回滚和灰度迁移先锁定规则定义行，再执行状态 CAS，所有受影响行数必须为 1。
4. 规则语法、组件存在性和 Chain 加载在事务前验证，缩短数据库锁持有时间。
5. 第一版按单实例设计，本地缓存由生命周期服务精确失效；多实例缓存广播不在当前范围内。

## 项目边界

当前版本已经形成可运行、可测试的后端闭环，但仍是第一版工程实现：未包含认证授权、管理后台、
多实例缓存一致性、限流、指标监控和生产级容灾。这些能力应按真实部署场景继续演进，而不是在 Demo 中
虚构“生产可用”。

更完整的设计说明见：

- [技术升级报告](./FlowPilot%20动态规则编排与灰度发布平台技术升级报告.md)
- [数据库、包结构与核心接口定义](./FlowPilot%20数据库表、包结构与核心接口定义.md)
