package com.example.medical_ner.mapper;

import com.example.medical_ner.entity.EntityResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface EntityResultMapper {

    @Insert("INSERT INTO entity_result(record_id, entity_text, entity_type, start_index, end_index) " +
            "VALUES(#{recordId}, #{entityText}, #{entityType}, #{startIndex}, #{endIndex})")
    int insert(EntityResult entity);
}