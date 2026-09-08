# FlowPilot 数据库表、包结构与核心接口定义

## 1. 数据库表设计

第一版建议控制在 **5 张核心表**：

```text
rule_definition
rule_version
rule_gray_policy
flow_execution
flow_execution_node
```

其中：

```text
rule_definition
    1
    │
    N
rule_version
```

执行部分：

```text
flow_execution
    1
    │
    N
flow_execution_node
```

灰度：

```text
rule_definition
    1
    │
    0..1
rule_gray_policy
```

---

# 2. rule_definition

表示一个“逻辑规则”。

例如：

```text
ORDER_FLOW
PAYMENT_RISK_FLOW
REFUND_FLOW
```

表：

```sql
CREATE TABLE rule_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,

    current_version INT DEFAULT NULL,

    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',

    description VARCHAR(512) DEFAULT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_rule_code (rule_code)
);
```

字段解释：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| rule_code | 规则唯一编码 |
| rule_name | 展示名称 |
| current_version | 当前全量版本 |
| status | ENABLED / DISABLED |
| description | 描述 |
| created_at | 创建时间 |
| updated_at | 更新时间 |

注意：

> `current_version` 表示当前稳定全量版本，不等于灰度版本。

例如：

```text
currentVersion = 3
grayVersion = 4
grayPercentage = 10
```

表示：

```text
90% → V3
10% → V4
```

---

# 3. rule_version

保存规则的实际版本。

```sql
CREATE TABLE rule_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    rule_id BIGINT NOT NULL,
    version INT NOT NULL,

    rule_content TEXT NOT NULL,

    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',

    checksum VARCHAR(64) DEFAULT NULL,

    created_by VARCHAR(64) DEFAULT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME DEFAULT NULL,

    UNIQUE KEY uk_rule_version (rule_id, version),
    KEY idx_rule_status (rule_id, status)
);
```

状态：

```text
DRAFT
PUBLISHED
ARCHIVED
```

典型情况：

```text
ORDER_FLOW

V1 ARCHIVED
V2 ARCHIVED
V3 PUBLISHED
V4 DRAFT
```

灰度期间：

```text
V3 PUBLISHED
V4 PUBLISHED
```

这里需要注意一个设计。

第一版中建议允许：

> 一个稳定版本 + 一个灰度版本同时处于 PUBLISHED。

所以不要做数据库约束：

```text
一个 rule 只能存在一个 PUBLISHED
```

真正哪个是：

```text
base version
gray version
```

由：

```text
rule_definition.current_version
+
rule_gray_policy
```

决定。

---

## 3.1 checksum

建议加：

```text
SHA-256(rule_content)
```

主要用途：

```text
规则内容一致性判断
缓存更新判断
未来多节点校验
```

例如：

```java
checksum = sha256(ruleContent);
```

---

# 4. rule_gray_policy

灰度策略。

```sql
CREATE TABLE rule_gray_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    rule_id BIGINT NOT NULL,

    base_version INT NOT NULL,
    gray_version INT NOT NULL,

    percentage INT NOT NULL,

    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_gray_rule (rule_id)
);
```

约束由 Service 校验：

```text
1 <= percentage <= 99

baseVersion != grayVersion
```

`0%` 统一通过“停止灰度”表达，`100%` 统一通过“灰度转正”表达，不作为活动灰度策略保存。

例如：

```text
ORDER_FLOW

base_version = 3
gray_version = 4
percentage = 10
```

路由：

```text
bucket 0~9   → V4
bucket 10~99 → V3
```

---

# 5. flow_execution

记录一次完整规则执行。

```sql
CREATE TABLE flow_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    execution_id VARCHAR(64) NOT NULL,

    rule_code VARCHAR(64) NOT NULL,
    rule_version INT NOT NULL,

    routing_key VARCHAR(128) DEFAULT NULL,

    status VARCHAR(32) NOT NULL,

    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) DEFAULT NULL,

    duration_ms BIGINT DEFAULT NULL,

    error_code VARCHAR(64) DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_execution_id (execution_id),

    KEY idx_rule_execution (
        rule_code,
        rule_version,
        created_at
    ),

    KEY idx_routing_execution (
        routing_key,
        created_at
    )
);
```

