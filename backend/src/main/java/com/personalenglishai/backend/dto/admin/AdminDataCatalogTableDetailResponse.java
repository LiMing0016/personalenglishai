package com.personalenglishai.backend.dto.admin;

import java.util.ArrayList;
import java.util.List;

public class AdminDataCatalogTableDetailResponse extends AdminDataCatalogTableResponse {
    private List<Column> columns = new ArrayList<>();
    private List<Index> indexes = new ArrayList<>();
    private List<ForeignKey> foreignKeys = new ArrayList<>();
    private List<Relationship> relationships = new ArrayList<>();
    private List<String> sensitiveColumns = new ArrayList<>();
    private List<String> securityNotes = new ArrayList<>();

    public List<Column> getColumns() { return columns; }
    public void setColumns(List<Column> columns) { this.columns = columns; }
    public List<Index> getIndexes() { return indexes; }
    public void setIndexes(List<Index> indexes) { this.indexes = indexes; }
    public List<ForeignKey> getForeignKeys() { return foreignKeys; }
    public void setForeignKeys(List<ForeignKey> foreignKeys) { this.foreignKeys = foreignKeys; }
    public List<Relationship> getRelationships() { return relationships; }
    public void setRelationships(List<Relationship> relationships) { this.relationships = relationships; }
    public List<String> getSensitiveColumns() { return sensitiveColumns; }
    public void setSensitiveColumns(List<String> sensitiveColumns) { this.sensitiveColumns = sensitiveColumns; }
    public List<String> getSecurityNotes() { return securityNotes; }
    public void setSecurityNotes(List<String> securityNotes) { this.securityNotes = securityNotes; }

    public static class Column {
        private String name;
        private String type;
        private boolean nullable;
        private String defaultValue;
        private boolean primaryKey;
        private boolean sensitive;
        private String comment;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public boolean isPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
        public boolean isSensitive() { return sensitive; }
        public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class Index {
        private String name;
        private String columns;
        private boolean uniqueIndex;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getColumns() { return columns; }
        public void setColumns(String columns) { this.columns = columns; }
        public boolean isUniqueIndex() { return uniqueIndex; }
        public void setUniqueIndex(boolean uniqueIndex) { this.uniqueIndex = uniqueIndex; }
    }

    public static class ForeignKey {
        private String name;
        private String columnName;
        private String referencedTableName;
        private String referencedColumnName;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getReferencedTableName() { return referencedTableName; }
        public void setReferencedTableName(String referencedTableName) { this.referencedTableName = referencedTableName; }
        public String getReferencedColumnName() { return referencedColumnName; }
        public void setReferencedColumnName(String referencedColumnName) { this.referencedColumnName = referencedColumnName; }
    }

    public static class Relationship {
        private String sourceTable;
        private String sourceColumn;
        private String targetTable;
        private String targetColumn;
        private String relationType;
        private String direction;
        private String description;

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
        public String getTargetColumn() { return targetColumn; }
        public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
