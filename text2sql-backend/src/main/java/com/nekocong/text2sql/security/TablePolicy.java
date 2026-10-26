package com.nekocong.text2sql.security;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The declarative permission policy: which tables a role may touch, and which row filter applies to
 * each of them.
 *
 * <p>Policies live in code (reviewed with the change) while the principal's <em>values</em> live in
 * the application database ({@code user_scope}). Anything not listed here is denied by default, so
 * a table added to the business schema later is invisible until it is deliberately granted.
 */
@Component
public class TablePolicy {

    /** Tables of the campus dataset. */
    public static final Set<String> CAMPUS_TABLES =
            Set.of("class", "course", "student", "score", "attendance");

    /**
     * Tables a role may reference.
     *
     * @param role the canonical role
     * @return the allowed table names
     */
    public Set<String> tablesFor(String role) {
        if (Roles.ADMIN.equals(role)
                || Roles.STUDENT.equals(role)
                || Roles.COUNSELOR.equals(role)) {
            return CAMPUS_TABLES;
        }
        return Set.of();
    }

    /**
     * Reports whether the caller may reference a table.
     *
     * <p>Admins bypass the whitelist (they are unrestricted everywhere else too); every other role
     * is limited to the tables declared for it, and anything undeclared is denied.
     *
     * @param scope the caller's scope
     * @param table the referenced table (unqualified)
     * @return {@code true} when the table is visible to the caller
     */
    public boolean allows(DataScope scope, String table) {
        if (scope.isAdmin()) {
            return true;
        }
        return tablesFor(scope.role()).contains(table.toLowerCase(Locale.ROOT));
    }

    /**
     * Fails when the table is outside the caller's whitelist.
     *
     * @param scope the caller's scope
     * @param table the referenced table (unqualified)
     * @throws SqlPermissionException when the table is not allowed for the role
     */
    public void requireAllowed(DataScope scope, String table) {
        if (!allows(scope, table)) {
            throw new SqlPermissionException("表 " + table + " 不在当前数据范围内");
        }
    }

    /**
     * The row filter to inject for one table, if any.
     *
     * @param scope the caller's scope
     * @param table the referenced table (unqualified)
     * @return a SQL predicate (without {@code WHERE}), or empty when the table needs no row filter
     */
    public Optional<String> filterFor(DataScope scope, String table) {
        if (scope.isAdmin()) {
            return Optional.empty();
        }
        String name = table.toLowerCase(Locale.ROOT);
        if (scope.isStudent()) {
            return switch (name) {
                case "student" -> Optional.of("student_no IN (" + quote(scope.studentNo()) + ")");
                case "score", "attendance" -> Optional.of("student_id IN (" + scope.studentId() + ")");
                case "class" -> Optional.of("id IN (" + join(scope.classIds()) + ")");
                default -> Optional.empty();
            };
        }
        if (scope.isCounselor()) {
            return switch (name) {
                case "student", "score", "attendance" ->
                        Optional.of("class_id IN (" + join(scope.classIds()) + ")");
                case "class" -> Optional.of("id IN (" + join(scope.classIds()) + ")");
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    private static String join(Set<Long> ids) {
        return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
