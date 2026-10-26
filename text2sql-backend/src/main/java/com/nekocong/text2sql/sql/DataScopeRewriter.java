package com.nekocong.text2sql.sql;

import com.nekocong.text2sql.security.DataScope;
import com.nekocong.text2sql.security.SqlPermissionException;
import com.nekocong.text2sql.security.TablePolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.select.WithItem;
import org.springframework.stereotype.Component;

/**
 * Enforces data permission by rewriting a generated read-only query into an equivalent one whose
 * every scoped table reference is filtered for the caller.
 *
 * <p>Each protected table reference is replaced by a derived table carrying the row predicate:
 *
 * <pre>
 *   FROM score sc   ->   FROM (SELECT * FROM score WHERE student_id IN (1)) AS sc
 * </pre>
 *
 * <p>This is the single enforcement point for both the deterministic pipeline and the agent tools.
 * It is fail-closed: a statement that cannot be parsed, contains an unsupported construct, or
 * references a table outside the whitelist is rejected — the original SQL is never executed.
 */
@Component
public class DataScopeRewriter {

    private final TablePolicy policy;

    /**
     * Creates the rewriter.
     *
     * @param policy the table/row policy
     */
    public DataScopeRewriter(TablePolicy policy) {
        this.policy = policy;
    }

    /**
     * Rewrites a statement for the caller's scope.
     *
     * @param sql the validated, read-only SQL
     * @param scope the caller's scope
     * @return the enforced SQL (identical to the input for admins)
     * @throws SqlPermissionException when the query cannot be safely scoped
     */
    public String rewrite(String sql, DataScope scope) {
        if (scope.isAdmin()) {
            return sql;
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception ex) {
            throw new SqlPermissionException("无法解析 SQL，已拒绝执行以保护数据范围", ex);
        }
        if (!(statement instanceof Select select)) {
            throw new SqlPermissionException("只支持单条只读查询");
        }
        try {
            rewriteSelect(select, scope, new HashSet<>());
        } catch (SqlPermissionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SqlPermissionException("无法应用数据权限，已拒绝执行", ex);
        }
        return select.toString();
    }

    private void rewriteSelect(Select select, DataScope scope, Set<String> inheritedCtes) {
        Set<String> ctes = new HashSet<>(inheritedCtes);
        List<WithItem> withItems = select.getWithItemsList();
        if (withItems != null) {
            for (WithItem item : withItems) {
                if (item.getAlias() != null && item.getAlias().getName() != null) {
                    ctes.add(item.getAlias().getName().toLowerCase(Locale.ROOT));
                }
            }
            for (WithItem item : withItems) {
                rewriteSelect(item.getSelect(), scope, ctes);
            }
        }

        if (select instanceof PlainSelect plain) {
            rewritePlainSelect(plain, scope, ctes);
        } else if (select instanceof SetOperationList setOperation) {
            for (Select branch : setOperation.getSelects()) {
                rewriteSelect(branch, scope, ctes);
            }
        } else if (select instanceof ParenthesedSelect parenthesed) {
            rewriteSelect(parenthesed.getSelect(), scope, ctes);
        } else if (!(select instanceof Values)) {
            throw new SqlPermissionException("不支持该查询结构，无法应用数据权限");
        }
    }

    private void rewritePlainSelect(PlainSelect select, DataScope scope, Set<String> ctes) {
        Set<String> aliases = new HashSet<>();
        select.setFromItem(rewriteFromItem(select.getFromItem(), scope, ctes, aliases));

        List<Join> joins = select.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                join.setRightItem(rewriteFromItem(join.getRightItem(), scope, ctes, aliases));
                if (join.getOnExpressions() != null) {
                    for (Expression on : join.getOnExpressions()) {
                        rewriteExpression(on, scope, ctes);
                    }
                }
            }
        }

        rewriteExpression(select.getWhere(), scope, ctes);
        rewriteExpression(select.getHaving(), scope, ctes);
        for (SelectItem<?> item : select.getSelectItems()) {
            rewriteExpression(item.getExpression(), scope, ctes);
        }
        if (select.getGroupBy() != null) {
            rewriteExpression(select.getGroupBy().getGroupByExpressionList(), scope, ctes);
        }
        if (select.getOrderByElements() != null) {
            for (OrderByElement order : select.getOrderByElements()) {
                rewriteExpression(order.getExpression(), scope, ctes);
            }
        }
    }

    private FromItem rewriteFromItem(
            FromItem item, DataScope scope, Set<String> ctes, Set<String> aliases) {
        if (item == null) {
            return null;
        }
        if (item instanceof Table table) {
            return rewriteTable(table, scope, ctes, aliases);
        }
        if (item instanceof ParenthesedSelect parenthesed) {
            rewriteSelect(parenthesed.getSelect(), scope, ctes);
            return item;
        }
        if (item instanceof ParenthesedFromItem parenthesed) {
            parenthesed.setFromItem(rewriteFromItem(parenthesed.getFromItem(), scope, ctes, aliases));
            if (parenthesed.getJoins() != null) {
                for (Join join : parenthesed.getJoins()) {
                    join.setRightItem(rewriteFromItem(join.getRightItem(), scope, ctes, aliases));
                }
            }
            return item;
        }
        throw new SqlPermissionException("不支持该表引用形式，无法应用数据权限");
    }

    private FromItem rewriteTable(
            Table table, DataScope scope, Set<String> ctes, Set<String> aliases) {
        String name = table.getName() == null ? "" : table.getName().toLowerCase(Locale.ROOT);
        if (ctes.contains(name)) {
            return table;
        }
        policy.requireAllowed(scope, name);

        String declaredAlias =
                table.getAlias() != null ? table.getAlias().getName() : table.getName();
        Optional<String> filter = policy.filterFor(scope, name);
        if (filter.isEmpty()) {
            register(declaredAlias, aliases);
            return table;
        }

        String alias = uniqueAlias(declaredAlias, aliases);
        register(alias, aliases);

        Table source = new Table(table.getFullyQualifiedName());
        source.setAlias(null);
        PlainSelect inner = new PlainSelect();
        inner.addSelectItems(new AllColumns());
        inner.setFromItem(source);
        try {
            inner.setWhere(CCJSqlParserUtil.parseCondExpression(filter.get()));
        } catch (Exception ex) {
            throw new SqlPermissionException("无法生成行过滤条件", ex);
        }
        ParenthesedSelect derived = new ParenthesedSelect();
        derived.setSelect(inner);
        derived.setAlias(new Alias(alias, true));
        return derived;
    }

    private static String uniqueAlias(String requested, Set<String> aliases) {
        String candidate = requested;
        int suffix = 1;
        while (aliases.contains(candidate.toLowerCase(Locale.ROOT))) {
            suffix++;
            candidate = requested + "_sp" + suffix;
        }
        return candidate;
    }

    private static void register(String alias, Set<String> aliases) {
        if (alias != null) {
            aliases.add(alias.toLowerCase(Locale.ROOT));
        }
    }

    private void rewriteExpression(Expression expression, DataScope scope, Set<String> ctes) {
        if (expression == null) {
            return;
        }
        expression.accept(
                new ExpressionVisitorAdapter() {
                    @Override
                    public void visit(Select subSelect) {
                        // ParenthesedSelect.accept(ExpressionVisitor) dispatches to visit(Select);
                        // it does not call the visit(ParenthesedSelect) overload.
                        if (subSelect instanceof ParenthesedSelect parenthesed) {
                            rewriteSelect(parenthesed.getSelect(), scope, ctes);
                        } else {
                            rewriteSelect(subSelect, scope, ctes);
                        }
                    }
                });
    }
}
