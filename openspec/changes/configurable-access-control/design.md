# Design

## Context

See `proposal.md` for motivation. The current enforcement is correct for three fixed roles but hardcodes the role vocabulary in several places:

- `security/Roles` normalizes exactly `admin`, `student`, `counselor`.
- `security/DataScope` is a record whose `isAdmin()` / `isStudent()` / `isCounselor()` methods are used by `TablePolicy`, the explanation helper, and the caller-context helper.
- `security/TablePolicy` maps role names to table whitelists and row predicates.
- `security/DataScopeResolver` switches on the role name and requires role-specific `user_scope` values.
- `Text2SqlPipelineService` has `scopedExplanation` and `callerContext` branches on `scope.isAdmin()` / `isStudent()`.
- `SecurityConfig` and admin controllers use `Roles.isAdmin(jwt)`.
- `datasource_access` stores a role string; `user_scope` stores principal attributes.

The completed-but-unarchived `campus-data-permission` change built the runtime enforcement chain: both the deterministic pipeline and the AgentScope path resolve a `DataScope` and rewrite SQL before execution. This change keeps that chain and replaces the hardcoded policy source with database records. The main spec for `data-permission` is not yet in `openspec/specs/`; see the compatibility note below.

Constraints:

- Java 25 / Spring WebFlux / R2DBC / MySQL 8.4; AgentScope 2.0.4.
- JSqlParser is already used by `DataScopeRewriter`; no new SQL parser is needed.
- The demo deployment initializes the app database from `db/app-schema.sql` and `db/app-data.sql`, which run only on an empty MySQL data directory.
- JWT currently carries a single `role` claim. Enforcement must not depend on a stale claim if an administrator changes roles.
- No approval workflow, SSO, column masking, or sandbox is introduced.

## Goals / Non-Goals

**Goals:**

- Make roles, permissions, role assignments, datasource grants, and data-scope policies data-driven and manageable from the admin UI.
- Remove role-name branching from enforcement; authorization is based on effective permissions and policy records.
- Keep the existing runtime guarantees: both paths use the same chokepoint, unknown policy fails closed, and a restricted user never executes an unfiltered statement.
- Make CTE handling safe: a CTE name cannot exempt a protected table from policy.
- Preserve the effective behavior of the three seeded roles during migration.
- Make access-control changes auditable and resistant to self-escalation and last-admin lockout.

**Non-Goals:**

- Approval workflow for permission changes.
- Periodic access-review campaigns or recertification.
- SSO/SCIM, multi-tenant organizations, or arbitrary ABAC expressions.
- Column-level masking or database-side row-level security.
- AgentScope sandbox or container isolation (separate concern; see the exploration discussion).
- A general-purpose authorization framework; this is a focused RBAC + data-scope model for this product.

## Decisions

### 1. Data model: access-control tables in the application database

Add the following tables to the app database (names are illustrative; follow the existing `snake_case` schema style):

| Table | Purpose | Key columns |
| --- | --- | --- |
| `access_role` | Role catalog | `id`, `code`, `name`, `description`, `parent_id`, `built_in`, `enabled`, timestamps |
| `access_permission` | Permission catalog | `id`, `key`, `module`, `action`, `description`, `built_in` |
| `access_role_permission` | Role → permission | `role_id`, `permission_id` |
| `access_user_role` | User → role | `user_id`, `role_id` |
| `access_role_datasource` | Role → datasource | `role_id`, `datasource_id` |
| `access_data_scope` | Per-role/table data policy | `role_id`, `datasource_id`, `table_name`, `policy`, `column_name`, `principal_attribute` |
| `access_audit` | Change log | `id`, `actor_id`, `action`, `entity_type`, `entity_id`, `before_json`, `after_json`, `result`, `created_at` |

`user_scope` remains the source of principal attributes (`class_id`, `student_id`, `student_no`). Add `user_id` as a supported attribute if needed; do not create a second attribute table in this phase.

`access_role.code` is immutable after creation and is used only for display/diagnostics; enforcement never branches on it. Built-in roles are seeded rows with `built_in = true`. The built-in administrator role cannot be deleted or disabled; its management capability is protected by the anti-escalation rules.

`access_data_scope` allows one policy per role/datasource/table in this phase. `FILTER` has exactly one column and one principal attribute; `ALL` and `DENY` have neither. This deliberately avoids an expression language and arbitrary SQL in the admin UI.

Alternative considered: storing policies as JSON expressions. Rejected because it creates an injection surface, is hard to validate, and is difficult to explain in the admin UI.

### 2. Effective permissions: resolve from the database, not the JWT claim

Introduce an `EffectiveAccess` (or `AccessContext`) value object containing:

- the user id and effective role list;
- the union of permissions from all enabled roles and their ancestors;
- the effective datasource grants;
- the effective per-table data-scope policies;
- the principal attributes resolved from `user_scope`.

