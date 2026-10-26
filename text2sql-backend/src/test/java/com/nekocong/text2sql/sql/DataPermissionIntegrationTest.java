package com.nekocong.text2sql.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nekocong.text2sql.security.DataScope;
import com.nekocong.text2sql.security.SqlPermissionException;
import com.nekocong.text2sql.security.TablePolicy;
import com.nekocong.text2sql.sql.model.QueryResult;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end verification of row-level permission against the seeded H2 campus database: the
 * rewritten SQL is actually executed, and the rows prove that a student sees only itself and a
 * counselor only its classes.
 */
@SpringBootTest
class DataPermissionIntegrationTest {

    @Autowired
    private DataScopeRewriter rewriter;

    @Autowired
    private SqlSafetyValidator safetyValidator;

    @Autowired
    private SqlExecutor executor;

    private QueryResult run(String sql, DataScope scope) {
        String safe = safetyValidator.validateAndNormalize(sql);
        return executor.executeBlocking(null, rewriter.rewrite(safe, scope));
    }

    @Test
    void studentOnlySeesItselfEvenWithoutAWhereClause() {
        QueryResult result = run("SELECT student_no, name FROM student", studentScope());

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows().get(0)).containsEntry("student_no", "2023001");
    }

    @Test
    void studentCannotReachAnotherStudentByAskingForIt() {
        QueryResult result =
                run("SELECT student_no FROM student WHERE student_no = '2023002'", studentScope());

        assertThat(result.rowCount()).isZero();
    }

    @Test
    void studentAggregatesAreComputedOverItsOwnRowsOnly() {
        QueryResult result = run("SELECT AVG(score) AS avg_score FROM score", studentScope());

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(((Number) result.rows().get(0).get("avg_score")).doubleValue())
                .isCloseTo(86.125, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void studentJoinCannotWidenTheScope() {
        QueryResult result =
                run(
                        "SELECT s.student_no FROM score sc JOIN student s ON s.id = sc.student_id",
                        studentScope());

        assertThat(result.rowCount()).isPositive();
        assertThat(result.rows())
                .allSatisfy(row -> assertThat(row).containsEntry("student_no", "2023001"));
    }

    @Test
    void counselorSeesExactlyItsTwoClasses() {
        QueryResult result =
                run(
                        "SELECT class_id, COUNT(*) AS n FROM score GROUP BY class_id ORDER BY class_id",
                        counselorScope());

        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.rows()).extracting(row -> ((Number) row.get("class_id")).intValue())
                .containsExactly(1, 2);
    }

    @Test
    void adminIsUnrestricted() {
        QueryResult result = run("SELECT COUNT(*) AS n FROM class", DataScope.admin("u-admin"));

        assertThat(((Number) result.rows().get(0).get("n")).intValue()).isEqualTo(3);
    }

    @Test
    void unionBypassAttemptStillYieldsOnlyTheStudentsRow() {
        QueryResult result =
                run("SELECT student_no FROM student UNION SELECT student_no FROM student", studentScope());

        assertThat(result.rowCount()).isEqualTo(1);
    }

    @Test
    void tableOutsideTheWhitelistIsRejectedBeforeExecution() {
        assertThatThrownBy(() -> run("SELECT * FROM salary", studentScope()))
                .isInstanceOf(SqlPermissionException.class);
    }

    @Test
    void seededFactsAgreeWithTheirStudentsClass() {
        QueryResult mismatched =
                run(
                        "SELECT COUNT(*) AS n FROM score sc JOIN student s ON s.id = sc.student_id "
                                + "WHERE sc.class_id <> s.class_id",
                        DataScope.admin("u-admin"));

        assertThat(((Number) mismatched.rows().get(0).get("n")).intValue()).isZero();
    }

    private static DataScope studentScope() {
        return new DataScope("u-student", "student", Set.of(1L), 1L, "2023001", Set.of("campus"));
    }

    private static DataScope counselorScope() {
        return new DataScope("u-counselor", "counselor", Set.of(1L, 2L), null, null, Set.of("campus"));
    }
}
