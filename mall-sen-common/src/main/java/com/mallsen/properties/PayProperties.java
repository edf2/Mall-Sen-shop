package com.mallsen.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mallsen.pay")
@Data
public class PayProperties {

    /**
     * true=模拟支付（无需微信商户认证，用于开发/演示，直接完成支付闭环）
     * false=真实微信支付（需企业认证开通微信支付后配置 mallsen.wechat 商户参数）
     */
    private boolean mock = true;

    /**
     * 模拟支付回调的鉴权令牌（mallsen.pay.mock=true 时生效）。
     * 客户端调用 /notify/mock/paySuccess 必须携带请求头 mock-token 且与此值一致，
     * 防止未鉴权的任何人将任意订单标记为已支付。
     */
    private String mockToken = "change-me";
}