`DataScopeResolver` becomes `AccessContextResolver`: it takes the authenticated user id, loads roles/permissions/scopes, and fails closed when no usable policy exists. The JWT `role` claim remains for display and backward compatibility, but it is not the enforcement source. This means a role/permission change takes effect on the next request without forcing a re-login.

No cache in the first implementation. A per-request load is a few indexed queries; if profiling shows a problem, add a short-TTL cache with explicit invalidation on admin writes. Alternative considered: putting all roles in the JWT. Rejected because role changes would require re-login and the token would grow unboundedly.

### 3. Data-scope semantics

For each user and table, compute the effective policy from all enabled roles:

1. If any role has `DENY` for the table → the table is denied.
2. Otherwise, if any role has `ALL` → the table is unrestricted.
3. Otherwise, all `FILTER` policies are combined with `OR` (the union of each role's allowed rows).
4. If no role defines a policy for the table → deny by default.

Within one role, one policy per table. `FILTER` supports:

- `column = principal.<attribute>` for a single-valued attribute (`userId`, `username`, `studentId`, `studentNo`);
- `column IN principal.<attribute>` for a set-valued attribute (`classIds`).

Column names and principal attributes are validated against the datasource metadata and a fixed attribute catalog. Identifiers are quoted; values are parameterized or safely quoted. No raw predicate text is accepted.

The built-in administrator role receives the `access.bypass_data_scope` permission, which makes the policy engine return `ALL` for every table. That is a permission, not a role-name check; an administrator could grant it to another role deliberately.

Alternative considered: a role-name `admin` bypass. Rejected because it reintroduces the hardcoding this change removes and makes the admin role impossible to model as data.

### 4. Enforcement chokepoint

Replace `TablePolicy` with a policy engine that consumes `EffectiveAccess`:

- `DataScopeRewriter` takes `EffectiveAccess` instead of `DataScope` and asks the engine for each table's policy.
- `SqlTools`, `Text2SqlPipelineService`, and the schema/introspection helpers use the same `EffectiveAccess`.
- `SqlTools` still injects the access context through AgentScope's `RuntimeContext`; the model cannot supply it via `@ToolParam`.
- `scopedExplanation` and `callerContext` use role display names and policy descriptions from the context, not `isStudent()` / `isCounselor()`.
- `SecurityConfig` and controllers check permissions (for example, an `AccessPermissionService` or method-level checks) rather than `Roles.isAdmin(jwt)`.

The chokepoint remains “validate → rewrite → validate → execute” in the pipeline. The agent path continues to call the pipeline through `query_database`, so both paths share the same enforcement.

### 5. CTE-safe rewriting

The current `DataScopeRewriter` adds all CTE names to a set before rewriting the CTE bodies, and `rewriteTable` returns early when a name is in that set. A CTE named like a protected table can therefore skip the whitelist and filter.

Fix approach:

- While rewriting a non-recursive CTE body, the CTE's own name is **not** visible; only previously defined CTEs are visible.
- Reject any CTE whose name collides with a protected table name, even if the body looks benign. This is a simple, testable, fail-closed rule.
- Reject `WITH RECURSIVE` unless a later change adds explicit recursive support; recursive visibility is easy to get wrong and not needed for this product.
- Keep the existing “parse failure → reject” behavior.
- Add tests for `WITH student AS (SELECT * FROM student) SELECT * FROM student`, schema-qualified CTE bodies, nested CTEs, and a legal CTE that references a protected table in its body.

Alternative considered: fully modeling SQL CTE scoping. Rejected for this phase because the product does not need recursive CTEs and the fail-closed rule is safer.

### 6. Admin API and UI

Add an admin-only area at `/admin/access-control` with permission `access.manage`. Tabs:

1. **Roles** — list/create/edit/disable roles, set a parent role, show built-in status.
2. **Permissions** — permission catalog grouped by module, with a role-permission matrix.
3. **Data scope** — per role, per datasource, per table: `ALL` / `FILTER` / `DENY`; for `FILTER`, select a column and a principal attribute.
4. **Users** — search users, assign/remove roles.
5. **Datasource access** — reuse the existing role → datasource matrix, migrated to role records.
6. **Audit** — read-only change log with actor, time, entity, before/after, result.

Backend endpoints live under `/api/admin/access-control/**`; they validate role codes, permission keys, table/column names, principal attributes, inheritance cycles, and last-admin protection. The frontend route guard and navigation use the effective permission set, but the API is the security boundary.

Alternative considered: extending the existing datasource-access page only. Rejected because roles, permissions, data scopes, users, and audit do not fit one matrix.

### 7. Anti-escalation and lockout protection

- Only `access.manage` can call management endpoints.
- A user cannot grant themselves a permission they do not currently hold; the built-in administrator is the bootstrap exception.
- The last principal holding `access.manage` cannot be removed or disabled.
- The built-in administrator role cannot be deleted or disabled.
- Role codes are immutable; permission keys come from the seeded catalog; built-in permission definitions cannot be deleted.
- Every rejected escalation attempt is recorded in the audit log with result `DENIED`.

### 8. Audit

Write an audit row in the same transaction as the access-control change. Record actor, action, entity type/id, before/after JSON, and result. Audit reads require `audit.read`. Audit rows are append-only through the API; no update/delete endpoint. Retention/export is a later operational concern.

### 9. Migration and compatibility

- Add the new tables to `db/app-schema.sql` with `CREATE TABLE IF NOT EXISTS` and indexes.
- Seed built-in roles, permissions, role-permission mappings, role-datasource grants, and data-scope policies in `db/app-data.sql` with `INSERT IGNORE`.
- Add a one-time migration script under `db/migration/` for existing deployments. It:
  - creates the tables if absent;
  - inserts built-in roles/permissions;
  - backfills `access_user_role` from `app_user.role`;
  - backfills `access_role_datasource` from `datasource_access`;
  - backfills `access_data_scope` from the current `TablePolicy` behavior (`student` / `counselor` / `admin`);
  - leaves `app_user.role`, `user_scope`, and `datasource_access` in place for compatibility.
- The migration is idempotent; running it twice is safe.
- The demo deployment currently recreates the app DB only on an empty data directory. The deploy docs must state that an existing deployment needs the migration script (or a fresh `down -v` with data loss) before the new code starts.
- Legacy role values that cannot be mapped are left without an `access_user_role`; the resolver denies them rather than defaulting to a scoped role.

### 10. Testing strategy

- Unit: effective permission union and inheritance; cycle rejection; data-scope merge (`DENY` > `ALL` > `FILTER` union); unknown table/column/attribute; one-policy-per-table validation; anti-escalation; last-admin protection.
- SQL rewriting: CTE collision, CTE body references, nested CTEs, recursive rejection, parse failure, backslash/quote handling in predicates.
- Integration: admin API 403/409/422 behavior; audit rows; pipeline and agent path produce the same filtered result; role change takes effect on the next request.
- Migration: run the backfill against a copy of the seed data and compare effective access for `admin`, `student01`, and `counselor01` with the current behavior.
- Frontend: `npm run build`; manual permission-aware navigation and admin CRUD checks. No new component-test framework in this change.

## Risks / Trade-offs

- [Administrator lockout during migration or role edits] → Seed a built-in admin role with `access.manage` and `access.bypass_data_scope`; protect the last holder; run the migration in a transaction and verify the admin login before enabling the UI.
- [Policy engine becomes a new single point of failure] → Keep it pure and unit-tested; default deny; log the effective policy decision for denied requests.
- [Per-request role/permission queries add latency] → Start without a cache; add indexes on `access_user_role(user_id)`, `access_role_permission(role_id)`, and `access_data_scope(role_id, datasource_id, table_name)`; add a short-TTL cache only if measurements require it.
- [Admin UI accidentally allows an unsafe predicate] → Typed policies only; table/column/attribute selectors; server-side validation against metadata; no raw SQL field.
- [Data-scope inheritance/merge semantics surprise users] → Document `DENY` > `ALL` > `FILTER` union; show the effective policy preview in the admin UI.
- [Spec conflict with the completed-but-unarchived `campus-data-permission` change] → Archive that change first and update its “only three roles” requirement, or merge the two changes before archiving this one.
- [Migration on an existing MySQL volume] → Provide an idempotent migration script; document backup and verification; do not rely on init scripts for existing volumes.
- [Recursive CTEs or exotic SQL remain a bypass surface] → Reject recursive CTEs in this phase; keep parse-failure fail-closed; add regression tests.
- [JWT still carries a stale `role` claim used elsewhere] → Treat the claim as display-only; audit any remaining `Roles.isAdmin` call sites and replace them with permission checks.

## Migration Plan

1. Add the new tables and seed the built-in roles/permissions/scopes.
2. Backfill existing users, datasource grants, and data scopes; verify `admin`, `student01`, `counselor01` effective access.
3. Deploy the backend with the new resolver/policy engine; keep legacy tables and claims for rollback.
4. Deploy the admin UI and permission-aware navigation.
5. Verify the CTE regression tests and the pipeline/agent parity tests.
6. Rollback: revert the application image; the new tables are additive and can remain unused. The legacy `app_user.role`, `user_scope`, and `datasource_access` remain the fallback.
7. Before archiving this change, archive `campus-data-permission` and reconcile its role-vocabulary requirement with the configurable model.

## Open Questions

- Approval workflow and periodic access review are deferred; they can be added as a follow-up change without changing this change's data model.
- Column-level masking is deferred; the policy model can later add a `column` dimension without changing role/permission records.
- Whether to add a short-TTL effective-permission cache is an operational decision after profiling; it does not change the specs or task breakdown.
