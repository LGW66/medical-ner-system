package com.example.medical_ner.mapper;

import com.example.medical_ner.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Insert("INSERT INTO user(username, password, role) VALUES(#{username}, #{password}, #{role})")
    int insert(User user);
}