状态：

```text
RUNNING
SUCCESS
FAILED
```

示例：

```text
executionId = 550e8400...

ruleCode = ORDER_FLOW
ruleVersion = 4
routingKey = 10001
status = SUCCESS
durationMs = 83
```

---

# 6. flow_execution_node

节点级执行记录。

```sql
CREATE TABLE flow_execution_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    execution_id VARCHAR(64) NOT NULL,

    node_id VARCHAR(64) NOT NULL,

    status VARCHAR(32) NOT NULL,

    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) DEFAULT NULL,

    duration_ms BIGINT DEFAULT NULL,

    error_message VARCHAR(1000) DEFAULT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY idx_execution_node (
        execution_id,
        id
    )
);
```

状态：

```text
RUNNING
SUCCESS
FAILED
SKIPPED
```

例如：

```text
EX001

userCheck       SUCCESS  9ms
riskCheck       SUCCESS  31ms
stockCheck      SUCCESS  16ms
createOrder     FAILED   26ms
```

---

# 7. 第一版不要额外建的表

暂时不要：

```text
rule_publish_log
rule_audit_log
rule_component
rule_permission
rule_environment
rule_tag
```

因为这些会导致范围膨胀。

如果后续需要发布记录，可以再增加：

```text
rule_operation_log
```

第一版直接依赖：

```text
rule_version.created_at
published_at
created_by
```

即可。

---

# 8. Java 包结构

推荐：

```text
com.flowpilot
│
├── FlowPilotApplication.java
│
├── controller
│   ├── RuleController.java
│   ├── RuleVersionController.java
│   ├── GrayPolicyController.java
│   ├── FlowExecutionController.java
│   └── ExecutionQueryController.java
│
├── application
│   ├── RuleApplicationService.java
│   ├── PublishApplicationService.java
│   ├── GrayApplicationService.java
│   └── FlowExecutionApplicationService.java
│
├── domain
│   ├── rule
│   │   ├── model
│   │   │   ├── RuleDefinition.java
│   │   │   ├── RuleVersion.java
│   │   │   ├── RuleSnapshot.java
│   │   │   ├── RuleStatus.java
│   │   │   └── RuleVersionStatus.java
│   │   │
│   │   ├── service
│   │   │   ├── RuleResolver.java
│   │   │   ├── RulePublisher.java
│   │   │   └── RuleValidator.java
│   │   │
│   │   └── repository
│   │       ├── RuleDefinitionRepository.java
│   │       └── RuleVersionRepository.java
│   │
│   ├── gray
│   │   ├── model
│   │   │   └── GrayPolicy.java
│   │   ├── service
│   │   │   └── GrayRouter.java
│   │   └── repository
│   │       └── GrayPolicyRepository.java
│   │
│   └── execution
│       ├── model
│       │   ├── FlowExecution.java
│       │   ├── NodeExecution.java
│       │   ├── ExecutionStatus.java
│       │   └── NodeExecutionStatus.java
│       │
│       ├── service
│       │   └── ExecutionTraceService.java
│       │
│       └── repository
│           ├── FlowExecutionRepository.java
│           └── NodeExecutionRepository.java
│
├── engine
│   ├── RuleEngine.java
│   ├── LiteFlowRuleEngine.java
│   ├── LiteFlowRuleLoader.java
│   └── LiteFlowComponentRegistry.java
│
├── cache
│   ├── RuleCache.java
│   └── CaffeineRuleCache.java
│
├── component
│   ├── UserCheckComponent.java
│   ├── RiskCheckComponent.java
│   ├── StockCheckComponent.java
│   ├── VipCheckComponent.java
│   ├── PriceCalculateComponent.java
│   ├── CreateOrderComponent.java
│   └── NotifyComponent.java
│
├── infrastructure
│   ├── persistence
│   │   ├── mapper
│   │   ├── entity
│   │   └── repository
│   │
│   ├── config
│   │   ├── LiteFlowConfig.java
│   │   ├── CacheConfig.java
│   │   └── JacksonConfig.java
│   │
│   └── util
│       ├── HashUtils.java
│       └── ChecksumUtils.java
│
├── dto
│   ├── request
│   └── response
│
└── exception
    ├── GlobalExceptionHandler.java
    ├── RuleNotFoundException.java
    ├── RuleVersionNotFoundException.java
    ├── RuleValidationException.java
    ├── IllegalRuleStateException.java
    └── RuleExecutionException.java
```

