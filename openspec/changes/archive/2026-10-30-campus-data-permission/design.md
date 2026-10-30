# Design

## Context

见 proposal.md 的 Why。落地前需要知道的现状约束：

- **收口已存在**：确定性流水线与 AgentScope 工具最终都走 `SqlSafetyValidator` → `SqlExecutor`，只是 `SqlTools.queryDatabase` 目前以 `null` 身份调用 `Text2SqlPipelineService.runBlocking`，身份在 agent 路径丢失。
- **身份只有两级**：`Roles` 只认 `admin` / `user`，`normalize()` 会把任何未知角色静默降级为 `user`；注册接口固定写入 `user`。
- **应用库与业务库分离**：应用库走 R2DBC（`app_user`、`conversation`、`message`），业务库走 JDBC（Hikari + `DatabaseMetaData` 内省）；业务库当前是电商 schema，由 `db/schema.sql` + `db/data.sql` 种子化，`DataSourceRegistry.seed()` 硬编码这两个文件。
- **MySQL 没有行级安全**，且视图定义不允许引用用户变量，所以"每请求 SET 变量 + 视图过滤"在 MySQL 上不可行；数据库侧强制只能在 PostgreSQL RLS 上成立。
- **AgentScope 2.0.4 的两条能力已核实**（反编译 `ToolMethodInvoker`）：`@Tool` 方法可以声明 `RuntimeContext` / `ToolExecutionContext` / 任意 POJO 参数，框架通过 `RuntimeContext.get(Class)` 注入；事件流中有 `ToolCallStartEvent` / `ToolCallEndEvent` / `ToolResultEndEvent`（含状态）。
- **前端**：确定性路径已经消费 `step` / `sql` / `rows` / `chart` 事件；agent 路径只消费 `delta`。路由守卫已存在，`AuthUser` 已带 `role`。

## Goals / Non-Goals

**Goals:**

- 行级与表级权限只有一处强制点，两条查询路径共用，且失败即拒绝。
- 身份从 JWT 一路到达 SQL 执行，agent 路径不再出现匿名查询。
- 演示数据整体切换为校园域，并给出可一眼验证的多角色数据集。
- 数据源访问变成可运营的白名单，管理员可在页面上维护。
- Agent 过程对用户可见：步骤 + 实际执行的 SQL。

**Non-Goals:**

- 列级脱敏/隐藏（`phone` 等字段在第一阶段按原样返回）。
- 数据库侧 RLS 或每用户独立数据库账号。
- 用户级数据源例外（只做角色级授权），以及 `user_scope` 的可视化维护界面。
- Agent 会话的持久化（当前 agent 路径本就不写会话）。
- 跨数据源联邦查询、权限审计报表。

## Decisions

### D1. 强制点放在只读校验之后、执行之前

新增 `DataScopeRewriter`，插在 `SqlSafetyValidator.validateAndNormalize()` 与 `SqlExecutor` 之间，两条路径都经过它。

- 备选 A：只在提示词里要求过滤 —— 一句提示注入即可绕过，只保留为"提高首次命中率"的辅助层，不作为边界。
- 备选 B：数据库视图 + 会话变量 —— MySQL 不支持（见 Context），排除。
- 备选 C：查完过滤结果行 —— 聚合查询会泄露（`AVG` 在过滤前计算），排除。
- 备选 D：在 `SqlExecutor` 内部重写 —— 语义上把"安全执行"和"权限策略"揉在一起，且测试困难；放在两者之间的独立组件更清晰。

### D2. AST 重写：把受保护的表引用替换为带谓词的派生表

用 SQL 解析器（JSqlParser 一类）解析为 AST，对每个表引用：

```
FROM score sc
  ->  FROM (SELECT * FROM score WHERE student_id = 1001) sc

FROM student            -- 无别名
  ->  FROM (SELECT * FROM student WHERE student_no = '2023001') student
```

要点：

