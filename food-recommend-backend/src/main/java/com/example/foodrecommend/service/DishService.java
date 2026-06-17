package com.example.foodrecommend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.foodrecommend.entity.Dish;

import java.util.List;

public interface DishService extends IService<Dish> {

    List<Dish> listAll();

    Dish addDish(Dish dish);

    Dish updateDish(Long id, Dish dish);

    void deleteDish(Long id);

    /** 查询可推荐菜品（上架 + 有库存 + 向量已生成） */
    List<Dish> listAvailable();

    /** 扣减库存（采纳时调用），返回扣减后库存 */
    int deductStock(Long id, int quantity);

    String buildDishEmbeddingText(Dish dish);
}
