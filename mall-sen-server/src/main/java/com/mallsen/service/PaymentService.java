package com.mallsen.service;

import com.mallsen.entity.Orders;
import com.mallsen.vo.OrderPaymentVO;

/**
 * 支付服务抽象。
 * <p>
 * 支付链路以策略模式实现：业务层只依赖本接口，由 {@code PaymentServiceRouter}
 * 根据 {@code mallsen.pay.mock} 配置在「模拟支付」与「真实微信支付」两个实现间切换，
 * 避免业务层散落 {@code payProperties.isMock()} 分支判断。
 * <ul>
 *     <li>{@code mockPaymentService}：无微信企业认证时使用，写支付流水并返回模拟预支付参数</li>
 *     <li>{@code wechatPaymentService}：企业认证后启用，对接微信支付（APIv3），未配置时明确拒绝</li>
 * </ul>
 */
public interface PaymentService {

    /**
     * 创建支付：生成支付流水并返回预支付参数（用于小程序调起支付）。
     *
     * @param order 待支付订单
     * @return 预支付参数
     */
    OrderPaymentVO createPayment(Orders order);

    /**
     * 标记支付成功（幂等）：将订单对应的待支付流水置为已支付。
     *
     * @param orderNumber 商户平台订单号
     */
    void markPaid(String orderNumber);

    /**
     * 退款（幂等）：将已支付流水置为已退款。
     *
     * @param orderNumber 商户平台订单号
     */
    void refund(String orderNumber);
}
