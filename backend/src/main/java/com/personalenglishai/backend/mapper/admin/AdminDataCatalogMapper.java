package com.personalenglishai.backend.mapper.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDataCatalogMapper {
    List<Map<String, Object>> selectTables();

    List<Map<String, Object>> selectColumns(@Param("tableName") String tableName);

    List<Map<String, Object>> selectIndexes(@Param("tableName") String tableName);

    List<Map<String, Object>> selectForeignKeys(@Param("tableName") String tableName);

    List<Map<String, Object>> selectAllForeignKeys();

    Object selectLatestAt(@Param("tableName") String tableName, @Param("timeColumn") String timeColumn);
}
