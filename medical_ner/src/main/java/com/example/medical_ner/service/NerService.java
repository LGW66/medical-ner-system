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
        // 第一步：只做实体识别。单独使用实体提示词，避免关系谓词被误当成实体类型输出
        String entityPrompt = "你是医疗命名实体识别助手。请从下面的医疗文本中识别医疗实体，严格遵守以下规则：\n"
                + "1. 实体类型 type 只能从以下 9 类中选择：" + ENTITY_TYPES + "。\n"
                + "2. 实体边界必须完整：实体要包含完整的修饰语和限定成分，不要截断或省略。"
                + "例如应输出\"上方的巩膜外露\"而不是\"巩膜外露\"，应输出\"小儿惊厥\"而不是\"惊厥\"，应输出\"继发性巨血小板症\"而不是\"血小板症\"。\n"
                + "3. 医疗实体存在嵌套现象，例如\"非持续性室上性心动过速\"内部还包含\"室上性心动过速\"，此时内外层实体都要分别输出。\n"
                + "4. 不要输出\"多发群体\"\"预后状况\"\"遗传因素\"这类关系名称作为实体类型，实体类型只能是上面 9 类之一。\n"
                + "5. 只输出一个 JSON 对象，不要输出任何解释或 markdown 代码块标记，格式必须严格为：\n"
                + "{\"entities\":[{\"text\":\"实体内容\",\"type\":\"实体类型\"}]}\n"
                + "6. 没有识别到实体时输出空数组 []。\n"
                + "医疗文本：" + text;

        JSONArray entities = parseArray(callDeepSeek(entityPrompt, true), "entities");

        // 第二步：只做关系抽取，把第一步识别出的实体作为候选实体传入，并约束关系方向
        JSONArray relations = new JSONArray();
        if (entities != null && !entities.isEmpty()) {
            StringBuilder entityList = new StringBuilder();
            for (int i = 0; i < entities.size(); i++) {
                JSONObject e = entities.getJSONObject(i);
                if (i > 0) {
                    entityList.append("、");
                }
                entityList.append(e.getStr("text")).append("(").append(e.getStr("type")).append(")");
            }

            String relationPrompt = "你是医疗关系抽取助手。请从医疗文本中抽取下面已识别实体之间的关系，严格遵守以下规则：\n"
                    + "1. 关系的 source 和 target 必须严格来自已识别实体列表，不要输出列表之外的实体。\n"
                    + "已识别实体：" + entityList + "\n"
                    + "2. 关系 relation 只能从以下谓词表中选择：" + RELATION_PREDICATES + "。\n"
                    + "3. 方向约束：关系是有方向的，必须按医学语义确定主客体顺序。大多数关系的 subject 应该是疾病实体，"
                    + "例如应输出\"脑炎-辅助治疗-血浆置换\"，而不是\"血浆置换-辅助治疗-脑炎\"；"
                    + "object 是该疾病对应的症状、药物、检查项目、身体部位等。只有\"同义词\"\"鉴别诊断\"\"相关（症状）\"这类双向关系可以互换主客体。\n"
                    + "4. 只输出文本中明确表述的关系，不要依据医学常识补充或推测文本中没有出现的关系。\n"
                    + "5. 只输出一个 JSON 对象，不要输出任何解释或 markdown 代码块标记，格式必须严格为：\n"
                    + "{\"relations\":[{\"source\":\"源实体\",\"target\":\"目标实体\",\"relation\":\"关系谓词\"}]}\n"
                    + "6. 没有抽取到关系时输出空数组 []。\n"
                    + "医疗文本：" + text;

            try {
                relations = parseArray(callDeepSeek(relationPrompt, true), "relations");
            } catch (Exception e) {
                // 关系抽取失败时降级为空关系，不影响实体识别结果
                System.out.println("关系抽取失败，降级为不输出关系：" + e.getMessage());
            }
        }

        // 合并两次调用的结果，对外返回格式保持不变
        JSONObject contentJson = new JSONObject();
        contentJson.set("entities", entities == null ? new JSONArray() : entities);
        contentJson.set("relations", relations);
        String content = contentJson.toString();

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

    /**
     * 解析模型返回的 JSON 字符串，取出指定字段的数组；清理可能残留的 markdown 代码块标记。
     * 字段不存在时返回空数组，便于后续统一处理。
     */
    private JSONArray parseArray(String content, String key) {
        content = content.replace("```json", "").replace("```", "").trim();
        JSONObject obj = JSONUtil.parseObj(content);
        JSONArray arr = obj.getJSONArray(key);
        return arr == null ? new JSONArray() : arr;
    }
}
