package com.example.foodrecommend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.common.Result;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 服务员推荐控制器
 */
@RestController
@RequestMapping("/api/waiter")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WAITER')")
public class WaiterRecommendController {

    private final RecommendService recommendService;
    private final RecommendationRecordMapper recordMapper;
    private final RecommendationFeedbackMapper feedbackMapper;
    private final DishService dishService;

    /**
     * 查看菜品列表（只读，供标签面板参考）
     */
    @GetMapping("/dishes")
    public Result<List<Dish>> listAllDishes() {
        return Result.success(dishService.listAll());
    }

    /**
     * 标签+场景推荐（5 Agent 管线）
     */
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
     * 查看自己的推荐记录
     */
    @GetMapping("/history")
    public Result<List<RecommendationRecord>> listMyHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<RecommendationRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<RecommendationRecord>()
                        .eq(RecommendationRecord::getWaiterId, principal.getUserId())
                        .orderByDesc(RecommendationRecord::getCreateTime)
                        .last("LIMIT 50")
        );
        return Result.success(records);
    }

    /**
     * 获取推荐详情
     */
    @GetMapping("/history/{id}")
    public Result<RecommendationRecord> getRecordDetail(@PathVariable Long id) {
        RecommendationRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        return Result.success(record);
    }

    /**
     * 反馈推荐结果（采纳/不采纳）
     */
    @PostMapping("/feedback/{recordId}")
    public Result<String> submitFeedback(
            @PathVariable Long recordId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        RecommendationRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("推荐记录不存在");
        }

        Boolean adopted = (Boolean) body.get("adopted");
        Object adoptedDishIdObj = body.get("adoptedDishId");

        // 更新推荐记录
        record.setAdopted(adopted != null && adopted ? 1 : 0);
        if (adoptedDishIdObj != null) {
            record.setAdoptedDishId(Long.valueOf(adoptedDishIdObj.toString()));
        }
        recordMapper.updateById(record);

        // 保存反馈详情
        RecommendationFeedback feedback = new RecommendationFeedback();
        feedback.setRecordId(recordId);
        feedback.setWaiterId(principal.getUserId());
        if (adoptedDishIdObj != null) {
            feedback.setAdoptedDishId(Long.valueOf(adoptedDishIdObj.toString()));
        }
        if (body.get("rating") != null) {
            feedback.setRating(Integer.valueOf(body.get("rating").toString()));
        }
        if (body.get("note") != null) {
            feedback.setNote(body.get("note").toString());
        }
        feedbackMapper.insert(feedback);

        return Result.success("反馈成功", null);
    }
}
