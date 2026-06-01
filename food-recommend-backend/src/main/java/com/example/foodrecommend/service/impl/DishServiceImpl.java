package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.service.DishService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    @Override
    public List<Dish> listAll() {
        return list();
    }

    @Override
    public Dish addDish(Dish dish) {
        dish.setStatus(1);
        dish.setVectorStatus(0);
        save(dish);
        return dish;
    }

    @Override
    public Dish updateDish(Long id, Dish dish) {
        Dish existing = getById(id);
        if (existing == null) {
            throw new BusinessException("菜品不存在");
        }
        dish.setId(id);
        dish.setVectorStatus(0);
        updateById(dish);
        return getById(id);
    }

    @Override
    public void deleteDish(Long id) {
        removeById(id);
    }

    @Override
    public String buildDishEmbeddingText(Dish dish) {
        return "菜品名称：" + n(dish.getName()) + "。" +
                "分类：" + n(dish.getCategory()) + "。" +
                "价格：" + n(dish.getPrice()) + "元。" +
                "营养信息：热量" + n(dish.getCalories()) + "千卡，蛋白质" + n(dish.getProtein()) + "克，脂肪" + n(dish.getFat()) + "克，碳水" + n(dish.getCarbohydrate()) + "克。" +
                "口味特点：" + n(dish.getTaste()) + "。" +
                "适合人群：" + n(dish.getSuitablePeople()) + "。" +
                "推荐场景：" + n(dish.getScene()) + "。" +
                "菜品标签：" + n(dish.getTags()) + "。" +
                "菜品描述：" + n(dish.getDescription());
    }

    private String n(Object o) {
        return o == null ? "" : o.toString();
    }
}
