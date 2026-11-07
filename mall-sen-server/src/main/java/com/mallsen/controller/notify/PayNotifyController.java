package com.mallsen.controller.notify;

import com.mallsen.properties.PayProperties;
import com.mallsen.result.Result;
import com.mallsen.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 支付回调相关接口。
 * <p>
 * 说明：真实微信回调路由 /notify/paySuccess 已删除。原实现只解密不验签
 * （未校验回调头 Wechatpay-Signature 与平台证书），任何人拿到 apiV3Key 即可伪造回调
 * 把任意订单标记为已支付，属于不可接受的裸奔风险；且当前无微信企业资质，该路由本是死代码。
 * 如需接入真实微信支付，应先开通企业认证，再实现「验签 + 解密」的合规回调（可参考
 * wechatpay-apache-httpclient 自动验签），此处不保留不安全路由。
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private PayProperties payProperties;

    /**
     * 模拟支付成功回调（仅 mallsen.pay.mock=true 时可用）
     * 用于在无微信商户认证的演示/联调环境模拟微信支付回调，完成支付闭环
     *
     * @param orderNumber 商户平台订单号
     */
    @PostMapping("/mock/paySuccess")
    public Result<?> mockPaySuccessNotify(HttpServletRequest request, @RequestParam String orderNumber) {
        if (!payProperties.isMock()) {
            return Result.error("当前未开启模拟支付");
        }
        //鉴权：mock 回调必须携带合法 mock-token，否则任何人可白嫖把任意订单标记为已支付
        String provided = request.getHeader("mock-token");
        String expected = payProperties.getMockToken();
        boolean authed = provided != null && expected != null
                && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                                         expected.getBytes(StandardCharsets.UTF_8));
        if (!authed) {
            log.warn("模拟支付回调鉴权失败：缺少或非法 mock-token，orderNumber={}", orderNumber);
            return Result.error("回调令牌校验失败");
        }

        log.info("模拟支付成功回调：{}", orderNumber);

        //业务处理，修改订单状态、来单提醒
        orderService.paySuccess(orderNumber);
        return Result.success();
    }
}
