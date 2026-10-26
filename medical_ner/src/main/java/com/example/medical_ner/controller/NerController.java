package com.example.medical_ner.controller;

import com.example.medical_ner.entity.EntityRelation;
import com.example.medical_ner.entity.EntityResult;
import com.example.medical_ner.entity.RecognitionRecord;
import com.example.medical_ner.mapper.EntityRelationMapper;
import com.example.medical_ner.mapper.EntityResultMapper;
import com.example.medical_ner.mapper.RecognitionRecordMapper;
import com.example.medical_ner.service.NerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ner")
public class NerController {

    @Autowired
    private NerService nerService;

    @Autowired
    private RecognitionRecordMapper recognitionRecordMapper;

    @Autowired
    private EntityResultMapper entityResultMapper;

    @Autowired
    private EntityRelationMapper entityRelationMapper;

    @GetMapping("/test-db")
    public String testDb() {
        RecognitionRecord record = new RecognitionRecord();
        record.setOriginalText("测试文本：患者因发热就诊，诊断为肺炎。");
        record.setCreateTime(LocalDateTime.now());
        recognitionRecordMapper.insert(record);
        return "插入成功，ID=" + record.getId();
    }

    @GetMapping("/graph/{recordId}")
    public Map<String, Object> getGraph(@PathVariable Long recordId) {
        List<EntityResult> entities = entityResultMapper.selectByRecordId(recordId);
        List<EntityRelation> relations = entityRelationMapper.selectByRecordId(recordId);

        List<Map<String, String>> nodes = new ArrayList<>();
        for (EntityResult e : entities) {
            Map<String, String> node = new HashMap<>();
            node.put("name", e.getEntityText());
            node.put("category", e.getEntityType());
            nodes.add(node);
        }

        List<Map<String, String>> links = new ArrayList<>();
        for (EntityRelation r : relations) {
            Map<String, String> link = new HashMap<>();
            link.put("source", r.getSourceEntity());
            link.put("target", r.getTargetEntity());
            link.put("relation", r.getRelationType());
            links.add(link);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("links", links);
        return result;
    }

    @PostMapping("/recognize")
    public String recognize(@RequestBody Map<String, Object> request) {
        Object textObj = request.get("text");
        String text = textObj == null ? null : textObj.toString().trim();
        if (text == null || text.isEmpty()) {
            return "{\"error\":\"text 不能为空\"}";
        }
        // 默认保存识别结果；批量评测时可传 {"persist": false}，只识别不入库，避免测试数据污染图谱
        boolean persist = !Boolean.FALSE.equals(request.get("persist"));
        return nerService.recognize(text, persist);
    }

    @PostMapping("/rag/ask")
    public String ask(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        return nerService.ask(question);
    }
}