package com.mallsen.service.impl;

import com.mallsen.entity.Orders;
import com.mallsen.exception.OrderBusinessException;
import com.mallsen.service.PaymentService;
import com.mallsen.vo.OrderPaymentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 真实微信支付实现（策略：mallsen.pay.mock=false，需微信企业认证）。
 * <p>
 * 注意：当前项目无微信商户企业资质，该实现为「明确拒绝」的占位实现，绝不静默降级，
 * 防止把未验签/未授权的支付回调当作真实支付处理。
 * <p>
 * 后续获得企业资质后的接入点：
 * <ul>
 *     <li>{@code createPayment}：调用 {@code WeChatPayUtil.pay(orderNum, total, description, openid)}
 *         统一下单，需在订单链路中携带用户 openid（小程序端 wx.login 换得）</li>
 *     <li>{@code markPaid / refund}：由微信支付回调/退款回调驱动（回调需验签 Wechatpay-Signature
 *         与平台证书，见 wechatpay-apache-httpclient 的自动验签），此处不做本地落库死代码</li>
 * </ul>
 */
@Service("wechatPaymentService")
@Slf4j
@RequiredArgsConstructor
public class WeChatPaymentService implements PaymentService {

    /**
     * 真实微信统一下单。未开通企业认证时直接拒绝，避免返回伪造的支付参数。
     */
    @Override
    public OrderPaymentVO createPayment(Orders order) {
        throw new OrderBusinessException(
                "微信支付未开通：请完成微信商户企业认证并配置 mallsen.wechat 商户参数，或保持 mallsen.pay.mock=true 使用模拟支付");
    }

    /**
     * 真实微信支付由回调通道承载（需验签），这里不提供可被伪造的本地落库入口。
     */
    @Override
    public void markPaid(String orderNumber) {
        throw new OrderBusinessException(
                "微信支付回调未启用：当前未配置安全验签回调通道，请保持 mallsen.pay.mock=true 使用模拟支付闭环");
    }

    /**
     * 真实微信退款由退款回调驱动，这里不提供可被伪造的本地落库入口。
     */
    @Override
    public void refund(String orderNumber) {
        throw new OrderBusinessException(
                "微信退款回调未启用：当前未配置安全验签回调通道，请保持 mallsen.pay.mock=true 使用模拟退款闭环");
    }
}
