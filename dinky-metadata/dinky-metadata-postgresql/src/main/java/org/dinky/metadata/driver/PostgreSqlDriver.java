/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.dinky.metadata.driver;

import org.dinky.assertion.Asserts;
import org.dinky.metadata.convert.ITypeConvert;
import org.dinky.metadata.convert.PostgreSqlTypeConvert;
import org.dinky.metadata.query.IDBQuery;
import org.dinky.metadata.query.PostgreSqlQuery;
import org.dinky.model.Column;
import org.dinky.model.QueryData;
import org.dinky.model.Table;
import org.dinky.utils.TextUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSqlDriver
 *
 * @author wenmo
 * @since 2021/7/22 9:28
 */
public class PostgreSqlDriver extends AbstractJdbcDriver {

    @Override
    String getDriverClass() {
        return "org.postgresql.Driver";
    }

    @Override
    public IDBQuery getDBQuery() {
        return new PostgreSqlQuery();
    }

    @Override
    public ITypeConvert getTypeConvert() {
        return new PostgreSqlTypeConvert();
    }

    @Override
    public String getType() {
        return "PostgreSql";
    }

    @Override
    public String getName() {
        return "PostgreSql 数据库";
    }

    @Override
    public Map<String, String> getFlinkColumnTypeConversion() {
        return new HashMap<>();
    }

    @Override
    public String generateCreateSchemaSql(String schemaName) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE SCHEMA ").append(schemaName);
        return sb.toString();
    }

    @Override
    public String getSqlSelect(Table table) {
        List<Column> columns = table.getColumns();
        StringBuilder sb = new StringBuilder("SELECT\n");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("    ");
            if (i > 0) {
                sb.append(",");
            }
            String columnComment = columns.get(i).getComment();
            if (Asserts.isNotNullString(columnComment)) {
                if (columnComment.contains("\'") | columnComment.contains("\"")) {
                    columnComment = columnComment.replaceAll("\"|'", "");
                }
                sb.append("\"" + columns.get(i).getName() + "\"  --  " + columnComment + " \n");
            } else {
                sb.append("\"" + columns.get(i).getName() + "\" \n");
            }
        }
        if (Asserts.isNotNullString(table.getComment())) {
            sb.append(
                    " FROM \""
                            + table.getSchema()
                            + "\".\""
                            + table.getName()
                            + "\";"
                            + " -- "
                            + table.getComment()
                            + "\n");
        } else {
            sb.append(" FROM \"" + table.getSchema() + "\".\"" + table.getName() + "\";\n");
        }
        return sb.toString();
    }

    @Override
    public String getCreateTableSql(Table table) {
        StringBuilder key = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        StringBuilder comments = new StringBuilder();

        sb.append("CREATE TABLE \"")
                .append(table.getSchema())
                .append("\".\"")
                .append(table.getName())
                .append("\" (\n");

        for (Column column : table.getColumns()) {
            sb.append("  \"").append(column.getName()).append("\" ");
            sb.append(column.getType());
            if (column.getPrecision() > 0 && column.getScale() > 0) {
                sb.append("(")
                        .append(column.getLength())
                        .append(",")
                        .append(column.getScale())
                        .append(")");
            } else if (null != column.getLength()) { // 处理字符串类型
                sb.append("(").append(column.getLength()).append(")");
            }
            if (column.isNullable() == true) {
                sb.append(" NOT NULL");
            }
            if (Asserts.isNotNullString(column.getDefaultValue())
                    && !column.getDefaultValue().contains("nextval")) {
                sb.append(" DEFAULT ").append(column.getDefaultValue());
            }
            sb.append(",\n");

            // 注释
            if (Asserts.isNotNullString(column.getComment())) {
                comments.append("COMMENT ON COLUMN \"")
                        .append(table.getSchema())
                        .append("\".\"")
                        .append(table.getName())
                        .append("\".\"")
                        .append(column.getName())
                        .append("\" IS '")
                        .append(column.getComment())
                        .append("';\n");
            }
        }
        sb.deleteCharAt(sb.length() - 3);

        if (Asserts.isNotNullString(table.getComment())) {
            comments.append("COMMENT ON TABLE \"")
                    .append(table.getSchema())
                    .append("\".\"")
                    .append(table.getName())
                    .append("\" IS '")
                    .append(table.getComment())
                    .append("';");
        }
        sb.append(");\n\n").append(comments);

        return sb.toString();
    }

    @Override
    public StringBuilder genQueryOption(QueryData queryData) {

        String where = queryData.getOption().getWhere();
        String order = queryData.getOption().getOrder();
        String limitStart = queryData.getOption().getLimitStart();
        String limitEnd = queryData.getOption().getLimitEnd();

        StringBuilder optionBuilder =
                new StringBuilder()
                        .append("select * from ")
                        .append(queryData.getSchemaName())
                        .append(".")
                        .append(queryData.getTableName());

        if (where != null && !where.equals("")) {
            optionBuilder.append(" where ").append(where);
        }
        if (order != null && !order.equals("")) {
            optionBuilder.append(" order by ").append(order);
        }

        if (TextUtil.isEmpty(limitStart)) {
            limitStart = "0";
        }
        if (TextUtil.isEmpty(limitEnd)) {
            limitEnd = "100";
        }
        optionBuilder.append(" limit ").append(limitEnd);

        return optionBuilder;
    }
}
