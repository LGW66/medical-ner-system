package com.example.medical_ner.mapper;

import com.example.medical_ner.entity.EntityResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityResultMapper {

    @Insert("INSERT INTO entity_result(record_id, entity_text, entity_type, start_index, end_index) " +
            "VALUES(#{recordId}, #{entityText}, #{entityType}, #{startIndex}, #{endIndex})")
    int insert(EntityResult entity);

    @Select("SELECT * FROM entity_result WHERE record_id = #{recordId}")
    List<EntityResult> selectByRecordId(Long recordId);

    @Delete("DELETE FROM entity_result WHERE record_id = #{recordId}")
    int deleteByRecordId(Long recordId);

    // 清空全部实体数据（测试后清理用）
    @Delete("DELETE FROM entity_result")
    int deleteAll();
}