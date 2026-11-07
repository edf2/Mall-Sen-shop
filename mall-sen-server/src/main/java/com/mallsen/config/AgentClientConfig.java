package com.mallsen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AgentClientConfig {

    /**
     * 与 Python Agent 服务约定的内部鉴权密钥（AGENT_API_KEY），
     * 所有转发给 Agent 的请求统一注入 x-agent-key 请求头。
     */
    private static final String AGENT_API_KEY_HEADER = "x-agent-key";

    @Value("${agent.service.url:http://localhost:8100}")
    private String agentServiceUrl;

    @Value("${agent.service.api-key:change-me}")
    private String agentApiKey;

    @Bean
    public WebClient agentWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(agentServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(AGENT_API_KEY_HEADER, agentApiKey)
                .build();
    }
}
