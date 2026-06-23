package com.example.foodrecommend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.dto.FeedbackRequestDTO;
import com.example.foodrecommend.dto.RecommendRequestDTO;
import com.example.foodrecommend.dto.RecommendWithScriptDTO;
import com.example.foodrecommend.entity.RecommendationFeedback;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.RecommendationFeedbackMapper;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.security.UserPrincipal;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.service.DishService;
import com.example.foodrecommend.service.RecommendService;
import com.example.foodrecommend.service.RecommendationHistoryService;
import com.example.foodrecommend.service.UserProfileService;
import com.example.foodrecommend.dto.UserProfileDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 服务员推荐控制器
 */
@Tag(name = "服务员-推荐", description = "推荐、历史记录、反馈、营业额")
@Slf4j
@RestController
@RequestMapping("/api/waiter")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WAITER')")
public class WaiterRecommendController {

    private final RecommendService recommendService;
    private final RecommendationRecordMapper recordMapper;
    private final RecommendationFeedbackMapper feedbackMapper;
    private final DishService dishService;
    private final UserProfileService userProfileService;
    private final RecommendationHistoryService historyService;

    /**
     * 查看可推荐菜品列表（只显示上架 + 有库存 + 向量已生成的）
     */
    @Operation(summary = "可推荐菜品列表", description = "只显示上架、有库存、向量已生成的菜品")
    @GetMapping("/dishes")
    public Result<List<Dish>> listAllDishes() {
        return Result.success(dishService.listAvailable());
    }

    /**
     * 标签+场景推荐（5 Agent 管线）
     */
    @Operation(summary = "标签+场景推荐", description = "5 Agent 推荐管线，输入标签JSON和可选场景图片")
    @PostMapping("/recommend")
    public Result<RecommendWithScriptDTO> recommend(
            @RequestParam("tagInputJson") String tagInputJson,
            @RequestParam(value = "sceneImage", required = false) MultipartFile sceneImage,
            @AuthenticationPrincipal UserPrincipal principal) {

        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTagInputJson(tagInputJson);

        RecommendWithScriptDTO result = recommendService.recommendByTags(
                request, sceneImage, principal.getUserId());
        return Result.success("推荐成功", result);
    }

    /**
     * 语音推荐（Agent 0 + 5 Agent 管线）
     * 适用于微信小程序语音输入
     */
    @Operation(summary = "语音推荐", description = "Agent 0 理解语音文本 + 5 Agent 推荐管线")
    @PostMapping("/recommend/voice")
    public Result<RecommendWithScriptDTO> recommendByVoice(
            @RequestParam("voiceText") String voiceText,
            @RequestParam(value = "sceneImage", required = false) MultipartFile sceneImage,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (voiceText == null || voiceText.trim().isEmpty()) {
            throw new BusinessException("语音文本不能为空");
        }

        RecommendWithScriptDTO result = recommendService.recommendByVoice(
                voiceText, sceneImage, principal.getUserId());
        return Result.success("语音推荐成功", result);
    }

    /**
     * 根据手机号查询顾客的长期记忆/历史画像
     */
    @Operation(summary = "查询顾客画像", description = "根据手机号获取顾客长期记忆/历史偏好")
    @GetMapping("/customer/profile")
    public Result<UserProfileDTO> getCustomerProfile(@RequestParam("phone") String phone) {
        return Result.success(userProfileService.getCustomerHistoryProfile(phone));
    }

