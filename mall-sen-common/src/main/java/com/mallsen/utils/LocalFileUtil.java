package com.mallsen.utils;

import com.mallsen.properties.LocalUploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件上传工具类
 */
@Component
@Slf4j
public class LocalFileUtil {

    @Autowired
    private LocalUploadProperties localUploadProperties;

    private String localPath;
    private String urlPrefix;
    private String serverUrl;

    @PostConstruct
    public void init() {
        this.localPath = localUploadProperties.getLocalPath();
        this.urlPrefix = localUploadProperties.getUrlPrefix();
        this.serverUrl = localUploadProperties.getServerUrl();

        // 确保上传目录存在
        File uploadDir = new File(localPath);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (created) {
                log.info("创建本地上传目录: {}", localPath);
            }
        }
        log.info("本地文件上传工具初始化完成，存储路径: {}, 访问前缀: {}, 服务器地址: {}", localPath, urlPrefix, serverUrl);
    }

    /**
     * 上传文件到本地
     *
     * @param bytes      文件字节数组
     * @param objectName 文件名（含扩展名）
     * @return 文件访问路径
     */
    public String upload(byte[] bytes, String objectName) {
        try {
            // 构建完整的文件路径
            Path filePath = Paths.get(localPath, objectName);

            // 写入文件
            Files.write(filePath, bytes);

            // 构建访问URL：如果配置了serverUrl则返回完整URL，否则返回相对路径
            String url;
            if (serverUrl != null && !serverUrl.isEmpty()) {
                url = serverUrl + urlPrefix + "/" + objectName;
            } else {
                url = urlPrefix + "/" + objectName;
            }
            log.info("文件上传成功: {}", url);
            return url;
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 删除文件
     *
     * @param fileName 文件名
     */
    public void deleteFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }

        // 从URL中提取文件名
        String actualFileName = fileName;
        if (fileName.contains("/")) {
            actualFileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }

        Path filePath = Paths.get(localPath, actualFileName);
        try {
            Files.deleteIfExists(filePath);
            log.info("文件删除成功: {}", actualFileName);
        } catch (IOException e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
        }
    }
}
