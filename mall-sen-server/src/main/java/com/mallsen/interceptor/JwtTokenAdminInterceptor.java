package com.mallsen.interceptor;

import com.mallsen.constant.JwtClaimsConstant;
import com.mallsen.context.BaseContext;
import com.mallsen.properties.JwtProperties;
import com.mallsen.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;   // 修改：javax.servlet -> jakarta.servlet
import jakarta.servlet.http.HttpServletResponse;  // 修改：javax.servlet -> jakarta.servlet
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * jwt令牌校验的拦截器
 * <p>
 * 除常规管理端 JWT 校验外，对「Python Agent 服务间调用」的路径（/admin/business/ai/**、
 * /admin/purchase-order/**）额外放行共享密钥 x-agent-key 鉴权：共享密钥不设过期时间，
 * 取代静态 ADMIN_JWT_TOKEN（8 天 TTL 过期后商家 AI 静默失效且无刷新机制）。
 * 管理后台（Vue）仍走原 JWT 校验，不受影响。
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    private static final String AGENT_KEY_HEADER = "x-agent-key";

    /**
     * 允许 Agent 以共享密钥访问的管理端路径前缀（其余 /admin/** 仍强制 JWT）
     */
    private static final List<String> AGENT_ALLOWED_PREFIXES = Arrays.asList(
            "/admin/business/ai/",
            "/admin/purchase-order/"
    );

    @Autowired
    private JwtProperties jwtProperties;

    @Value("${agent.service.api-key:change-me}")
    private String agentApiKey;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、服务间调用（Python Agent）：命中 Agent 允许路径且携带合法共享密钥时直接放行。
        //   共享密钥取 application.yml 的 agent.service.api-key（与 Python 侧 AGENT_API_KEY 一致），
        //   无过期时间，避免 JWT 过期后商家 AI 静默失效
        if (isAgentAllowedPath(request.getRequestURI())
                && matchesAgentKey(request.getHeader(AGENT_KEY_HEADER))) {
            log.info("Agent 服务间鉴权通过: {}", request.getRequestURI());
            return true;
        }

        //2、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        //3、校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            log.info("当前员工id：{}", empId);  // 修复：添加 {} 占位符
            BaseContext.setCurrentId(empId);
            //4、通过，放行
            return true;
        } catch (Exception ex) {
            //5、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 请求结束清理 ThreadLocal，防止线程复用导致跨请求身份串号
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }

    private boolean isAgentAllowedPath(String uri) {
        if (uri == null) {
            return false;
        }
        for (String prefix : AGENT_ALLOWED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 常量时间比较共享密钥，避免时序侧信道
     */
    private boolean matchesAgentKey(String provided) {
        return provided != null && agentApiKey != null
                && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                                         agentApiKey.getBytes(StandardCharsets.UTF_8));
    }
}
