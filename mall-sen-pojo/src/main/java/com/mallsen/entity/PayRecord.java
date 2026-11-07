package com.mallsen.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayRecord implements Serializable {

    /**
     * 支付状态 0待支付 1已支付 2支付失败 3已退款
     */
    public static final Integer PENDING = 0;
    public static final Integer SUCCESS = 1;
    public static final Integer FAILED = 2;
    public static final Integer REFUNDED = 3;

    private static final long serialVersionUID = 1L;

    private Long id;

    //订单id
    private Long orderId;

    //订单号
    private String orderNumber;

    //微信支付交易号（模拟支付下为 MOCK 前缀流水号）
    private String transactionId;

    //支付方式 1微信，2支付宝
    private Integer payMethod;

    //支付金额
    private BigDecimal amount;

    //支付状态 0待支付 1已支付 2支付失败
    private Integer status;

    //创建时间
    private LocalDateTime createTime;

    //支付完成时间
    private LocalDateTime payTime;
}
