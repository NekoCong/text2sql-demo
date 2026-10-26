package com.nekocong.text2sql.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The permission matrix: which tables each role may touch, and which predicate is injected for each
 * of them. Anything outside the campus whitelist is denied for every non-admin role.
 */
class TablePolicyTest {

    private final TablePolicy policy = new TablePolicy();

    @Test
    void allowsOnlyTheCampusTablesForScopedRoles() {
        for (String role : new String[] {Roles.STUDENT, Roles.COUNSELOR}) {
            for (String table : TablePolicy.CAMPUS_TABLES) {
                assertThatCode(() -> policy.requireAllowed(scope(role), table))
                        .as("%s may read %s", role, table)
                        .doesNotThrowAnyException();
            }
            assertThatThrownBy(() -> policy.requireAllowed(scope(role), "salary"))
                    .isInstanceOf(SqlPermissionException.class)
                    .hasMessageContaining("salary");
        }
    }

    @Test
    void adminIsNotRestrictedByTheWhitelist() {
        assertThatCode(() -> policy.requireAllowed(DataScope.admin("u-admin"), "salary"))
                .doesNotThrowAnyException();
    }

    @Test
    void unknownRoleHasNoTables() {
        assertThat(policy.tablesFor("user")).isEmpty();
        assertThatThrownBy(() -> policy.requireAllowed(scope("user"), "student"))
                .isInstanceOf(SqlPermissionException.class);
    }

    @Test
    void studentIsFilteredByItsOwnIdentity() {
        DataScope student = scope(Roles.STUDENT);

        assertThat(policy.filterFor(student, "student"))
                .contains("student_no IN ('2023001')");
        assertThat(policy.filterFor(student, "score")).contains("student_id IN (1)");
        assertThat(policy.filterFor(student, "attendance")).contains("student_id IN (1)");
        assertThat(policy.filterFor(student, "class")).contains("id IN (1)");
        assertThat(policy.filterFor(student, "course")).isEmpty();
    }

    @Test
    void counselorIsFilteredByManagedClasses() {
        DataScope counselor = scope(Roles.COUNSELOR);

        assertThat(policy.filterFor(counselor, "student")).contains("class_id IN (1,2)");
        assertThat(policy.filterFor(counselor, "score")).contains("class_id IN (1,2)");
        assertThat(policy.filterFor(counselor, "attendance")).contains("class_id IN (1,2)");
        assertThat(policy.filterFor(counselor, "class")).contains("id IN (1,2)");
        assertThat(policy.filterFor(counselor, "course")).isEmpty();
    }

    @Test
    void adminGetsNoRowFilter() {
        for (String table : TablePolicy.CAMPUS_TABLES) {
            assertThat(policy.filterFor(DataScope.admin("u-admin"), table)).isEmpty();
        }
    }

    private static DataScope scope(String role) {
        if (Roles.STUDENT.equals(role)) {
            return new DataScope("u-student", role, Set.of(1L), 1L, "2023001", Set.of("campus"));
        }
        return new DataScope("u-counselor", role, Set.of(2L, 1L), null, null, Set.of("campus"));
    }
}
