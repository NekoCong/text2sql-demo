package com.nekocong.text2sql.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nekocong.text2sql.security.DataScope;
import com.nekocong.text2sql.security.SqlPermissionException;
import com.nekocong.text2sql.security.TablePolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SQL filter injection: every scoped table reference must end up filtered, in
 * every shape the model is likely to produce, and anything the rewriter cannot handle must be
 * rejected instead of executed.
 */
class DataScopeRewriterTest {

    private final DataScopeRewriter rewriter = new DataScopeRewriter(new TablePolicy());

    private static DataScope student() {
        return new DataScope("u-student", "student", Set.of(1L), 1L, "2023001", Set.of("campus"));
    }

    private static DataScope counselor() {
        return new DataScope("u-counselor", "counselor", Set.of(1L, 2L), null, null, Set.of("campus"));
    }

    private static DataScope admin() {
        return DataScope.admin("u-admin");
    }

    @Test
    void filtersATableWithoutAlias() {
        String sql = rewriter.rewrite("SELECT student_no FROM student", student());

        assertThat(sql).containsIgnoringCase("student_no IN ('2023001')");
        assertThat(sql).containsIgnoringCase("AS student");
    }

    @Test
    void filtersJoinSidesAndKeepsAliases() {
        String sql =
                rewriter.rewrite(
                        "SELECT s.name, avg(sc.score) FROM score sc JOIN student s ON s.id = sc.student_id "
                                + "GROUP BY s.name",
                        student());

        assertThat(sql).containsIgnoringCase("AS sc").containsIgnoringCase("AS s");
        assertThat(sql).containsIgnoringCase("student_id IN (1)");
        assertThat(sql).containsIgnoringCase("student_no IN ('2023001')");
    }

    @Test
    void filtersEveryBranchOfAUnion() {
        String sql =
                rewriter.rewrite(
                        "SELECT student_no FROM student UNION SELECT student_no FROM student", student());

        assertThat(countOccurrences(sql.toLowerCase(), "student_no in ('2023001')")).isEqualTo(2);
    }

    @Test
    void filtersSubqueriesInsideExpressions() {
        String sql =
                rewriter.rewrite(
                        "SELECT name FROM student WHERE class_id IN (SELECT class_id FROM student)",
                        student());

        assertThat(countOccurrences(sql, "SELECT * FROM student")).isEqualTo(2);
    }

    @Test
    void rewritesCteBodiesButLeavesCteReferencesAlone() {
        String sql =
                rewriter.rewrite(
                        "WITH x AS (SELECT * FROM score) SELECT * FROM x", student());

        assertThat(sql).containsIgnoringCase("student_id IN (1)");
        // The outer reference is a CTE, not a physical table, so it must not be wrapped again.
        assertThat(sql).doesNotContainIgnoringCase("AS x");
    }

    @Test
    void counselorFiltersByManagedClasses() {
        String sql =
                rewriter.rewrite("SELECT class_id, count(*) FROM score GROUP BY class_id", counselor());

        assertThat(compact(sql)).contains("class_id in (1,2)");
        assertThat(compact(sql)).doesNotContain("student_id in");
    }

    @Test
    void adminStatementIsReturnedUnchanged() {
        String sql = "SELECT * FROM student";

        assertThat(rewriter.rewrite(sql, admin())).isEqualTo(sql);
    }

    @Test
    void rejectsTablesOutsideTheWhitelist() {
        assertThatThrownBy(() -> rewriter.rewrite("SELECT * FROM salary", student()))
                .isInstanceOf(SqlPermissionException.class)
                .hasMessageContaining("salary");
    }

    @Test
    void rejectsUnparsableSql() {
        assertThatThrownBy(() -> rewriter.rewrite("SELECT FROM", student()))
                .isInstanceOf(SqlPermissionException.class);
    }

    @Test
    void givesRepeatedTableReferencesDistinctAliases() {
        String sql = rewriter.rewrite("SELECT 1 FROM student, student", student());

        assertThat(sql).containsIgnoringCase("AS student_sp2");
    }

    private static String compact(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", " ").replaceAll(",\\s*", ",");
    }

    private static int countOccurrences(String text, String needle) {        int count = 0;
        int index = 0;
        String lowerText = text.toLowerCase();
        String lowerNeedle = needle.toLowerCase();
        while ((index = lowerText.indexOf(lowerNeedle, index)) >= 0) {
            count++;
            index += lowerNeedle.length();
        }
        return count;
    }
}
