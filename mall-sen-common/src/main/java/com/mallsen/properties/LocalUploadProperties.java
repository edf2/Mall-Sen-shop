package com.mallsen.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地文件上传配置（临时替代阿里云 OSS）
 */
@Component
@ConfigurationProperties(prefix = "mallsen.upload")
@Data
public class LocalUploadProperties {

    /**
     * 本地文件存储的绝对/相对路径（相对路径相对于应用启动目录）。
     * 目录不存在会自动创建。
     */
    private String localPath = "./uploads";

    /**
     * 对外暴露的 URL 访问前缀，需与 WebMvc 的静态资源映射保持一致。
     * 例如：/upload  =>  实际访问地址为  http://host:port/upload/xxx.png
     */
    private String urlPrefix = "/upload";

    /**
     * 允许的最大文件大小（单位：字节），默认 10MB
     */
    private long maxSize = 10 * 1024 * 1024L;

    /**
     * 临时开关：true=把默认的 /admin/common/upload 接口也强制走本地模式（不管前端传哪个路径）。
     * 切换 OSS 时改为 false 即可。
     */
    private boolean useLocal = true;

    /**
     * 服务器对外访问的基础URL，用于拼接完整的图片访问地址。
     * 例如：http://localhost:8080，最终图片URL为 http://localhost:8080/upload/xxx.png
     * 留空则返回相对路径（向后兼容旧版行为）。
     */
    private String serverUrl = "";
}
