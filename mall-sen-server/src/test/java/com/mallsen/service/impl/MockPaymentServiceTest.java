package com.mallsen.service.impl;

import com.mallsen.entity.Orders;
import com.mallsen.entity.PayRecord;
import com.mallsen.exception.OrderBusinessException;
import com.mallsen.mapper.PayRecordMapper;
import com.mallsen.properties.PayProperties;
import com.mallsen.vo.OrderPaymentVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟支付流水测试：创建支付返回预支付参数、重复支付流水拦截、支付/退款幂等更新。
 */
@ExtendWith(MockitoExtension.class)
class MockPaymentServiceTest {

    @Mock
    private PayRecordMapper payRecordMapper;

    @Mock
    private PayProperties payProperties;

    @InjectMocks
    private MockPaymentService service;

    private Orders order() {
        return Orders.builder()
                .id(1L)
                .number("20260101120000000012345")
                .amount(BigDecimal.valueOf(99.5))
                .payMethod(1)
                .build();
    }

    @Test
    void createPayment_returnsPrepayParams() {
        when(payProperties.isMock()).thenReturn(true);
        when(payRecordMapper.getPendingByOrderNumber(anyString())).thenReturn(null);

        OrderPaymentVO vo = service.createPayment(order());

        assertNotNull(vo.getPackageStr());
        assertTrue(vo.getPackageStr().startsWith("prepay_id=mock_"));
        verify(payRecordMapper).insert(any(PayRecord.class));
    }

    @Test
    void createPayment_rejectsExistingPendingRecord() {
        when(payProperties.isMock()).thenReturn(true);
        when(payRecordMapper.getPendingByOrderNumber(anyString()))
                .thenReturn(PayRecord.builder().build());

        assertThrows(OrderBusinessException.class, () -> service.createPayment(order()));

        verify(payRecordMapper, never()).insert(any(PayRecord.class));
    }

    @Test
    void createPayment_rejectsWhenMockDisabled() {
        when(payProperties.isMock()).thenReturn(false);

        assertThrows(OrderBusinessException.class, () -> service.createPayment(order()));

        verify(payRecordMapper, never()).insert(any(PayRecord.class));
    }

    @Test
    void markPaid_updatesOnlyPendingRecord() {
        when(payRecordMapper.markSuccess("2026...")).thenReturn(1);

        service.markPaid("2026...");

        verify(payRecordMapper).markSuccess("2026...");
    }

    @Test
    void refund_marksOnlyPaidRecord() {
        when(payRecordMapper.markRefunded(anyString())).thenReturn(1);

        service.refund("2026...");

        verify(payRecordMapper).markRefunded("2026...");
    }
}