- **表白名单检查**与**谓词注入**在同一个遍历里完成：白名单外的表直接抛权限异常，不生成任何 SQL。
- **别名规则**：保留原别名；无别名时用表名；同一层出现重名时追加 `_sp1`、`_sp2`（派生表必须有别名）。
- **CTE 名称**（`WITH x AS ... FROM x`）不是物理表，遍历时必须排除，否则会被误判为白名单外表。
- **重写后再次过一遍只读校验**，防止序列化过程引入不可执行或非只读内容。
- **fail-closed**：解析失败、遇到不支持的结构（如动态表名、表函数）、或重写后无法序列化 → 抛出明确错误，原 SQL 绝不执行。
- 备选：字符串/正则拼接 —— 无法正确处理子查询、`UNION`、注释与字符串字面量；排除。备选：Calcite —— 能力过剩且引入更重的依赖；排除。

谓词模板随角色与表而不同（学生按学号/学号 id，辅导员按班级集合），所以过滤器不是"统一 `class_id`"，而是 `(role, table) → predicate template`。这也解释了为什么成绩/考勤表需要同时冗余 `class_id` 与 `student_id`：辅导员的过滤列是 `class_id`，学生的过滤列是 `student_id`。

### D3. 策略写在代码里，主体属性存在应用库

- 代码中维护 `(role, table) → allow / predicate template`，与版本一起评审、不可在运行期被篡改。
- 应用库只存**主体属性**：`user_scope(user_id, scope_type, scope_value)`，`scope_type ∈ {class_id, student_id, student_no}`。
- 学生：`class_id` + `student_id` + `student_no` 各一条；辅导员：多条 `class_id`；管理员：无记录 = 全量。
- 备选：把策略也放进数据库让管理员编辑 —— 等于把安全边界交给运行期配置，演示阶段收益低、风险高；排除（`user_scope` 的可视化维护列为后续项）。

把映射放在应用库而不是业务库，理由：主体属性属于身份域，避免"为了查权限先查被保护的库"的循环依赖，也让策略可以脱离业务 schema 独立测试。

### D4. 身份传递：Web 显式传参，Agent 走 `RuntimeContext` 注入

- `DataScopeResolver`（应用库 R2DBC）把 `userId + role` 解析成不可变 `DataScope`（role、classIds、studentId、studentNo、允许的数据源、是否有范围）。
- Controller 解析 `DataScope` 后显式传给 `Text2SqlPipelineService`（签名从 `userId` 改为 `DataScope`）。
- Agent 路径：`Text2SqlAgentService` 解析同一个 `DataScope`，写入 `RuntimeContext`（`ctx.put(DataScope.class, scope)`），工具方法签名改为 `queryDatabase(question, datasourceId, DataScope scope)`。
  - **关键约束**：该参数不能加 `@ToolParam`，否则会被当作模型可见入参；不打注解时框架按"上下文 POJO"注入。
  - 这样不需要每请求 new 一套 `SqlTools`，也不需要用 `ThreadLocal`，子智能体复用同一 `RuntimeContext` 时同样生效。
- 备选：按请求构造绑定用户的工具实例 —— 可行但让 Agent 工厂多一条隐式生命周期；备选：`ThreadLocal` —— 与 AgentScope 的线程池模型冲突；均排除。

### D5. Agent 的 SQL 可见性走旁路监听，而不是解析模型可见文本

- 定义 `ExecutionListener`（函数式接口，接收 datasourceId、实际执行的 SQL、行数、失败原因），随 `DataScope` 一起放进 `RuntimeContext`，由 pipeline 在重写后/执行后回调。
- `Text2SqlAgentService` 把它接到一个 `Sinks.Many`，与 AgentScope 的事件流合并；旁路事件转成 SSE：`step`（工具开始/结束，来源 `ToolCallStartEvent` / `ToolResultEndEvent`）与 `sql`（来自 listener）。
- 备选：从 `ToolResultTextDeltaEvent` 的文本里解析 JSON 取 `sql` —— 依赖工具返回值格式，且工具失败时拿不到；排除。

### D6. 数据源白名单：应用库 + 每请求判定

- 表：`datasource_access(role, datasource_id)`，主键 `(role, datasource_id)`。
- 解析请求时读入允许集合；管理员不受限。
- "未指定数据源"只能回落到**调用者允许集合内的**默认数据源；集合为空 → 直接失败，不回落全局默认。
- 每请求读取（数据量极小），因此管理员改动天然即时生效，不需要缓存失效逻辑。
- 与行级权限叠加：通过数据源授权只代表"能连"，表级/行级约束照旧。

### D7. 校园数据模型

业务库 `campus`：