    /**
     * 查看自己的推荐记录（附带每条推荐的采纳反馈明细）
     */
    @Operation(summary = "我的推荐历史", description = "查看自己的推荐记录，含反馈明细，最近50条")
    @GetMapping("/history")
    public Result<List<RecommendationRecord>> listMyHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<RecommendationRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<RecommendationRecord>()
                        .eq(RecommendationRecord::getWaiterId, principal.getUserId())
                        .orderByDesc(RecommendationRecord::getCreateTime)
                        .last("LIMIT 50")
        );
        // 批量加载反馈明细
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(RecommendationRecord::getId).toList();
            List<RecommendationFeedback> allFeedbacks = feedbackMapper.selectList(
                    new LambdaQueryWrapper<RecommendationFeedback>()
                            .in(RecommendationFeedback::getRecordId, recordIds)
            );
            java.util.Map<Long, List<RecommendationFeedback>> feedbackMap = new java.util.HashMap<>();
            for (RecommendationFeedback fb : allFeedbacks) {
                feedbackMap.computeIfAbsent(fb.getRecordId(), k -> new java.util.ArrayList<>()).add(fb);
            }
            for (RecommendationRecord r : records) {
                r.setFeedbacks(feedbackMap.getOrDefault(r.getId(), java.util.Collections.emptyList()));
            }
        }
        return Result.success(records);
    }

    /**
     * 获取推荐详情（附带反馈明细）
     */
    @Operation(summary = "推荐详情", description = "获取单条推荐记录及其反馈明细")
    @GetMapping("/history/{id}")
    public Result<RecommendationRecord> getRecordDetail(@PathVariable Long id) {
        RecommendationRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        List<RecommendationFeedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<RecommendationFeedback>()
                        .eq(RecommendationFeedback::getRecordId, id)
                        .orderByDesc(RecommendationFeedback::getCreateTime)
        );
        record.setFeedbacks(feedbacks);
        return Result.success(record);
    }

    /**
     * 反馈推荐结果（采纳/不采纳，采纳时扣减库存）
     * 一条推荐可采纳多道不同菜品，但同一道菜品不可重复采纳
     */
    @Operation(summary = "提交推荐反馈", description = "采纳/不采纳推荐结果，采纳时自动扣减库存")
    @Transactional
    @PostMapping("/feedback/{recordId}")
    public Result<String> submitFeedback(
            @PathVariable Long recordId,
            @Valid @RequestBody FeedbackRequestDTO req,
            @AuthenticationPrincipal UserPrincipal principal) {

        RecommendationRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("推荐记录不存在");
        }

        boolean isAdopted = Boolean.TRUE.equals(req.getAdopted());
        Long adoptedDishId = req.getAdoptedDishId();
        int quantity = (req.getQuantity() != null && req.getQuantity() > 0) ? req.getQuantity() : 1;

        // 防止同一道菜品被重复采纳
        if (isAdopted && adoptedDishId != null) {
            Long count = feedbackMapper.selectCount(
                    new LambdaQueryWrapper<RecommendationFeedback>()
                            .eq(RecommendationFeedback::getRecordId, recordId)
                            .eq(RecommendationFeedback::getAdoptedDishId, adoptedDishId)
            );
            if (count != null && count > 0) {
                throw new BusinessException("该菜品已采纳过，不能重复采纳");
            }
        }

        // 采纳时校验菜品存在且库存充足
        if (isAdopted && adoptedDishId != null) {
            Dish adoptedDish = dishService.getById(adoptedDishId);
            if (adoptedDish == null) {
                throw new BusinessException("菜品不存在");
            }
            if (adoptedDish.getStock() == null || adoptedDish.getStock() < quantity) {
                throw new BusinessException("库存不足");
            }
            dishService.deductStock(adoptedDishId, quantity);
        }

        // 更新推荐记录
        if (isAdopted && adoptedDishId != null) {
            record.setAdopted(1);
            record.setAdoptedDishId(adoptedDishId);
            record.setAdoptedQuantity(quantity);
            recordMapper.updateById(record);
        }

        // 保存反馈
        RecommendationFeedback feedback = new RecommendationFeedback();
        feedback.setRecordId(recordId);
        feedback.setWaiterId(principal.getUserId());
        feedback.setAdoptedDishId(adoptedDishId);
        feedback.setQuantity(isAdopted ? quantity : null);
        feedback.setRating(req.getRating());
        feedback.setNote(req.getNote());
        feedbackMapper.insert(feedback);

        // 反馈反哺：异步写入历史向量库（@Async + @Retryable，非阻塞）
        if (isAdopted && adoptedDishId != null) {
            try {
                historyService.indexAdoption(
                        recordId,
                        record.getQueryText(),
                        java.util.List.of(adoptedDishId),
                        principal.getUserId());
            } catch (Exception ex) {
                log.warn("[{}] indexAdoption invocation failed (non-blocking): {}",
                        org.slf4j.MDC.get("traceId"), ex.getMessage());
            }
        }

        return Result.success(isAdopted ? "采纳成功，已扣减库存" : "反馈成功", null);
    }

    /**
     * 获取服务员自己的营业额
     */
    @Operation(summary = "我的营业额", description = "统计服务员本人的推荐带来的营业额")
    @GetMapping("/revenue")
    public Result<Map<String, Object>> getMyRevenue(@AuthenticationPrincipal UserPrincipal principal) {
        List<RecommendationFeedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<RecommendationFeedback>()
                        .eq(RecommendationFeedback::getWaiterId, principal.getUserId())
                        .isNotNull(RecommendationFeedback::getAdoptedDishId)
        );
        List<Dish> allDishes = dishService.listAll();
        java.util.Map<Long, java.math.BigDecimal> dishPriceMap = new java.util.HashMap<>();
        for (Dish d : allDishes) {
            if (d.getId() != null && d.getPrice() != null) {
                dishPriceMap.put(d.getId(), d.getPrice());
            }
        }

        java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
        for (RecommendationFeedback fb : feedbacks) {
            if (fb.getAdoptedDishId() == null) continue;
            java.math.BigDecimal price = dishPriceMap.getOrDefault(fb.getAdoptedDishId(), java.math.BigDecimal.ZERO);
            int qty = fb.getQuantity() != null ? fb.getQuantity() : 1;
            totalRevenue = totalRevenue.add(price.multiply(java.math.BigDecimal.valueOf(qty)));
        }

        Map<String, Object> res = new java.util.HashMap<>();
        res.put("waiterId", principal.getUserId());
        res.put("revenue", totalRevenue);
        return Result.success(res);
    }
}
