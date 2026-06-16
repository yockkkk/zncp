package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.DishVectorDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.service.DishService;
import com.example.foodrecommend.service.EmbeddingService;
import com.example.foodrecommend.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;

    @Override
    public List<Dish> listAll() {
        return list();
    }

    @Override
    public Dish addDish(Dish dish) {
        // P3-23: 关键字段校验
        if (dish.getName() == null || dish.getName().isBlank()) {
            throw new BusinessException("菜品名称不能为空");
        }
        if (dish.getPrice() == null || dish.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("菜品价格必须大于0");
        }
        dish.setStatus(1);
        dish.setVectorStatus(0);
        save(dish);
        syncVector(dish);
        return dish;
    }

    @Override
    public Dish updateDish(Long id, Dish dish) {
        Dish existing = getById(id);
        if (existing == null) {
            throw new BusinessException("菜品不存在");
        }
        // P3-23: 校验更新的关键字段
        if (dish.getPrice() != null && dish.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("菜品价格必须大于0");
        }
        // 仅复制非 null 的可更新字段，避免覆盖已有数据
        if (dish.getName() != null) existing.setName(dish.getName());
        if (dish.getCategory() != null) existing.setCategory(dish.getCategory());
        if (dish.getPrice() != null) existing.setPrice(dish.getPrice());
        if (dish.getCalories() != null) existing.setCalories(dish.getCalories());
        if (dish.getProtein() != null) existing.setProtein(dish.getProtein());
        if (dish.getFat() != null) existing.setFat(dish.getFat());
        if (dish.getCarbohydrate() != null) existing.setCarbohydrate(dish.getCarbohydrate());
        if (dish.getTaste() != null) existing.setTaste(dish.getTaste());
        if (dish.getSuitablePeople() != null) existing.setSuitablePeople(dish.getSuitablePeople());
        if (dish.getScene() != null) existing.setScene(dish.getScene());
        if (dish.getTags() != null) existing.setTags(dish.getTags());
        if (dish.getImageUrl() != null) existing.setImageUrl(dish.getImageUrl());
        if (dish.getDescription() != null) existing.setDescription(dish.getDescription());
        if (dish.getStock() != null) existing.setStock(dish.getStock());
        if (dish.getStatus() != null) existing.setStatus(dish.getStatus());
        if (dish.getGrossMargin() != null) existing.setGrossMargin(dish.getGrossMargin());

        // P2-18: 仅当嵌入相关字段变更时才重新生成向量
        boolean needRebuild = dish.getName() != null
                || dish.getCategory() != null
                || dish.getPrice() != null
                || dish.getCalories() != null
                || dish.getProtein() != null
                || dish.getFat() != null
                || dish.getCarbohydrate() != null
                || dish.getTaste() != null
                || dish.getSuitablePeople() != null
                || dish.getScene() != null
                || dish.getTags() != null
                || dish.getDescription() != null;

        updateById(existing);
        if (needRebuild) {
            existing.setVectorStatus(0);
            updateById(existing);
            syncVector(existing);
        }
        return existing;
    }

    @Override
    public void deleteDish(Long id) {
        // P3-22: 重试3次删除Qdrant向量，减少孤儿向量
        for (int i = 0; i < 3; i++) {
            try {
                vectorSearchService.deleteDishVector(id);
                break;
            } catch (Exception e) {
                if (i == 2) {
                    log.error("删除Qdrant向量最终失败 dishId={}, 向量已孤立", id, e);
                } else {
                    log.warn("删除Qdrant向量失败(第{}次) dishId={}, 重试中", i + 1, id);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }
        }
        removeById(id);
    }

    @Override
    public List<Dish> listAvailable() {
        return list(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, 1)
                .gt(Dish::getStock, 0)
                .eq(Dish::getVectorStatus, 1));
    }

    @Override
    public int deductStock(Long id, int quantity) {
        Dish dish = getById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 原子扣减：使用SQL级别 SET stock = stock - quantity, sales = sales + quantity WHERE stock >= quantity
        LambdaUpdateWrapper<Dish> wrapper = new LambdaUpdateWrapper<>();
        wrapper.setSql("stock = stock - " + quantity + ", sales = sales + " + quantity)
               .eq(Dish::getId, id)
               .ge(Dish::getStock, quantity);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("库存不足，当前库存: " + (dish.getStock() == null ? 0 : dish.getStock()));
        }
        int newStock = dish.getStock() - quantity;
        log.info("菜品 {} 扣减库存 {}，剩余 {}", id, quantity, newStock);
        return newStock;
    }

    private void syncVector(Dish dish) {
        try {
            vectorSearchService.initCollection();
            String embeddingText = buildDishEmbeddingText(dish);
            List<Float> vector = embeddingService.getEmbedding(embeddingText);

            DishVectorDTO payload = new DishVectorDTO();
            payload.setDishId(dish.getId());
            payload.setName(dish.getName());
            payload.setCategory(dish.getCategory());
            double price = dish.getPrice() != null ? dish.getPrice().doubleValue() : 0;
            payload.setPrice(price);
            payload.setCalories(dish.getCalories() != null ? dish.getCalories() : 0);
            payload.setProtein(dish.getProtein() != null ? dish.getProtein().doubleValue() : 0);
            payload.setTaste(dish.getTaste());
            payload.setTags(splitToList(dish.getTags()));
            payload.setSuitablePeople(splitToList(dish.getSuitablePeople()));
            payload.setScene(splitToList(dish.getScene()));
            payload.setGrossMargin(dish.getGrossMargin() != null ? dish.getGrossMargin().doubleValue() : 0.60);

            vectorSearchService.upsertDishVector(dish.getId(), vector, payload);
            dish.setVectorStatus(1);
            updateById(dish);
            log.info("菜品 {} 向量同步成功", dish.getId());
        } catch (Exception e) {
            log.error("菜品 {} 向量同步失败: {}", dish.getId(), e.getMessage());
            dish.setVectorStatus(0);
            updateById(dish);
        }
    }

    private List<String> splitToList(String str) {
        if (str == null || str.isBlank()) return Collections.emptyList();
        return Arrays.asList(str.split(","));
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
