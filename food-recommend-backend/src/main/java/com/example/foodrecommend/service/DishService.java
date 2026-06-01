package com.example.foodrecommend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.foodrecommend.entity.Dish;

import java.util.List;

public interface DishService extends IService<Dish> {

    List<Dish> listAll();

    Dish addDish(Dish dish);

    Dish updateDish(Long id, Dish dish);

    void deleteDish(Long id);

    String buildDishEmbeddingText(Dish dish);
}
