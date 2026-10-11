package com.example.medical_ner.entity;

import lombok.Data;

@Data
public class EntityResult {
    private Long id;
    private Long recordId;
    private String entityText;
    private String entityType;
    private Integer startIndex;
    private Integer endIndex;
}