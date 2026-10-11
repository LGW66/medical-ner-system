package com.example.medical_ner.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RecognitionRecord {
    private Long id;
    private String originalText;
    private LocalDateTime createTime;
}