package com.example.medical_ner.controller;

import com.example.medical_ner.entity.RecognitionRecord;
import com.example.medical_ner.entity.User;
import com.example.medical_ner.mapper.EntityRelationMapper;
import com.example.medical_ner.mapper.EntityResultMapper;
import com.example.medical_ner.mapper.RecognitionRecordMapper;
import com.example.medical_ner.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private RecognitionRecordMapper recognitionRecordMapper;

    @Autowired
    private EntityResultMapper entityResultMapper;

    @Autowired
    private EntityRelationMapper entityRelationMapper;

    // 登录接口
    @PostMapping("/user/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        User user = userService.login(username, password);

        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            result.put("success", true);
            result.put("username", user.getUsername());
            result.put("role", user.getRole());
            // 注意：真实项目不要把密码返回给前端
        } else {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
        }
        return result;
    }

    // 获取历史记录列表
    @GetMapping("/history/list")
    public List<RecognitionRecord> getHistoryList() {
        return recognitionRecordMapper.selectAll();
    }

    // 清空全部识别数据（关系、实体、记录），用于批量测试后一键清理，避免测试数据污染演示图谱
    @PostMapping("/history/clear")
    @Transactional
    public Map<String, Object> clearHistory() {
        // 先删子表，再删主表
        int relations = entityRelationMapper.deleteAll();
        int entities = entityResultMapper.deleteAll();
        int records = recognitionRecordMapper.deleteAll();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("relationsDeleted", relations);
        result.put("entitiesDeleted", entities);
        result.put("recordsDeleted", records);
        return result;
    }

    // 修改后的删除历史记录接口
    @DeleteMapping("/history/delete/{id}")
    @Transactional // 加上事务注解，保证要么一起删成功，要么一起失败
    public String deleteHistory(@PathVariable Long id) {
        try {
            // 1. 先删关系表里的数据
            entityRelationMapper.deleteByRecordId(id);
            // 2. 再删实体表里的数据
            entityResultMapper.deleteByRecordId(id);
            // 3. 最后删主记录表里的数据
            recognitionRecordMapper.deleteById(id);
            return "删除成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "删除失败：" + e.getMessage();
        }
    }
}