---

# 9. 为什么这么分包

不要使用这种：

```text
controller
service
mapper
entity
util
```

然后所有业务都堆在一起。

推荐：

```text
domain/rule
domain/gray
domain/execution
```

因为你的项目本身存在三个明确领域：

```text
规则生命周期
灰度路由
执行追踪
```

再通过：

```text
application
```

层组织业务用例。

这样不会搞成完整 DDD，但已经比普通三层架构清晰。

---

# 10. RuleSnapshot

核心对象。

```java
public record RuleSnapshot(
        Long ruleId,
        String ruleCode,
        Integer version,
        String ruleContent,
        String checksum
) {
}
```

必须遵循：

> Immutable。

不要给 setter。

执行过程中 Snapshot 不允许变化。

---

# 11. ExecutionContext

业务执行上下文。

第一版：

```java
public class ExecutionContext {

    private String executionId;

    private String userId;

    private Map<String, Object> variables;

}
```

如果订单 Demo 可以再增加：

```java
public class OrderExecutionContext {

    private String executionId;

    private String userId;

    private Long orderId;

    private BigDecimal amount;

    private boolean vip;

    private Map<String, Object> attributes;
}
```

但我更建议第一版：

```text
通用 ExecutionContext
```

业务数据统一放：

```text
variables
```

避免项目被订单业务绑死。

---

# 12. RuleEngine

底层规则引擎抽象。

```java
public interface RuleEngine {

    ExecutionResult execute(
            RuleSnapshot snapshot,
            ExecutionContext context
    );

}
```

返回：

```java
public record ExecutionResult(
        String executionId,
        String ruleCode,
        Integer version,
        boolean success,
        Object result
) {
}
```

实现：

```java
@Component
public class LiteFlowRuleEngine implements RuleEngine {

    @Override
    public ExecutionResult execute(
            RuleSnapshot snapshot,
            ExecutionContext context
    ) {
        // LiteFlow execution
    }
}
```

---

# 13. RuleResolver

这是请求链路最核心接口。

```java
public interface RuleResolver {

    RuleSnapshot resolve(
            String ruleCode,
            String routingKey
    );

}
```

这里不要直接叫：

```text
userId
```

建议：

```text
routingKey
```

原因：

以后可以按：

```text
userId
tenantId
deviceId
merchantId
```

灰度。

内部流程：

```text
RuleDefinition
      ↓
GrayPolicy
      ↓
GrayRouter
      ↓
version
      ↓
RuleCache
      ↓
RuleSnapshot
```

---

# 14. GrayRouter

专门负责灰度分桶。

```java
public interface GrayRouter {

    int route(
            String routingKey,
            int baseVersion,
            int grayVersion,
            int percentage
    );
}
```

实现：

```java
@Component
public class HashGrayRouter implements GrayRouter {

    @Override
    public int route(
            String routingKey,
            int baseVersion,
            int grayVersion,
            int percentage
    ) {

        int bucket =
                Math.floorMod(
                        routingKey.hashCode(),
                        100
                );

        return bucket < percentage
                ? grayVersion
                : baseVersion;
    }
}
```

后面可以升级：

```text
MurmurHash
xxHash
```

但第一版不用。

---

# 15. RuleCache

接口：

```java
public interface RuleCache {

    RuleSnapshot get(
            String ruleCode,
            int version
    );

    void put(RuleSnapshot snapshot);

    void evict(
            String ruleCode,
            int version
    );

    void evictAll(String ruleCode);
}
```

实现：

```text
CaffeineRuleCache
```

缓存 Key：

```java
public record RuleCacheKey(
        String ruleCode,
        Integer version
) {
}
```

