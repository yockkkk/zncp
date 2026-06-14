package com.example.foodrecommend.dto;

import lombok.Data;

/**
 * 推荐请求 DTO
 * 服务员通过标签 + 可选场景照片发起推荐
 */
@Data
public class RecommendRequestDTO {
    /** 标签面板输入的 JSON 字符串 */
    private String tagInputJson;
}