```
class      (id, name, grade, head_teacher)                     -- 维度
course     (id, code, name, credits)                           -- 维度
student    (id, student_no UNIQUE, name, gender, class_id, enrolled_at, phone)
score      (id, student_id, course_id, term, score, class_id)
attendance (id, student_id, course_id, class_date, status, class_id)
```

- `score` / `attendance` 冗余 `class_id`：让辅导员的过滤成为表内谓词，避免重写器合成 JOIN，也降低模型写错 JOIN 的概率。代价是"记录的 class_id 必须等于学生的 class_id"，由种子数据保证。
- 过滤列全部建索引：`student(student_no)`、`student(class_id)`、`score(student_id)`、`score(class_id)`、`attendance(student_id)`、`attendance(class_id)`。
- 演示账号（固定 id，便于断言）：`student01` / `student02`（同班）、`student03`（异班）、`counselor01`（管两个班）、`admin`。

### D8. 数据集整体替换与初始化

- 默认数据源 id 由 `demo` 改为 `campus`，`db/schema.sql` / `db/data.sql` 重写为校园域；`app-data.sql` 的账号、`user_scope`、`datasource_access`、会话种子全部替换，旧账号与会话不再存在。
- 保留现有"一个种子数据源 + 硬编码脚本"的机制（现在只有一个种子数据源），不引入按 id 分目录的复杂度。
- 注册接口默认角色改为 `student`；未配置范围的学生在查询时收到"未配置数据范围"，而不是空结果。
- 迁移：初始化脚本只在空数据目录执行，因此 demo 环境的升级步骤是重建数据卷；文档必须写清楚。

### D9. 前端

- Agent 路径复用确定性路径已有的 `step` / `sql` 渲染组件，追加"已按当前数据范围过滤"提示。
- 新增管理员视图（数据源 × 角色矩阵，勾选保存）与 `api/admin.ts`，路由加 `meta.admin` 守卫并在导航中按角色显示。
- 非管理员直接访问路由时跳回对话页，接口侧同样拒绝（前端守卫只是体验层）。

## Risks / Trade-offs

- **解析器对 H2 与 MySQL 的方言差异** → 只接受 `SELECT` / `WITH` 子集；重写后重新过只读校验；集成测试分别在 H2（测试）与 MySQL（部署）上跑同一批用例。
- **派生表带来的执行计划变化** → MySQL 8 可合并派生表；过滤列建索引；保留现有行数上限与超时。
- **CTE / 自连接 / 同名表别名冲突** → 明确别名生成与 CTE 排除规则，用例覆盖自连接、`UNION`、嵌套子查询。
- **`user_scope` 存了业务库 id（`student_id`）形成双源** → 种子数据加一致性测试；若未来业务库 id 会变，改为请求期按 `student_no` 查一次 id（记为后续项）。
- **管理员改白名单即改变安全边界** → 仅 `admin` 可调用，写结构化日志；完整审计不在本 change 范围。
- **AgentScope 子智能体是否沿用同一 `RuntimeContext`** → 设计上不依赖事件冒泡（走 listener），但仍需一次真实链路验证；若不沿用，回退方案是禁止子智能体调用查询工具。
- **数据卷不重建就不会执行新种子** → 部署文档明确 `docker compose down -v`（或手工执行脚本），并在启动日志中打印当前默认数据源与种子来源。

## Migration Plan

1. 应用库新增表与角色值、替换种子脚本；业务库 schema/seed 替换为校园域。
2. 落地权限内核（策略、解析、重写器）与单元测试，此时尚未接线，行为不变。
3. 接线两条路径（pipeline / agent），补齐身份传递；旧接口保持兼容或明确破坏点（`userId` → `DataScope` 属内部签名）。
4. 数据源白名单判定 + 管理员 API。
5. 前端：agent 步骤/SQL 展示、管理员页面与路由守卫。
6. 文档与测试收尾；用一份"角色 × 问题"矩阵做人工验收（学生本人 / 学生越界 / 辅导员跨班 / 管理员全量 / 未授权数据源）。

Rollback：单 change 一次提交，回滚即 revert；数据侧因是 demo，回滚后重建数据卷即可。

## Open Questions

- `user_scope` 是否需要一个管理员维护界面？当前只靠种子脚本，后续可加，不影响本设计。
- 列级权限（如学生对同班同学手机号）是否要做？已明确列为非目标，留待下一 change。
