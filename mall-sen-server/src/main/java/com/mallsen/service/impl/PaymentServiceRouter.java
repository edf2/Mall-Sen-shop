package com.mallsen.service.impl;

import com.mallsen.properties.PayProperties;
import com.mallsen.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付策略路由器：根据 {@code mallsen.pay.mock} 在 Mock / 微信两个实现间切换。
 * <p>
 * 业务层统一通过 {@link #getCurrent()} 获取当前生效的支付实现，不再散落
 * {@code payProperties.isMock()} 分支判断。Spring 会将容器内所有 {@code PaymentService}
 * 实现按 bean 名称注入 {@code Map<String, PaymentService>}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceRouter {

    private static final String MOCK_BEAN = "mockPaymentService";
    private static final String WECHAT_BEAN = "wechatPaymentService";

    private final PayProperties payProperties;
    private final Map<String, PaymentService> paymentServiceMap;

    /**
     * 获取当前生效的支付实现。
     *
     * @return mock=true 返回模拟支付实现，否则返回微信支付实现
     */
    public PaymentService getCurrent() {
        String bean = payProperties.isMock() ? MOCK_BEAN : WECHAT_BEAN;
        PaymentService service = paymentServiceMap.get(bean);
        if (service == null) {
            throw new IllegalStateException("支付实现缺失: " + bean);
        }
        return service;
    }
}
