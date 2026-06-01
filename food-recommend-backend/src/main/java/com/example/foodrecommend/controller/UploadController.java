package com.example.foodrecommend.controller;

import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.service.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final OssService ossService;

    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        String url = ossService.uploadFile(file);
        return Result.success("上传成功", url);
    }
}
