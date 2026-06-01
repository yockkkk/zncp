package com.example.foodrecommend.controller;

import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.service.DishService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dish")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;

    @GetMapping
    public Result<List<Dish>> list() {
        return Result.success(dishService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Dish> getById(@PathVariable Long id) {
        return Result.success(dishService.getById(id));
    }

    @PostMapping
    public Result<Dish> add(@RequestBody Dish dish) {
        return Result.success("新增成功", dishService.addDish(dish));
    }

    @PutMapping("/{id}")
    public Result<Dish> update(@PathVariable Long id, @RequestBody Dish dish) {
        return Result.success("修改成功", dishService.updateDish(id, dish));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dishService.deleteDish(id);
        return Result.success("删除成功", null);
    }
}
