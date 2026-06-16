package com.example.foodrecommend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.dto.AnalyticsDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.entity.RecommendationFeedback;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.entity.User;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.mapper.RecommendationFeedbackMapper;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.mapper.UserMapper;
import com.example.foodrecommend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 老板控制器：数据分析 + 员工管理 + 全部记录
 */
@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAnalyticsController {

    private final RecommendationRecordMapper recordMapper;
    private final RecommendationFeedbackMapper feedbackMapper;
    private final DishMapper dishMapper;
    private final UserMapper userMapper;
    private final UserService userService;

    /**
     * 数据概览
     */
    @Transactional(readOnly = true)
    @GetMapping("/analytics/overview")
    public Result<AnalyticsDTO> getOverview() {
        List<RecommendationRecord> allRecords = recordMapper.selectList(
                new LambdaQueryWrapper<RecommendationRecord>()
                        .orderByDesc(RecommendationRecord::getCreateTime)
                        .last("LIMIT 10000")
        );
        long totalRecs = allRecords.size();
        long adoptedCount = allRecords.stream()
                .filter(r -> r.getAdopted() != null && r.getAdopted() == 1)
                .count();
        double adoptionRate = totalRecs > 0 ?
                Math.round(adoptedCount * 10000.0 / totalRecs) / 100.0 : 0;

        long totalDishes = dishMapper.selectCount(null);

        long activeWaiters = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "WAITER")
                        .eq(User::getStatus, 1)
        );

        AnalyticsDTO dto = new AnalyticsDTO();
        dto.setTotalRecommendations(totalRecs);
        dto.setAdoptionRate(adoptionRate);
        dto.setTotalDishes(totalDishes);
        dto.setActiveWaiters(activeWaiters);

        // 热门菜品
        dto.setTopDishes(computeTopDishes(allRecords));
        // 服务员表现
        dto.setWaiterStats(computeWaiterStats(allRecords));

        return Result.success(dto);
    }

    /**
     * 全部推荐记录（附带反馈明细）
     */
    @GetMapping("/records")
    public Result<List<RecommendationRecord>> listAllRecords(
            @RequestParam(value = "waiterId", required = false) Long waiterId,
            @RequestParam(value = "adopted", required = false) Integer adopted) {

        LambdaQueryWrapper<RecommendationRecord> wrapper =
                new LambdaQueryWrapper<RecommendationRecord>()
                        .orderByDesc(RecommendationRecord::getCreateTime);

        if (waiterId != null) {
            wrapper.eq(RecommendationRecord::getWaiterId, waiterId);
        }
        if (adopted != null) {
            wrapper.eq(RecommendationRecord::getAdopted, adopted);
        }
        wrapper.last("LIMIT 200");

        List<RecommendationRecord> records = recordMapper.selectList(wrapper);

        // 批量加载反馈明细（与服务端逻辑一致）
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
     * 员工管理：列表
     */
    @GetMapping("/staff")
    public Result<List<User>> listStaff() {
        return Result.success(userService.listWaiters());
    }

    /**
     * 员工管理：新增
     */
    @PostMapping("/staff")
    public Result<User> createStaff(@RequestBody Map<String, String> body) {
        User user = userService.createWaiter(
                body.get("username"),
                body.get("password"),
                body.get("realName"),
                body.get("phone")
        );
        return Result.success("创建成功", user);
    }

    /**
     * 员工管理：启用/禁用
     */
    @PutMapping("/staff/{id}/status")
    public Result<String> updateStaffStatus(@PathVariable Long id,
                                             @RequestBody Map<String, Integer> body) {
        userService.updateStatus(id, body.get("status"));
        return Result.success("操作成功", null);
    }

    /**
     * 员工管理：删除
     */
    @DeleteMapping("/staff/{id}")
    public Result<String> deleteStaff(@PathVariable Long id) {
        userService.deleteWaiter(id);
        return Result.success("删除成功", null);
    }

    // ========== 私有统计方法 ==========

    private List<AnalyticsDTO.DishStat> computeTopDishes(List<RecommendationRecord> records) {
        Map<Long, long[]> dishStats = new HashMap<>(); // dishId -> [recommendCount, adoptedCount]

        // 先加载所有采纳反馈，构建 recordId → Set<adoptedDishId>
        Map<Long, Set<Long>> adoptedMap = new HashMap<>();
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(RecommendationRecord::getId).collect(Collectors.toList());
            List<RecommendationFeedback> feedbacks = feedbackMapper.selectList(
                    new LambdaQueryWrapper<RecommendationFeedback>()
                            .in(RecommendationFeedback::getRecordId, recordIds)
                            .isNotNull(RecommendationFeedback::getAdoptedDishId)
            );
            for (RecommendationFeedback fb : feedbacks) {
                adoptedMap.computeIfAbsent(fb.getRecordId(), k -> new HashSet<>())
                        .add(fb.getAdoptedDishId());
            }
        }

        for (RecommendationRecord r : records) {
            if (r.getRecommendedDishIds() == null || r.getRecommendedDishIds().isEmpty()) continue;
            Set<Long> adoptedSet = adoptedMap.getOrDefault(r.getId(), Collections.emptySet());
            String[] ids = r.getRecommendedDishIds().split(",");
            for (String idStr : ids) {
                try {
                    Long dishId = Long.parseLong(idStr.trim());
                    long[] stats = dishStats.computeIfAbsent(dishId, k -> new long[2]);
                    stats[0]++; // 推荐次数
                    if (adoptedSet.contains(dishId)) {
                        stats[1]++; // 采纳次数
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return dishStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(10)
                .map(e -> {
                    AnalyticsDTO.DishStat stat = new AnalyticsDTO.DishStat();
                    stat.setDishId(e.getKey());
                    Dish dish = dishMapper.selectById(e.getKey());
                    stat.setDishName(dish != null ? dish.getName() : "未知菜品");
                    stat.setRecommendCount(e.getValue()[0]);
                    stat.setAdoptedCount(e.getValue()[1]);
                    return stat;
                })
                .collect(Collectors.toList());
    }

    private List<AnalyticsDTO.WaiterStat> computeWaiterStats(List<RecommendationRecord> records) {
        Map<Long, long[]> waiterStats = new HashMap<>(); // waiterId -> [total, adopted]

        for (RecommendationRecord r : records) {
            if (r.getWaiterId() == null) continue;
            long[] stats = waiterStats.computeIfAbsent(r.getWaiterId(), k -> new long[2]);
            stats[0]++;
            if (r.getAdopted() != null && r.getAdopted() == 1) {
                stats[1]++;
            }
        }

        return waiterStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .map(e -> {
                    AnalyticsDTO.WaiterStat stat = new AnalyticsDTO.WaiterStat();
                    stat.setWaiterId(e.getKey());
                    User user = userMapper.selectById(e.getKey());
                    stat.setWaiterName(user != null ? user.getRealName() : "未知员工");
                    stat.setTotalRecs(e.getValue()[0]);
                    stat.setAdoptedCount(e.getValue()[1]);
                    stat.setAdoptionRate(e.getValue()[0] > 0 ?
                            Math.round(e.getValue()[1] * 10000.0 / e.getValue()[0]) / 100.0 : 0.0);
                    return stat;
                })
                .collect(Collectors.toList());
    }
}
