package com.example.foodrecommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.foodrecommend.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    @Select("<script>SELECT * FROM dish WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Dish> selectByIds(List<Long> ids);
}
