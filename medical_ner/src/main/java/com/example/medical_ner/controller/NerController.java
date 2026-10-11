package com.example.medical_ner.controller;

import com.example.medical_ner.entity.RecognitionRecord;
import com.example.medical_ner.mapper.RecognitionRecordMapper;
import com.example.medical_ner.service.NerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ner")
public class NerController {

    @Autowired
    private NerService nerService;

    @Autowired
    private RecognitionRecordMapper recognitionRecordMapper;

    @GetMapping("/test-db")
    public String testDb() {
        RecognitionRecord record = new RecognitionRecord();
        record.setOriginalText("测试文本：患者因发热就诊，诊断为肺炎。");
        record.setCreateTime(LocalDateTime.now());
        recognitionRecordMapper.insert(record);
        return "插入成功，ID=" + record.getId();
    }

    @PostMapping("/recognize")
    public String recognize(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        return nerService.recognize(text);
    }
}