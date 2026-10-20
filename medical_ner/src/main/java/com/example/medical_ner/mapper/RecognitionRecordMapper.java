package com.example.medical_ner.mapper;

import com.example.medical_ner.entity.RecognitionRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RecognitionRecordMapper {

    @Insert("INSERT INTO recognition_record(original_text, create_time) VALUES(#{originalText}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RecognitionRecord record);

    @Select("SELECT * FROM recognition_record ORDER BY create_time DESC")
    List<RecognitionRecord> selectAll();

    @Delete("DELETE FROM recognition_record WHERE id = #{id}")
    int deleteById(Long id);
}