package com.example.medical_ner.service;

import com.example.medical_ner.entity.User;
import com.example.medical_ner.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public User login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            return user; // 登录成功
        }
        return null; // 登录失败
    }
}