package com.example.medical_ner.mapper;

import com.example.medical_ner.entity.EntityRelation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityRelationMapper {

    @Insert("INSERT INTO entity_relation(record_id, source_entity, target_entity, relation_type) " +
            "VALUES(#{recordId}, #{sourceEntity}, #{targetEntity}, #{relationType})")
    int insert(EntityRelation relation);

    @Select("SELECT * FROM entity_relation WHERE record_id = #{recordId}")
    List<EntityRelation> selectByRecordId(Long recordId);
}