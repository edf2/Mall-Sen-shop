package com.mallsen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 代客下单成功返回的订单信息（供用户智能客服展示/支付）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOrderCreateVO implements Serializable {

    private Long orderId;

    private String orderNumber;

    private BigDecimal amount;

    private List<OrderDetailItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderDetailItem implements Serializable {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal amount;
        private String image;
    }
}
