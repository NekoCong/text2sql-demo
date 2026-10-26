# Proposal

## Why

当前 Demo 的权限只有 `admin` / `user` 两级，NL2SQL 生成的 SQL 对任何登录用户都作用于整库；AgentScope 这条路径甚至把调用者身份丢成了 `null`。要把这个仓库当作"智慧校园"背景的作品来讲，必须让"学生只能看到自己、辅导员只能看到所管班级"成为系统强制行为，而不是提示词里的一句约定。

## What Changes

- **BREAKING** 演示数据源由电商域整体替换为校园域（`campus`）：移除 `customers` / `products` / `orders` / `order_items`、旧演示账号与旧会话；重新 seed 校园 schema、mock 数据与演示账号。
- 角色扩展为 `student` / `counselor` / `admin`；`admin` 保持现有"全量、不过滤"语义。
- 新增主体数据范围（应用库 `user_scope`）：`student_no` / `class_id`；按角色解析为「表级白名单 + 行级谓词」。
- 新增行级权限强制层：在只读校验之后、执行之前对生成 SQL 做 AST 重写，两条路径（确定性 pipeline 与 AgentScope agent）共用同一收口；解析失败即拒绝执行（fail-closed）。
- Agent 路径补齐身份：`query_database` 通过 AgentScope 注入的 `DataScope` 取得调用者，不再以 `null` 身份执行。
- **BREAKING** 数据源访问由静态配置改为白名单策略：非管理员只能使用角色被授权使用的数据源。
- 新增管理员页面与 API，维护「角色 → 数据源」白名单。
- Agent 执行过程向前端暴露实际执行的 SQL 与工具步骤，与确定性 pipeline 的 SQL 展示对齐。
- 元数据工具（`list_tables` / `describe_table`）与送入提示词的 schema 按角色裁剪。

## Capabilities

### New Capabilities

- `data-permission`: 角色与主体数据范围的解析、表级白名单、行级谓词注入，以及在 pipeline / agent 两条路径上不可绕过的强制与拒绝语义。
- `campus-demo-data`: 校园域默认数据源、schema 与 mock 数据、演示账号与其数据范围映射，以及电商演示数据的移除。
- `agent-sql-transparency`: AgentScope 执行过程中向前端流式暴露工具步骤与最终执行的 SQL。
- `datasource-access-policy`: 「角色 → 数据源」白名单的持久化、执行期判定，以及管理员的管理接口与页面。

### Modified Capabilities

无。项目当前没有已归档的主 specs，本 change 建立第一批能力契约。

## Impact

- **后端（Java / Spring WebFlux / AgentScope 2.0）**：`security/Roles`、`Text2SqlController`、`Text2SqlPipelineService`、`SchemaProvider` 实现、`SqlTools`、`HarnessAgentFactory`、`Text2SqlAgentService`、`SqlExecutor` 之前的执行链、`DataSourceRegistry`、应用库 schema 与 seed、`application.yml` 默认数据源。
- **前端（Vue 3）**：Agent SSE 事件渲染、ChatView 步骤区、新增管理员视图与 `api` 模块、路由与导航。
- **文档**：`README.md`、`docs/nl2sql.md`、`docs/deploy.md` 中的电商示例与账号说明。
- **测试**：现有多个测试以电商 schema、`admin/user` 角色与 `demo` 数据源为前提，需要随数据集与权限模型一起更新。
- **依赖**：需要引入一个 SQL 解析器（AST）用于谓词注入，属新增运行时依赖。
