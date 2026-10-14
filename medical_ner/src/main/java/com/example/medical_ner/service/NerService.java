package com.example.medical_ner.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.medical_ner.entity.EntityRelation;
import com.example.medical_ner.entity.EntityResult;
import com.example.medical_ner.entity.RecognitionRecord;
import com.example.medical_ner.mapper.EntityRelationMapper;
import com.example.medical_ner.mapper.EntityResultMapper;
import com.example.medical_ner.mapper.RecognitionRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NerService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.api-url}")
    private String apiUrl;

    @Autowired
    private EntityRelationMapper entityRelationMapper;

    @Autowired
    private RecognitionRecordMapper recognitionRecordMapper;

    @Autowired
    private EntityResultMapper entityResultMapper;

    public String recognize(String text) {
        // 1. 构造请求体
        String prompt = "请识别以下医疗文本中的疾病、药物、症状，并抽取实体间关系，" +
                "以JSON格式返回，格式为：{\"entities\":[{\"text\":\"实体内容\",\"type\":\"实体类型\"}]," +
                "\"relations\":[{\"source\":\"源实体\",\"target\":\"目标实体\",\"relation\":\"关系类型\"}]}。" +
                "文本：" + text;

        JSONObject message = new JSONObject();
        message.set("role", "user");
        message.set("content", prompt);

        JSONArray messages = new JSONArray();
        messages.add(message);

        JSONObject body = new JSONObject();
        body.set("model", "deepseek-chat");
        body.set("messages", messages);

        // 2. 发送请求
        HttpResponse response = HttpRequest.post(apiUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(body.toString())
                .timeout(60000)
                .execute();

        String responseBody = response.body();
        System.out.println("DeepSeek 原始返回：" + responseBody);

        // 3. 解析返回结果
        JSONObject jsonResponse = JSONUtil.parseObj(responseBody);
        String content = jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getStr("content");

        System.out.println("DeepSeek content：" + content);

        // 4. 清理 markdown 代码块标记
        content = content.replace("```json", "").replace("```", "").trim();

        // 5. 保存识别记录
        RecognitionRecord record = new RecognitionRecord();
        record.setOriginalText(text);
        record.setCreateTime(LocalDateTime.now());
        recognitionRecordMapper.insert(record);

        // 6. 解析实体并保存
        JSONObject contentJson = JSONUtil.parseObj(content);
        JSONArray entities = contentJson.getJSONArray("entities");
        if (entities != null) {
            for (int i = 0; i < entities.size(); i++) {
                JSONObject entity = entities.getJSONObject(i);
                EntityResult entityResult = new EntityResult();
                entityResult.setRecordId(record.getId());
                entityResult.setEntityText(entity.getStr("text"));
                entityResult.setEntityType(entity.getStr("type"));
                entityResultMapper.insert(entityResult);
            }
        } else {
            System.out.println("警告：entities 为 null，返回内容为：" + content);
        }

        // 7. 解析关系并保存
        JSONArray relations = contentJson.getJSONArray("relations");
        if (relations != null) {
            for (int i = 0; i < relations.size(); i++) {
                JSONObject rel = relations.getJSONObject(i);
                EntityRelation relation = new EntityRelation();
                relation.setRecordId(record.getId());
                relation.setSourceEntity(rel.getStr("source"));
                relation.setTargetEntity(rel.getStr("target"));
                relation.setRelationType(rel.getStr("relation"));
                entityRelationMapper.insert(relation);
            }
        } else {
            System.out.println("警告：relations 为 null，返回内容为：" + content);
        }

        // 8. 返回结果（带上 recordId）
        JSONObject result = new JSONObject();
        result.set("recordId", record.getId());
        result.set("content", content);
        return result.toString();
    }
}