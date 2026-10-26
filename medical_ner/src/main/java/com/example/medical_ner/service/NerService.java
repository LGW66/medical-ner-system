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
import java.util.ArrayList;
import java.util.List;

@Service
public class NerService {

    /** CMeEE-V2 的 9 类医疗实体（中文名，对应其标注体系的 dis/sym/dru/equ/pro/bod/mic/ite/dep） */
    private static final String ENTITY_TYPES =
            "疾病、症状、药物、医疗设备、医疗程序、身体部位、微生物、医学检验项目、科室";

    /** CMeIE-V2 的 53 种关系谓词（按类型组合去重后共 44 个谓词名） */
    private static final String RELATION_PREDICATES =
            "临床表现、药物治疗、辅助治疗、手术治疗、化疗、放射治疗、实验室检查、影像学检查、辅助检查、内窥镜检查、组织学检查、筛查、"
                    + "病因、高危因素、风险评估因素、病史、遗传因素、发病机制、病理生理、"
                    + "发病部位、转移部位、外侵部位、多发群体、发病率、发病年龄、多发地区、发病性别倾向、死亡率、多发季节、传播途径、"
                    + "并发症、病理分型、相关（导致）、相关（转化）、相关（症状）、鉴别诊断、同义词、阶段、预防、就诊科室、"
                    + "治疗后症状、预后状况、预后生存率、侵及周围组织转移的症状";

    /** RAG 检索时最多拼进提示词的三元组条数，防止超出模型 token 上限 */
    private static final int MAX_RETRIEVED_RELATIONS = 80;

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

    /**
     * 医疗实体识别 + 关系抽取。
     * persist 为 false 时只识别不入库（批量评测用），避免测试数据污染图谱；默认 persist 为 true，原有行为不变。
     */
    public String recognize(String text, boolean persist) {
        String prompt = "你是医疗信息抽取助手。请从下面的医疗文本中抽取医疗实体以及实体之间的关系，严格遵守以下规则：\n"
                + "1. 实体类型 type 只能从以下 9 类中选择：" + ENTITY_TYPES + "。\n"
                + "2. 医疗实体存在嵌套现象，例如\"非持续性室上性心动过速\"内部还包含\"室上性心动过速\"，此时内外层实体都要分别输出。\n"
                + "3. 关系 relation 只能从以下谓词表中选择：" + RELATION_PREDICATES + "。\n"
                + "4. 只输出一个 JSON 对象，不要输出任何解释或 markdown 代码块标记，格式必须严格为：\n"
                + "{\"entities\":[{\"text\":\"实体内容\",\"type\":\"实体类型\"}],\"relations\":[{\"source\":\"源实体\",\"target\":\"目标实体\",\"relation\":\"关系谓词\"}]}\n"
                + "5. 没有抽取到实体或关系时，对应数组输出空数组 []。\n"
                + "医疗文本：" + text;

        String content = callDeepSeek(prompt, true);

        // 清理可能残留的 markdown 代码块标记
        content = content.replace("```json", "").replace("```", "").trim();

        // 先解析，解析失败时不会往数据库写入半截数据
        JSONObject contentJson = JSONUtil.parseObj(content);
        JSONArray entities = contentJson.getJSONArray("entities");
        JSONArray relations = contentJson.getJSONArray("relations");

        Long recordId = null;
        if (persist) {
            // 保存识别记录
            RecognitionRecord record = new RecognitionRecord();
            record.setOriginalText(text);
            record.setCreateTime(LocalDateTime.now());
            recognitionRecordMapper.insert(record);
            recordId = record.getId();

            // 保存实体
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

            // 保存关系
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
        }

        // 返回结果（persist 为 false 时没有 recordId）
        JSONObject result = new JSONObject();
        if (recordId != null) {
            result.set("recordId", recordId);
        }
        result.set("content", content);
        return result.toString();
    }

    public String ask(String question) {
        // 1. 先用大模型从问题中提取检索关键词
        List<String> keywords = extractKeywords(question);

        // 2. 只检索与关键词相关的三元组，避免把整张表拼进提示词导致超出 token 上限
        List<EntityRelation> relatedRelations = new ArrayList<>();
        if (!keywords.isEmpty()) {
            relatedRelations = entityRelationMapper.selectByKeywords(keywords, MAX_RETRIEVED_RELATIONS);
        }

        // 3. 拼接检索到的图谱上下文
        StringBuilder graphContext = new StringBuilder();
        if (relatedRelations.isEmpty()) {
            graphContext.append("（知识图谱中未检索到与问题相关的三元组）");
        } else {
            graphContext.append("以下是从知识图谱中检索到的医疗关系：\n");
            for (EntityRelation rel : relatedRelations) {
                graphContext.append(rel.getSourceEntity()).append(" - ")
                        .append(rel.getRelationType()).append(" - ")
                        .append(rel.getTargetEntity()).append("\n");
            }
        }

        String prompt = "你是一个医疗问答助手。请根据下面提供的知识图谱信息回答用户问题。"
                + "如果知识图谱中没有相关信息，就基于你的医学知识回答，但不要编造。\n\n"
                + graphContext + "\n"
                + "用户问题：" + question;

        return callDeepSeek(prompt, false);
    }

    /**
     * 用大模型从问题中提取检索关键词，返回关键词列表。
     * 提取失败时返回空列表，问答退化为纯大模型回答，不影响接口可用性。
     */
    private List<String> extractKeywords(String question) {
        try {
            String prompt = "请从下面的问题中提取用于知识库检索的医疗关键词（如疾病、症状、药物、检查项目、治疗方法等名称），"
                    + "只输出一个 JSON 对象，不要输出任何其他内容，格式为：{\"keywords\":[\"关键词1\", \"关键词2\"]}\n"
                    + "问题：" + question;
            String content = callDeepSeek(prompt, true);
            content = content.replace("```json", "").replace("```", "").trim();
            JSONArray arr = JSONUtil.parseObj(content).getJSONArray("keywords");
            List<String> keywords = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    String kw = arr.getStr(i);
                    if (kw != null && !kw.trim().isEmpty()) {
                        keywords.add(kw.trim());
                    }
                }
            }
            return keywords;
        } catch (Exception e) {
            System.out.println("关键词提取失败，本次问答不使用知识图谱检索：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 调用 DeepSeek 对话接口，返回模型输出的文本内容。
     * jsonMode 为 true 时开启 response_format=json_object 强制输出合法 JSON（要求提示词中包含"JSON"字样）。
     */
    private String callDeepSeek(String prompt, boolean jsonMode) {
        JSONObject message = new JSONObject();
        message.set("role", "user");
        message.set("content", prompt);

        JSONArray messages = new JSONArray();
        messages.add(message);

        JSONObject body = new JSONObject();
        body.set("model", "deepseek-chat");
        body.set("messages", messages);
        if (jsonMode) {
            JSONObject responseFormat = new JSONObject();
            responseFormat.set("type", "json_object");
            body.set("response_format", responseFormat);
        }

        HttpResponse response = HttpRequest.post(apiUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(body.toString())
                .timeout(60000)
                .execute();

        String responseBody = response.body();
        System.out.println("DeepSeek 原始返回：" + responseBody);

        JSONObject jsonResponse = JSONUtil.parseObj(responseBody);
        return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getStr("content");
    }
}
