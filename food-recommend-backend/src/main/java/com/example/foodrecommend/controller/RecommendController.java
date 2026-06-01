package com.example.foodrecommend.controller;

import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.dto.RecommendResultDTO;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final RecommendationRecordMapper recordMapper;

    @PostMapping("/recommend/image")
    public Result<RecommendResultDTO> recommendByImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {
        RecommendResultDTO result = recommendService.recommendByImage(file, userId);
        return Result.success("推荐成功", result);
    }

    @GetMapping("/recommend/history")
    public Result<List<RecommendationRecord>> listHistory() {
        List<RecommendationRecord> records = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecommendationRecord>()
                        .orderByDesc(RecommendationRecord::getCreateTime)
                        .last("LIMIT 50")
        );
        return Result.success(records);
    }

    @PostMapping("/admin/dish/vector/batch-rebuild")
    public Result<String> batchRebuildVectors() {
        recommendService.batchRebuildVectors();
        return Result.success("批量向量生成完成", null);
    }

    @PostMapping("/admin/dish/{id}/vector/rebuild")
    public Result<String> rebuildDishVector(@PathVariable Long id) {
        recommendService.rebuildDishVector(id);
        return Result.success("向量重建完成", null);
    }
}
