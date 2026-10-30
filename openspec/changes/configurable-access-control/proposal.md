# Proposal

## Why

Access control is currently expressed as code: `Roles` recognizes exactly three role names, `DataScope` exposes `isAdmin()` / `isStudent()` / `isCounselor()`, `TablePolicy` branches on those names to build filters, and the explanation/caller-context helpers repeat the same role split. Adding a role today means editing all of those places, the JWT claim, the seed data, the admin page, and the tests. That is acceptable for a fixed three-role demo, but it is the wrong shape for a product that wants administrators to define roles, permissions, and data scopes without a code change.

The current admin surface only toggles "role → datasource" access. It cannot define module/function permissions, assign multiple roles to a user, configure per-table row filters, or audit who changed what. The completed `campus-data-permission` change made runtime enforcement correct for the three seeded roles; this change makes the policy source configurable while preserving that enforcement contract.

## What Changes

- Introduce a data-driven access-control model: roles, permissions, role hierarchy, user-role assignments, role-datasource grants, and per-table data-scope policies. The built-in `admin` / `student` / `counselor` roles remain as seeded, editable role records rather than code constants.
- Replace role-name branching in enforcement with capability checks and policy records. No enforcement path may call `isAdmin()` / `isStudent()` / `isCounselor()` to decide data visibility; a role gets access because it carries the relevant permission or data-scope policy, not because of its name.
- Add a typed data-scope policy model per role, datasource, and table: `ALL`, `FILTER`, or `DENY`, with predicates built only from approved principal attributes (`userId`, `username`, `studentId`, `studentNo`, `classIds`) and validated columns. No free-form SQL predicate is accepted from the admin UI.
- Add module-level and function-level permissions and enforce them on routes and APIs, not only in the sidebar. Examples: `chat.use`, `datasource.read`, `datasource.write`, `model.manage`, `access.manage`, `audit.read`, `access.bypass_data_scope`.
- Add an administrator "权限管理" area with tabs for roles, permissions, data scopes, user-role assignment, and audit. The existing datasource-access matrix becomes one tab or is folded into the new area.
- Record every access-control change (role, permission, assignment, datasource grant, data-scope policy) in an audit log with actor, timestamp, before/after, and result; expose a read-only audit view.
- Fix the CTE shadowing bypass in `DataScopeRewriter` as a side fix: a CTE whose name collides with a protected table must not skip the table whitelist or row filter.
- Preserve the runtime guarantees of the completed `campus-data-permission` change: both the pipeline and AgentScope paths still resolve the caller, apply the same policy, fail closed, and never execute an unfiltered statement for a restricted role.
- Migrate the existing `app_user.role`, `user_scope`, `datasource_access`, and `TablePolicy` behavior into the new model, including a seed for the three built-in roles and their current permissions/scopes.

Out of scope for this change:

- Approval workflow for permission changes.
- Periodic access-review campaigns and recertification.
- SSO/SCIM, multi-tenant organizations, and attribute-based access control with arbitrary expressions.
- Column-level masking or row-level security inside MySQL; policies remain application-enforced at the SQL execution chokepoint.
- AgentScope sandbox / container isolation. This change is about authorization policy, not execution isolation.

## Capabilities

### New Capabilities

- `access-control`: the configurable role, permission, user-role, datasource-grant, data-scope, and audit model; effective-permission resolution; policy-driven SQL enforcement; the admin API/UI; and the CTE-safe enforcement guarantee.

### Modified Capabilities

- None. The completed-but-unarchived `campus-data-permission` change remains the runtime enforcement contract. This change introduces the configuration layer and supersedes its hardcoded role vocabulary; the two must be reconciled (archive `campus-data-permission` first, then update its role requirement) before this change is archived.

## Impact

- **Backend**: `security/Roles`, `DataScope`, `DataScopeResolver`, `TablePolicy`, `DataScopeRewriter`, `SqlTools`, `Text2SqlPipelineService`, `HarnessAgentFactory`, `SecurityConfig`, `AuthService`/`JwtService`, `DatasourceAccessGuard`, new access-control repositories/services/controllers, `db/app-schema.sql` and `db/app-data.sql`, and security tests.
- **Frontend**: new admin access-control route/view, API client, navigation entry, permission-aware route guards, and reuse of the existing datasource-access matrix as a tab or replacement.
- **Data**: new application tables for roles, permissions, role-permission, user-role, data-scope policies, and audit; migration/backfill from `app_user.role`, `user_scope`, and `datasource_access`.
- **Docs**: `docs/nl2sql.md`, `docs/deploy.md`, and README access-control section.
- **Dependencies**: none expected; JSqlParser is already present for the CTE fix. No sandbox or container dependency is introduced.
- **Compatibility**: the JWT `role` claim remains for display/backward compatibility, but enforcement resolves effective roles and permissions from the database so a role change takes effect on the next request.
