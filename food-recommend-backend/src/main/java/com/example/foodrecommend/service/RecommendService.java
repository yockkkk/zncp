package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.RecommendRequestDTO;
import com.example.foodrecommend.dto.RecommendResultDTO;
import com.example.foodrecommend.dto.RecommendWithScriptDTO;
import org.springframework.web.multipart.MultipartFile;

public interface RecommendService {
    /** 旧版图片推荐（保持兼容） */
    RecommendResultDTO recommendByImage(MultipartFile file, Long userId);

    /** 新版标签+场景推荐（5 Agent 管线） */
    RecommendWithScriptDTO recommendByTags(RecommendRequestDTO request,
                                            MultipartFile sceneImage,
                                            Long waiterId);

    /** 语音推荐（Agent 0 语音理解 + 5 Agent 管线） */
    RecommendWithScriptDTO recommendByVoice(String voiceText,
                                             MultipartFile sceneImage,
                                             Long waiterId);

    /** 音频转文字（使用阿里云百炼 ASR 接口） */
    String transcribeAudio(MultipartFile audioFile);

    void batchRebuildVectors();

    void rebuildDishVector(Long dishId);
}
