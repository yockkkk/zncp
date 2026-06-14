package com.example.foodrecommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.foodrecommend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