不要把：

```text
currentVersion
```

缓存进去。

原因：

```text
version metadata
```

和：

```text
rule snapshot
```

职责不同。

第一版 `RuleResolver` 可以直接查询 `rule_definition.current_version`。

后面如果需要性能优化，再加：

```text
RuleMetadataCache
```

---

# 16. RuleValidator

发布前校验。

```java
public interface RuleValidator {

    ValidationResult validate(
            String ruleCode,
            String ruleContent
    );

}
```

返回：

```java
public record ValidationResult(
        boolean valid,
        List<String> errors
) {
}
```

校验至少包括：

```text
不能为空

LiteFlow EL是否合法

引用的 Component 是否存在
```

实现：

```text
LiteFlowRuleValidator
```

---

# 17. RulePublisher

规则发布核心接口。

```java
public interface RulePublisher {

    void publish(
            String ruleCode,
            int version
    );

    void rollback(
            String ruleCode,
            int targetVersion
    );
}
```

这个类不要负责：

```text
创建版本
删除规则
查询规则
```

只负责：

> 状态迁移 + 引擎加载 + 缓存处理。

---

# 18. PublishApplicationService

真正 Controller 调用这个。

```java
@Service
public class PublishApplicationService {

    private final RulePublisher rulePublisher;

    public void publish(
            String ruleCode,
            int version
    ) {
        rulePublisher.publish(
                ruleCode,
                version
        );
    }

    public void rollback(
            String ruleCode,
            int version
    ) {
        rulePublisher.rollback(
                ruleCode,
                version
        );
    }
}
```

为什么不让：

```text
Controller → RulePublisher
```

直接调用？

因为以后应用层还可能负责：

```text
权限
审计
事务编排
事件发送
```

---

# 19. Repository 接口

Domain 层只放接口。

## RuleDefinitionRepository

```java
public interface RuleDefinitionRepository {

    Optional<RuleDefinition> findByCode(
            String ruleCode
    );

    RuleDefinition save(
            RuleDefinition definition
    );

    boolean updateCurrentVersion(
            Long ruleId,
            Integer expectedVersion,
            Integer newVersion
    );
}
```

这里：

```text
expectedVersion
```

就是一个很重要的并发控制点。

SQL：

```sql
UPDATE rule_definition
SET current_version = #{newVersion}
WHERE id = #{ruleId}
AND current_version = #{expectedVersion}
```

相当于：

```text
CAS
```

首次发布时 `current_version` 为 `NULL`，不能生成 `current_version = NULL`。Repository
实现需要把首次发布作为明确分支，执行：

```sql
UPDATE rule_definition
SET current_version = #{newVersion}
WHERE id = #{ruleId}
AND current_version IS NULL
```

后续发布继续使用 `current_version = #{expectedVersion}`。两种更新都必须以
`affectedRows == 1` 作为成功条件。

---

# 20. RuleVersionRepository

```java
public interface RuleVersionRepository {

    Optional<RuleVersion> find(
            Long ruleId,
            Integer version
    );

    List<RuleVersion> findAll(
            Long ruleId
    );

    RuleVersion save(
            RuleVersion version
    );

    boolean changeStatus(
            Long ruleId,
            Integer version,
            RuleVersionStatus expectedStatus,
            RuleVersionStatus targetStatus
    );
}
```

---

# 21. GrayPolicyRepository

```java
public interface GrayPolicyRepository {

    Optional<GrayPolicy> findActiveByRuleId(
            Long ruleId
    );

    GrayPolicy save(
            GrayPolicy policy
    );

    void disable(Long ruleId);
}
```

---

# 22. FlowExecutionRepository

```java
public interface FlowExecutionRepository {

    void create(FlowExecution execution);

    void markSuccess(
            String executionId,
            long durationMs
    );

    void markFailed(
            String executionId,
            long durationMs,
            String errorMessage
    );

    Optional<FlowExecution> findByExecutionId(
            String executionId
    );
}
```

---

# 23. NodeExecutionRepository

