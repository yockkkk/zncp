package com.example.foodrecommend.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.OssConfig;
import com.example.foodrecommend.service.OssService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class OssServiceImpl implements OssService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",  // 图片
            "mp4", "avi", "mov", "webm",                   // 视频
            "pdf", "doc", "docx", "xls", "xlsx",           // 文档
            "mp3", "wav", "m4a", "aac", "ogg", "amr", "flac" // 音频
    );

    private final OssConfig ossConfig;

    @Value("${upload.local-path:./uploads}")
    private String localPath;

    @Value("${upload.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    public OssServiceImpl(OssConfig ossConfig) {
        this.ossConfig = ossConfig;
    }

    @PostConstruct
    public void init() {
        if (ossConfig.isEnabled()) {
            log.info("OSS 文件上传服务已启用 (endpoint={}, bucket={})",
                    ossConfig.getEndpoint(), ossConfig.getBucketName());
        } else {
            log.info("OSS 未启用，使用本地文件存储");
        }
    }

    private OSS getOssClient() {
        if (!ossConfig.isEnabled()) {
            return null;
        }
        return new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret()
        );
    }

    @Override
    public String uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ext.isEmpty() && !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext + "，允许类型: " + ALLOWED_EXTENSIONS);
        }
        ext = ext.isEmpty() ? "" : "." + ext;
        String fileName = "recommend/" + UUID.randomUUID().toString().replace("-", "") + ext;

        if (ossConfig.isEnabled()) {
            return uploadToOss(file, fileName);
        }
        return uploadToLocal(file, fileName);
    }

    private String uploadToOss(MultipartFile file, String fileName) {
        OSS client = getOssClient();
        if (client == null) {
            throw new BusinessException("OSS 客户端初始化失败");
        }
        try (InputStream is = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            PutObjectRequest putRequest = new PutObjectRequest(
                    ossConfig.getBucketName(), fileName, is, metadata);

            client.putObject(putRequest);
            
            // 生成 24 小时失效的签名 URL，避免私有 Bucket 导致外部 ASR 解析抛出 url error
            java.util.Date expiration = new java.util.Date(System.currentTimeMillis() + 24 * 3600 * 1000L);
            String url = client.generatePresignedUrl(ossConfig.getBucketName(), fileName, expiration).toString();
            log.info("OSS 上传及签名成功: {}", url);
            return url;
        } catch (IOException e) {
            throw new BusinessException("OSS 上传失败: " + e.getMessage());
        } finally {
            client.shutdown();
        }
    }

    private String uploadToLocal(MultipartFile file, String fileName) {
        File dir = new File(localPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("本地存储目录创建失败");
        }
        try {
            File dest = new File(dir, fileName.substring(fileName.lastIndexOf("/") + 1));
            file.transferTo(dest);
            return baseUrl + "/" + dest.getName();
        } catch (IOException e) {
            throw new BusinessException("本地文件保存失败: " + e.getMessage());
        }
    }
}
