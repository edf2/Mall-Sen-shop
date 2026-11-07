package com.mallsen.config;

import com.mallsen.interceptor.JwtTokenAdminInterceptor;
import com.mallsen.interceptor.JwtTokenUserInterceptor;
import com.mallsen.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Value("${mallsen.upload.local-path:./uploads}")
    private String localPath;

    @Value("${mallsen.upload.url-prefix:/upload}")
    private String urlPrefix;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/employee/login");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login")
                .excludePathPatterns("/user/shop/status")
                .excludePathPatterns("/user/shop/phone")
                // 商品/分类浏览免登录：用户可在未登录状态下浏览菜单门面
                .excludePathPatterns("/user/category/list")
                .excludePathPatterns("/user/product/list");
    }

    @Override
    public void extendMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        log.info("扩展消息转换器...");
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setObjectMapper(new JacksonObjectMapper());
        converters.add(0, jsonConverter);
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // 映射本地上传的图片资源
        // /upload/** 的请求映射到 file:./uploads/ 目录
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations("file:" + localPath + "/");
        log.info("添加本地上传资源映射: {}/** -> file:{}/", urlPrefix, localPath);
    }
}