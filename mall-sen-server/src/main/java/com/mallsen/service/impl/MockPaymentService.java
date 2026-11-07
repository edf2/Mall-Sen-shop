package com.mallsen.service.impl;

import com.mallsen.entity.Orders;
import com.mallsen.entity.PayRecord;
import com.mallsen.exception.OrderBusinessException;
import com.mallsen.mapper.PayRecordMapper;
import com.mallsen.properties.PayProperties;
import com.mallsen.service.PaymentService;
import com.mallsen.vo.OrderPaymentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 模拟支付实现（策略：mallsen.pay.mock=true）。
 * 无微信商户认证（企业资质）时使用：生成 pay_record 支付流水，并返回模拟预支付参数，
 * 与 OrderServiceImpl.paySuccess() 配合完成 待支付 -> 已支付 的支付闭环。
 */
@Service("mockPaymentService")
@Slf4j
@RequiredArgsConstructor
public class MockPaymentService implements PaymentService {

    private final PayRecordMapper payRecordMapper;
    private final PayProperties payProperties;

    /**
     * 创建模拟支付：写待支付流水，返回模拟预支付参数
     */
    @Override
    public OrderPaymentVO createPayment(Orders order) {
        if (!payProperties.isMock()) {
            throw new OrderBusinessException("未开启模拟支付");
        }
        if (payRecordMapper.getPendingByOrderNumber(order.getNumber()) != null) {
            throw new OrderBusinessException("该订单存在未完成的支付流水");
        }

        String mockTransactionId = "MOCK_" + UUID.randomUUID().toString().replace("-", "");

        PayRecord record = PayRecord.builder()
                .orderId(order.getId())
                .orderNumber(order.getNumber())
                .transactionId(mockTransactionId)
                .payMethod(order.getPayMethod() == null ? 1 : order.getPayMethod())
                .amount(order.getAmount())
                .status(PayRecord.PENDING)
                .createTime(LocalDateTime.now())
                .build();
        payRecordMapper.insert(record);

        log.info("模拟支付流水已创建: orderNumber={}, transactionId={}, amount={}",
                order.getNumber(), mockTransactionId, order.getAmount());

        return OrderPaymentVO.builder()
                .nonceStr(UUID.randomUUID().toString().replace("-", "").substring(0, 32))
                .paySign("MOCK")
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .signType("HMAC-SHA256")
                .packageStr("prepay_id=mock_" + order.getNumber())
                .build();
    }

    /**
     * 模拟支付完成，将待支付流水更新为已支付（幂等）
     */
    @Override
    public void markPaid(String orderNumber) {
        int rows = payRecordMapper.markSuccess(orderNumber);
        if (rows > 0) {
            log.info("模拟支付流水已更新为已支付: orderNumber={}", orderNumber);
        }
    }

    /**
     * 模拟退款：将已支付流水更新为已退款（幂等，仅 status=1 的流水会被更新）。
     * 真实微信支付下应由微信退款回调驱动，这里是 Mock 环境下的退款落库闭环。
     */
    @Override
    public void refund(String orderNumber) {
        int rows = payRecordMapper.markRefunded(orderNumber);
        if (rows > 0) {
            log.info("模拟退款完成，支付流水已标记退款: orderNumber={}", orderNumber);
        } else {
            log.warn("模拟退款：未找到可退款的已支付流水，orderNumber={}（可能此前已退款）", orderNumber);
        }
    }
}