```java
public interface NodeExecutionRepository {

    void create(NodeExecution execution);

    void markSuccess(
            Long nodeExecutionId,
            long durationMs
    );

    void markFailed(
            Long nodeExecutionId,
            long durationMs,
            String errorMessage
    );

    List<NodeExecution> findByExecutionId(
            String executionId
    );
}
```

---

# 24. ExecutionTraceService

封装执行日志。

```java
public interface ExecutionTraceService {

    void startExecution(
            String executionId,
            RuleSnapshot snapshot,
            String routingKey
    );

    void successExecution(
            String executionId,
            long durationMs
    );

    void failExecution(
            String executionId,
            long durationMs,
            Throwable throwable
    );

    Long startNode(
            String executionId,
            String nodeId
    );

    void successNode(
            Long nodeExecutionId,
            long durationMs
    );

    void failNode(
            Long nodeExecutionId,
            long durationMs,
            Throwable throwable
    );
}
```

Component 可以通过公共基类或 LiteFlow 生命周期 Hook 接入。

不要在每个 Component 都手写：

```java
trace.start();
try {
 ...
} finally {
 ...
}
```

后面统一处理。

---

# 25. RuleApplicationService

负责规则 CRUD 和版本创建。

```java
public interface RuleApplicationService {

    Long createRule(
            CreateRuleCommand command
    );

    Integer createVersion(
            String ruleCode,
            CreateRuleVersionCommand command
    );

    RuleDetailResponse getRule(
            String ruleCode
    );

    List<RuleVersionResponse> listVersions(
            String ruleCode
    );
}
```

---

# 26. GrayApplicationService

```java
public interface GrayApplicationService {

    void startGray(
            String ruleCode,
            StartGrayCommand command
    );

    void updatePercentage(
            String ruleCode,
            int percentage
    );

    void stopGray(
            String ruleCode
    );

    GrayPolicyResponse getPolicy(
            String ruleCode
    );
}
```

---

# 27. FlowExecutionApplicationService

```java
public interface FlowExecutionApplicationService {

    FlowExecuteResponse execute(
            String ruleCode,
            FlowExecuteCommand command
    );

    ExecutionDetailResponse queryExecution(
            String executionId
    );
}
```

核心逻辑：

```text
generate executionId

        ↓

RuleResolver.resolve()

        ↓

ExecutionTrace.start()

        ↓

RuleEngine.execute()

        ↓

SUCCESS / FAILED
```

---

# 28. Controller API

第一版控制在这些。

## 创建规则

```http
POST /api/rules
```

请求：

```json
{
  "ruleCode": "ORDER_FLOW",
  "ruleName": "订单处理流程",
  "description": "订单风控与创建流程"
}
```

---

## 创建版本

```http
POST /api/rules/{ruleCode}/versions
```

请求：

```json
{
  "ruleContent": "THEN(userCheck,stockCheck,createOrder)"
}
```

返回：

```json
{
  "version": 1
}
```

---

## 查询版本

```http
GET /api/rules/{ruleCode}/versions
```

---

# 29. 发布接口

```http
POST /api/rules/{ruleCode}/versions/{version}/publish
```

无 body。

---

# 30. 回滚接口

```http
POST /api/rules/{ruleCode}/rollback
```

请求：

```json
{
  "targetVersion": 1
}
```

---

# 31. 开启灰度

```http
POST /api/rules/{ruleCode}/gray
```

请求：

```json
{
  "grayVersion": 2,
  "percentage": 10
}
```

这里：

```text
baseVersion
```

不需要前端传。

直接：

```text
rule_definition.current_version
```

作为 baseVersion。

防止用户传错。

开启灰度时状态统一按以下规则迁移：

```text
baseVersion 保持 PUBLISHED
grayVersion: DRAFT → PUBLISHED
currentVersion 不变
创建 ACTIVE GrayPolicy
```

同一规则只能存在一个活动灰度策略。灰度版本不能等于当前稳定版本。

---

# 32. 修改灰度比例

```http
PUT /api/rules/{ruleCode}/gray
```

```json
{
  "percentage": 30
}
```

---

# 33. 停止灰度

