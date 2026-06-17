package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.TagInputDTO;

/**
 * Agent 0: 语音理解服务
 * 将服务员口述的自然语言转化为结构化的 TagInputDTO
 */
public interface VoiceUnderstandingService {
    /**
     * 解析语音文本，提取标签
     * @param voiceText 语音识别后的文本
     * @return 结构化的标签输入，解析失败返回 null
     */
    TagInputDTO parseVoiceText(String voiceText);
}
