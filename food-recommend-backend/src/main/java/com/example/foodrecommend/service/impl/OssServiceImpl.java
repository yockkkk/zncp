package com.example.foodrecommend.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.OssConfig;
import com.example.foodrecommend.service.OssService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
public class OssServiceImpl implements OssService {

    private final OssConfig ossConfig;
    private OSS ossClient;

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
            ossClient = new OSSClientBuilder().build(
                    ossConfig.getEndpoint(),
                    ossConfig.getAccessKeyId(),
                    ossConfig.getAccessKeySecret()
            );
            log.info("OSS 客户端初始化成功, endpoint={}, bucket={}",
                    ossConfig.getEndpoint(), ossConfig.getBucketName());
        } else {
            log.info("OSS 未启用，使用本地文件存储");
        }
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Override
    public String uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = "recommend/" + UUID.randomUUID().toString().replace("-", "") + ext;

        if (ossConfig.isEnabled() && ossClient != null) {
            return uploadToOss(file, fileName);
        }
        return uploadToLocal(file, fileName);
    }

    private String uploadToOss(MultipartFile file, String fileName) {
        try (InputStream is = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectAcl(CannedAccessControlList.PublicRead);

            PutObjectRequest putRequest = new PutObjectRequest(
                    ossConfig.getBucketName(), fileName, is, metadata);

            ossClient.putObject(putRequest);
            String url = "https://" + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + fileName;
            log.info("OSS 上传成功: {}", url);
            return url;
        } catch (IOException e) {
            throw new BusinessException("OSS 上传失败: " + e.getMessage());
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
