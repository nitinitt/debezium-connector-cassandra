/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra;

import java.util.Objects;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;

import io.debezium.DebeziumException;

/**
 * Cell-level data about the source event. Each cell contains the name, value and
 * type of a column in a Cassandra table.
 */
public class CellData implements KafkaRecord {

    /**
     * The type of a column in a Cassandra table
     */
    public enum ColumnType {
        /**
         * A partition column is responsible for data distribution across nodes for this table.
         * Every Cassandra table must have at least one partition column.
         */
        PARTITION,

        /**
         * A clustering column is used to specifies the order that the data is arranged inside the partition.
         * A Cassandra table may not have any clustering column,
         */
        CLUSTERING,

        /**
         * A regular column is a column that is not a partition or a clustering column.
         */
        REGULAR
    }

    public static final String CELL_VALUE_KEY = "value";
    public static final String CELL_DELETION_TS_KEY = "deletion_ts";
    public static final String CELL_SET_KEY = "set";

    public final String name;
    public final Object value;
    public final Object deletionTs;
    public final ColumnType columnType;

    public CellData(String name, Object value, Object deletionTs, ColumnType columnType) {
        this.name = name;
        this.value = value;
        this.deletionTs = deletionTs;
        this.columnType = columnType;
    }

    public boolean isPrimary() {
        return columnType == ColumnType.PARTITION || columnType == ColumnType.CLUSTERING;
    }

    @Override
    public Struct record(Schema schema) {
        try {
            return new Struct(schema)
                    .put(CELL_DELETION_TS_KEY, deletionTs)
                    .put(CELL_SET_KEY, true)
                    .put(CELL_VALUE_KEY, value);
        }
        catch (DataException e) {
            throw new DebeziumException(String.format("Failed to record Cell. Name: %s, Schema: %s, Value: %s", name, schema.toString(), value), e);
        }
    }

    static Schema cellSchema(String columnName, Schema columnSchema, boolean optional) {
        if (columnSchema == null) {
            return null;
        }

        SchemaBuilder schemaBuilder = SchemaBuilder.struct().name(columnName)
                .field(CELL_VALUE_KEY, columnSchema)
                .field(CELL_DELETION_TS_KEY, Schema.OPTIONAL_INT64_SCHEMA)
                .field(CELL_SET_KEY, Schema.BOOLEAN_SCHEMA);
        if (optional) {
            schemaBuilder.optional();
        }
        return schemaBuilder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CellData that = (CellData) o;
        return Objects.equals(name, that.name)
                && Objects.equals(value, that.value)
                && deletionTs == that.deletionTs
                && columnType == that.columnType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, deletionTs, columnType);
    }

    @Override
    public String toString() {
        return "{"
                + "name=" + name
                + ", value=" + value
                + ", deletionTs=" + deletionTs
                + ", type=" + columnType.name()
                + '}';
    }
}