```http
DELETE /api/rules/{ruleCode}/gray
```

停止后：

```text
100% 回到 currentVersion
grayVersion: PUBLISHED → ARCHIVED
GrayPolicy: ACTIVE → DISABLED
```

---

# 34. 全量灰度转正

建议额外提供：

```http
POST /api/rules/{ruleCode}/gray/promote
```

意思：

```text
grayVersion
↓
成为 currentVersion
```

例如：

```text
V3 base
V4 gray 50%

promote
↓

V4 current
gray disabled
```

转正后的统一状态为：

```text
原 baseVersion: PUBLISHED → ARCHIVED
grayVersion 保持 PUBLISHED，并成为 currentVersion
GrayPolicy: ACTIVE → DISABLED
```

这个接口很好。

---

# 35. 执行规则

```http
POST /api/flows/{ruleCode}/execute
```

请求：

```json
{
  "routingKey": "10001",
  "variables": {
    "amount": 299.00,
    "vip": true,
    "stock": 100
  }
}
```

返回：

```json
{
  "executionId": "EX-xxx",
  "ruleCode": "ORDER_FLOW",
  "ruleVersion": 2,
  "success": true,
  "result": {}
}
```

---

# 36. 查询执行记录

```http
GET /api/executions/{executionId}
```

返回：

```json
{
  "executionId": "EX001",
  "ruleCode": "ORDER_FLOW",
  "ruleVersion": 2,
  "status": "SUCCESS",
  "durationMs": 91,
  "nodes": [
    {
      "nodeId": "userCheck",
      "status": "SUCCESS",
      "durationMs": 9
    },
    {
      "nodeId": "riskCheck",
      "status": "SUCCESS",
      "durationMs": 31
    }
  ]
}
```

---

# 37. DTO 定义

## CreateRuleCommand

```java
public record CreateRuleCommand(

        @NotBlank
        String ruleCode,

        @NotBlank
        String ruleName,

        String description

) {
}
```

---

## CreateRuleVersionCommand

```java
public record CreateRuleVersionCommand(

        @NotBlank
        String ruleContent

) {
}
```

---

## StartGrayCommand

```java
public record StartGrayCommand(

        @NotNull
        Integer grayVersion,

        @Min(1)
        @Max(99)
        Integer percentage

) {
}
```

这里开启灰度建议：

```text
1~99
```

100% 应该使用：

```text
promote
```

而不是 gray 100。

语义更清晰。

---

# 38. FlowExecuteCommand

```java
public record FlowExecuteCommand(

        @NotBlank
        String routingKey,

        Map<String, Object> variables

) {
}
```

---

# 39. 核心执行调用链

最终：

```text
POST /flows/ORDER_FLOW/execute
              ↓
FlowExecutionController
              ↓
FlowExecutionApplicationService
              ↓
generate executionId
              ↓
RuleResolver.resolve()
              ↓
        GrayPolicy?
         /      \
       YES      NO
        ↓        ↓
    GrayRouter currentVersion
         \      /
          version
             ↓
         RuleCache
             ↓
       RuleSnapshot
             ↓
按 ruleCode + version 定位版本化 Chain
             ↓
   ExecutionTrace.start
             ↓
        RuleEngine
             ↓
    LiteFlowRuleEngine
             ↓
        Components
             ↓
       ExecutionTrace
             ↓
          Response
```

版本化 Chain 是平滑切换成立的必要条件。LiteFlow 侧不得只用 `ruleCode` 作为可覆盖的
全局 Chain ID；至少使用 `ruleCode + version`，并由传入的 `RuleSnapshot` 决定本次执行
哪个 Chain。发布新版本不能改变已经解析完成的在途请求所引用的旧 Chain。

---

# 40. 发布调用链

```text
POST /publish
     ↓
Controller
     ↓
PublishApplicationService
     ↓
RulePublisher
     ↓
RuleDefinition查询
     ↓
RuleVersion查询
     ↓
RuleValidator
     ↓
LiteFlow加载验证
     ↓
事务开始
     ↓
旧current版本 ARCHIVED
     ↓
目标版本 PUBLISHED
     ↓
CAS更新 currentVersion
     ↓
事务提交
     ↓
RuleCache刷新
```

