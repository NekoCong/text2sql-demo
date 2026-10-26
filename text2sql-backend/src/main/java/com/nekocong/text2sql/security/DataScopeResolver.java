package com.nekocong.text2sql.security;

import com.nekocong.text2sql.repository.AccessRepository;
import com.nekocong.text2sql.repository.AccessRepository.ScopeRow;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Resolves the data scope for a request from the caller's id and role.
 *
 * <p>Fails closed: an unknown role, or a non-admin account without a usable scope mapping, is
 * rejected with {@code 403} instead of being handed an empty or unrestricted scope.
 */
@Service
public class DataScopeResolver {

    private final AccessRepository access;

    /**
     * Creates the resolver.
     *
     * @param access the access-control repository
     */
    public DataScopeResolver(AccessRepository access) {
        this.access = access;
    }

    /**
     * Resolves the caller's scope.
     *
     * @param userId the authenticated user id
     * @param role the raw role claim
     * @return the resolved scope, or an error when the account has no usable scope
     */
    public Mono<DataScope> resolve(String userId, String role) {
        String canonical = Roles.normalize(role);
        if (canonical == null) {
            return Mono.error(denied("当前账号没有可识别的角色，无法确定数据范围"));
        }
        if (Roles.ADMIN.equals(canonical)) {
            return Mono.just(DataScope.admin(userId));
        }
        return Mono.zip(access.scopes(userId), access.datasourcesForRole(canonical))
                .map(tuple -> build(userId, canonical, tuple.getT1(), tuple.getT2()));
    }

    private static DataScope build(
            String userId, String role, List<ScopeRow> rows, Set<String> datasources) {
        Set<Long> classIds = new LinkedHashSet<>();
        Long studentId = null;
        String studentNo = null;
        for (ScopeRow row : rows) {
            String type = row.scopeType() == null ? "" : row.scopeType();
            switch (type) {
                case "class_id" -> classIds.add(parseLong(row.scopeValue(), "class_id"));
                case "student_id" -> studentId = parseLong(row.scopeValue(), "student_id");
                case "student_no" -> studentNo = row.scopeValue();
                default -> {
                    // Unknown attribute kinds are ignored: they grant nothing.
                }
            }
        }

        if (Roles.STUDENT.equals(role)) {
            if (studentId == null || studentNo == null || studentNo.isBlank() || classIds.isEmpty()) {
                throw denied("当前账号未配置数据范围，请联系管理员");
            }
        } else if (Roles.COUNSELOR.equals(role) && classIds.isEmpty()) {
            throw denied("当前账号未配置管理班级，请联系管理员");
        }

        return new DataScope(userId, role, Set.copyOf(classIds), studentId, studentNo, Set.copyOf(datasources));
    }

    private static Long parseLong(String value, String scopeType) {
        try {
            return Long.valueOf(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            throw denied("数据范围配置无效：" + scopeType + "=" + value);
        }
    }

    private static ResponseStatusException denied(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
