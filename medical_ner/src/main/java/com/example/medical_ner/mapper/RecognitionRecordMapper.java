package com.example.medical_ner.mapper;

import com.example.medical_ner.entity.RecognitionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface RecognitionRecordMapper {

    @Insert("INSERT INTO recognition_record(original_text, create_time) VALUES(#{originalText}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RecognitionRecord record);
}