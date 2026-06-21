package com.example.foodrecommend.controller;

import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.dto.RecommendResultDTO;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 旧版推荐控制器（保持向后兼容）
 * 推荐使用新的 WaiterRecommendController (/api/waiter/*)
 */
@Tag(name = "推荐-兼容", description = "旧版推荐接口（已废弃，兼容保留）")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WAITER', 'OWNER')")
public class RecommendController {

    private final RecommendService recommendService;
    private final RecommendationRecordMapper recordMapper;

    /** @deprecated 使用 POST /api/waiter/recommend (标签+场景) */
    @Operation(summary = "图片推荐（已废弃）", description = "已废弃，请使用 POST /api/waiter/recommend")
    @Deprecated
    @PostMapping("/recommend/image")
    public Result<RecommendResultDTO> recommendByImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {
        RecommendResultDTO result = recommendService.recommendByImage(file, userId);
        return Result.success("推荐成功", result);
    }

    /** @deprecated 使用 GET /api/waiter/history */
    @Operation(summary = "推荐历史（已废弃）", description = "已废弃，请使用 GET /api/waiter/history")
    @Deprecated
    @GetMapping("/recommend/history")
    public Result<List<RecommendationRecord>> listHistory() {
        List<RecommendationRecord> records = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecommendationRecord>()
                        .orderByDesc(RecommendationRecord::getCreateTime)
                        .last("LIMIT 50")
        );
        return Result.success(records);
    }
}
