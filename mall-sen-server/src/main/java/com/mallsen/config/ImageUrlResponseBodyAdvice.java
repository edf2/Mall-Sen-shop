package com.mallsen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Iterator;

/**
 * 全局响应体增强：自动将API返回中的相对图片URL转为完整URL
 * 解决微信小程序无法加载相对路径图片的问题
 */
@RestControllerAdvice
@Slf4j
public class ImageUrlResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private com.mallsen.properties.LocalUploadProperties localUploadProperties;

    private final ObjectMapper objectMapper;

    public ImageUrlResponseBodyAdvice() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String serverUrl = localUploadProperties.getServerUrl();
        if (serverUrl == null || serverUrl.isEmpty()) {
            return body;
        }

        try {
            String json = objectMapper.writeValueAsString(body);
            String replaced = replaceRelativeImageUrls(json, serverUrl);
            return objectMapper.readTree(replaced);
        } catch (Exception e) {
            log.warn("图片URL替换失败，返回原始数据: {}", e.getMessage());
            return body;
        }
    }

    /**
     * 递归遍历JSON，将相对图片URL替换为完整URL
     */
    private String replaceRelativeImageUrls(String json, String serverUrl) {
        try {
            JsonNode node = objectMapper.readTree(json);
            processNode(node, serverUrl);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return json;
        }
    }

    private void processNode(JsonNode node, String serverUrl) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<String> fieldNames = objectNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode child = objectNode.get(fieldName);
                if (child.isTextual()) {
                    String value = child.asText();
                    if (isRelativeImageUrl(value)) {
                        objectNode.put(fieldName, serverUrl + value);
                    }
                } else {
                    processNode(child, serverUrl);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode child = arrayNode.get(i);
                if (child.isTextual()) {
                    String value = child.asText();
                    if (isRelativeImageUrl(value)) {
                        arrayNode.set(i, objectMapper.getNodeFactory().textNode(serverUrl + value));
                    }
                } else {
                    processNode(child, serverUrl);
                }
            }
        }
    }

    /**
     * 判断是否为相对图片URL（以 /upload/ 开头）
     */
    private boolean isRelativeImageUrl(String value) {
        return value != null && value.startsWith("/upload/") && !value.startsWith("http");
    }
}
