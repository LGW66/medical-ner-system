package com.example.medical_ner.mapper;

import com.example.medical_ner.entity.EntityRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityRelationMapper {

    @Insert("INSERT INTO entity_relation(record_id, source_entity, target_entity, relation_type) " +
            "VALUES(#{recordId}, #{sourceEntity}, #{targetEntity}, #{relationType})")
    int insert(EntityRelation relation);

    @Select("SELECT * FROM entity_relation WHERE record_id = #{recordId}")
    List<EntityRelation> selectByRecordId(Long recordId);

    @Select("SELECT * FROM entity_relation")
    List<EntityRelation> selectAll();

    @Delete("DELETE FROM entity_relation WHERE record_id = #{recordId}")
    int deleteByRecordId(Long recordId);

    // 清空全部关系数据（测试后清理用）
    @Delete("DELETE FROM entity_relation")
    int deleteAll();

    // 按关键词模糊匹配源实体或目标实体，检索相关三元组（RAG 问答用，limit 防止超出 token 上限）
    @Select("<script>" +
            "SELECT * FROM entity_relation WHERE " +
            "<foreach collection='keywords' item='kw' separator=' OR '>" +
            "(source_entity LIKE CONCAT('%', #{kw}, '%') OR target_entity LIKE CONCAT('%', #{kw}, '%'))" +
            "</foreach>" +
            " LIMIT #{limit}" +
            "</script>")
    List<EntityRelation> selectByKeywords(@Param("keywords") List<String> keywords, @Param("limit") int limit);
}