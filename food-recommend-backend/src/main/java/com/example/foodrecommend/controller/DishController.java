package com.example.foodrecommend.controller;

import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.service.DishService;
import com.example.foodrecommend.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜品管理控制器（老板权限）
 * 同时映射 /api/owner/dishes 和 /api/admin/dish 以保持向后兼容
 */
@Tag(name = "菜品", description = "菜品增删改查、向量重建（老板权限）")
@RestController
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;
    private final RecommendService recommendService;

    @Operation(summary = "菜品列表", description = "获取全部菜品列表")
    @GetMapping({"/api/owner/dishes", "/api/admin/dish"})
    @PreAuthorize("hasRole('OWNER')")
    public Result<List<Dish>> list() {
        return Result.success(dishService.listAll());
    }

    @Operation(summary = "菜品详情", description = "按ID获取菜品详情")
    @GetMapping({"/api/owner/dishes/{id}", "/api/admin/dish/{id}"})
    @PreAuthorize("hasRole('OWNER')")
    public Result<Dish> getById(@PathVariable Long id) {
        return Result.success(dishService.getById(id));
    }

    @Operation(summary = "新增菜品", description = "新增一道菜品")
    @PostMapping({"/api/owner/dishes", "/api/admin/dish"})
    @PreAuthorize("hasRole('OWNER')")
    public Result<Dish> add(@Valid @RequestBody Dish dish) {
        return Result.success("新增成功", dishService.addDish(dish));
    }

    @Operation(summary = "修改菜品", description = "按ID修改菜品信息")
    @PutMapping({"/api/owner/dishes/{id}", "/api/admin/dish/{id}"})
    @PreAuthorize("hasRole('OWNER')")
    public Result<Dish> update(@PathVariable Long id, @Valid @RequestBody Dish dish) {
        return Result.success("修改成功", dishService.updateDish(id, dish));
    }

    @Operation(summary = "删除菜品", description = "按ID删除菜品")
    @DeleteMapping({"/api/owner/dishes/{id}", "/api/admin/dish/{id}"})
    @PreAuthorize("hasRole('OWNER')")
    public Result<Void> delete(@PathVariable Long id) {
        dishService.deleteDish(id);
        return Result.success("删除成功", null);
    }

    /** 批量重建向量 */
    @Operation(summary = "批量重建向量", description = "批量重建全部菜品的向量索引")
    @PostMapping({"/api/owner/dishes/vector/batch-rebuild", "/api/admin/dish/vector/batch-rebuild"})
    @PreAuthorize("hasRole('OWNER')")
    public Result<String> batchRebuildVectors() {
        recommendService.batchRebuildVectors();
        return Result.success("批量向量生成完成", null);
    }

    /** 单个菜品向量重建 */
    @Operation(summary = "单菜品向量重建", description = "重建指定菜品的向量索引")
    @PostMapping({"/api/owner/dishes/{id}/vector/rebuild", "/api/admin/dish/{id}/vector/rebuild"})
    @PreAuthorize("hasRole('OWNER')")
    public Result<String> rebuildDishVector(@PathVariable Long id) {
        recommendService.rebuildDishVector(id);
        return Result.success("向量重建完成", null);
    }
}