这里要注意：

> LiteFlow Rule 加载最好在数据库事务之前完成预校验。

否则：

```text
事务长期持有
```

不合理。

---

# 41. 回滚调用链

```text
rollback V2 → V1
       ↓
检查 V1 是否存在
       ↓
Validate V1
       ↓
预加载
       ↓
事务
       ↓
V2 → ARCHIVED
V1 → PUBLISHED
currentVersion 2 → 1
       ↓
刷新 Cache
```

---

# 42. 灰度调用链

```text
start gray V4 10%
      ↓
currentVersion = V3
      ↓
检查 V4
      ↓
Validate V4
      ↓
V4 DRAFT → PUBLISHED（CAS）
      ↓
创建 GrayPolicy

base = V3
gray = V4
percentage = 10
```

灰度比例只允许 `1~99`。停止灰度负责回到 0% 的语义并归档灰度版本；Promote 负责
100% 的语义，将灰度版本切换为 `currentVersion` 并归档原稳定版本。

执行：

```text
routingKey
   ↓
Hash
   ↓
bucket
   ↓
V3 / V4
```

---

# 43. 推荐开发顺序

不要按 Controller 开始写。

推荐：

```text
1. Entity / Domain Model

2. Repository

3. RuleSnapshot

4. RuleCache

5. GrayRouter

6. RuleResolver

7. LiteFlow Engine

8. RuleValidator

9. RulePublisher

10. ApplicationService

11. Controller

12. Execution Trace
```

原因：

> 先把核心领域逻辑写通，HTTP API 只是最外层壳。

---

# 44. 第一批必须写的单元测试

## GrayRouterTest

```text
同一个 routingKey 100 次
→ version 相同
```

---

## RuleResolverTest

测试：

```text
无灰度 → currentVersion

有灰度 → Hash决定版本

Cache hit → 不查询 RuleVersionRepository
```

---

## RulePublisherTest

测试：

```text
DRAFT → PUBLISHED

重复 publish → rejected

非法版本 → rejected

validator失败 → 不修改数据库
```

---

## SmoothSwitchTest

```text
T1 resolve V1

publish V2

T2 resolve V2

assert:
T1 Snapshot.version == 1
T2 Snapshot.version == 2
```

这是整个项目最重要的测试之一。

---

# 45. 实现第一版时的三个原则

## 原则一

不要过度设计。

第一版：

```text
单体应用
+
单 MySQL
+
本地 Caffeine
```

足够。

---

## 原则二

规则执行和规则治理分开。

不要：

```java
LiteFlow API
```

散落在：

```text
Controller
Service
Component
```

全部集中：

```text
engine
```

包。

---

## 原则三

RuleSnapshot 必须贯穿整个执行生命周期。

不要在节点执行过程中：

```text
重新读取 currentVersion
```

否则你项目最核心的：

```text
平滑切换
```

就失效了。

---

# 46. 最终最值得你重点维护的核心类

真正项目灵魂只有大约 8 个：

```text
RuleSnapshot

RuleResolver
DefaultRuleResolver

GrayRouter
HashGrayRouter

RulePublisher
DefaultRulePublisher

RuleEngine
LiteFlowRuleEngine

RuleValidator

RuleCache
CaffeineRuleCache
```

如果这几个类设计得干净，这个项目整体就不会差。

Controller、Mapper、Entity 反而都是次要的。

---

# 47. 第一阶段建议实现到此为止

第一版完整链路：

```text
创建 Rule
   ↓
创建 V1
   ↓
发布 V1
   ↓
执行 V1
   ↓
创建 V2
   ↓
V2 开启 10% 灰度
   ↓
同用户稳定命中
   ↓
扩大至 50%
   ↓
Promote V2
   ↓
V2 成为 currentVersion
   ↓
发现问题
   ↓
Rollback V1
```

并且整个过程中：

```text
执行记录可查
节点耗时可查
在途请求版本不被污染
```

做到这一条完整 Demo，FlowPilot 的第一版就成立了。
