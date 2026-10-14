package com.example.medical_ner.entity;

import lombok.Data;

@Data
public class EntityRelation {
    private Long id;
    private Long recordId;
    private String sourceEntity;
    private String targetEntity;
    private String relationType;
}