package com.nekocong.text2sql.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nekocong.text2sql.security.DataScope;
import com.nekocong.text2sql.security.SqlPermissionException;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Datasource-level permission: scoped roles may only reach granted datasources, an empty grant set
 * fails instead of falling back to the global default, and admins are unrestricted.
 */
@SpringBootTest
class DatasourceAccessGuardTest {

    @Autowired
    private DatasourceAccessGuard guard;

    @Test
    void grantedRoleResolvesTheDefaultDatasource() {
        assertThat(guard.resolve(scope("student", Set.of("campus")), null)).isEqualTo("campus");
        assertThat(guard.resolve(scope("student", Set.of("campus")), "campus")).isEqualTo("campus");
    }

    @Test
    void ungrantedDatasourceIsRejected() {
        assertThatThrownBy(() -> guard.resolve(scope("student", Set.of("campus")), "warehouse"))
                .isInstanceOf(SqlPermissionException.class)
                .hasMessageContaining("warehouse");
    }

    @Test
    void roleWithoutAnyGrantFailsInsteadOfUsingTheDefault() {
        assertThatThrownBy(() -> guard.resolve(scope("student", Set.of()), null))
                .isInstanceOf(SqlPermissionException.class)
                .hasMessageContaining("没有任何可用的数据源");
    }

    @Test
    void adminMayUseAnyConfiguredDatasource() {
        assertThat(guard.resolve(DataScope.admin("u-admin"), null)).isEqualTo("campus");
        assertThatThrownBy(() -> guard.resolve(DataScope.admin("u-admin"), "does-not-exist"))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static DataScope scope(String role, Set<String> allowed) {
        return new DataScope("u-1", role, Set.of(1L), 1L, "2023001", allowed);
    }
}
