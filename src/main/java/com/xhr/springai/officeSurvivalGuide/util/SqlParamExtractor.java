package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.SqlIr;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SqlParamExtractor {

    public List<String> extractWhereParams(String sql) throws JSQLParserException {
        List<String> params = new ArrayList<>();
        Statement stmt = CCJSqlParserUtil.parse(sql);
        if (stmt instanceof Select) {
            processSelect((Select) stmt, params);
        }
        return params;
    }

    private void processSelect(Select select, List<String> params) {
        if (select instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) select;

            // 当前层 WHERE
            collectParams(plainSelect.getWhere(), params);

            // FROM 子查询
            if (plainSelect.getFromItem() instanceof ParenthesedSelect) {
                processSelect(((ParenthesedSelect) plainSelect.getFromItem()).getSelect(), params);
            }

            // JOIN 子查询
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    if (join.getRightItem() instanceof ParenthesedSelect) {
                        processSelect(((ParenthesedSelect) join.getRightItem()).getSelect(), params);
                    }
                }
            }

        } else if (select instanceof SetOperationList) {
            // UNION / INTERSECT / EXCEPT
            for (Select body : ((SetOperationList) select).getSelects()) {
                processSelect(body, params);
            }

        } else if (select instanceof ParenthesedSelect) {
            processSelect(((ParenthesedSelect) select).getSelect(), params);
        }
    }

    private void collectParams(Expression expr, List<String> params) {
        if (expr == null) return;

        if (expr instanceof AndExpression) {
            collectParams(((AndExpression) expr).getLeftExpression(), params);
            collectParams(((AndExpression) expr).getRightExpression(), params);

        } else if (expr instanceof OrExpression) {
            collectParams(((OrExpression) expr).getLeftExpression(), params);
            collectParams(((OrExpression) expr).getRightExpression(), params);

        } else if (expr instanceof Parenthesis) {
            collectParams(((Parenthesis) expr).getExpression(), params);

        } else if (expr instanceof ExistsExpression) {
            Expression right = ((ExistsExpression) expr).getRightExpression();
            if (right instanceof ParenthesedSelect) {
                processSelect(((ParenthesedSelect) right).getSelect(), params);
            }

        } else if (expr instanceof InExpression) {
            InExpression in = (InExpression) expr;
            if (in.getRightExpression() instanceof ParenthesedSelect) {
                processSelect(((ParenthesedSelect) in.getRightExpression()).getSelect(), params);
            } else if (in.getRightExpression() instanceof JdbcParameter) {
                params.add(((Column) in.getLeftExpression()).getColumnName());
            }

        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            Expression left  = binary.getLeftExpression();
            Expression right = binary.getRightExpression();

            if (right instanceof ParenthesedSelect) {
                processSelect(((ParenthesedSelect) right).getSelect(), params);
            } else if (right instanceof JdbcParameter && left instanceof Column) {
                params.add(((Column) left).getColumnName());
            } else if (left instanceof JdbcParameter && right instanceof Column) {
                params.add(((Column) right).getColumnName());
            }
        }
    }

    public List<String> extractColumnName(String sql) throws JSQLParserException {
        List<String> columns = new ArrayList<>();
        Statement stmt = CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) (Select) stmt;
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            Expression expr = item.getExpression();
            if (expr instanceof AllColumns) {
                return Collections.emptyList();
            }
            if (expr instanceof Column) {
                columns.add(((Column) expr).getColumnName());
            }
        }
        return columns;
    }

    public List<String> extractColumnOrAlias(String sql) throws JSQLParserException {
        List<String> columns = new ArrayList<>();
        Statement stmt = CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) (Select) stmt;
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            Expression expr = item.getExpression();
            if (expr instanceof AllColumns) {
                return Collections.emptyList();
            }
            Alias alias = item.getAlias();
            if (alias != null) {
                columns.add(alias.getName());
            } else if (expr instanceof Column) {
                columns.add(((Column) expr).getColumnName());
            }
        }
        return columns;
    }

    public List<SqlIr.ColumnInfo> getTableColumns(String sql) throws JSQLParserException {
        List<SqlIr.ColumnInfo> columns = new ArrayList<>();
        Statement stmt = CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) (Select) stmt;
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            Expression expr = item.getExpression();
            if (expr instanceof AllColumns) {
                return Collections.emptyList();
            }

            Alias alias = item.getAlias();
            String aliasName = "";
            String columnName = "";

            if (expr instanceof Column) {
                columnName = ((Column) expr).getColumnName();
            } else {
                columnName = expr.toString();
            }
            aliasName = columnName;

            if (alias != null) {
                aliasName = alias.getName();
            }

            SqlIr.ColumnInfo columnInfo = new SqlIr.ColumnInfo();
            columnInfo.setColumn(columnName);
            columnInfo.setAlias(aliasName);
            columns.add(columnInfo);
        }
        return columns;
    }
}