package com.mallsen.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQ 事务消息 Outbox 表实体。
 * <p>
 * 解决「DB 已更新、MQ 发送失败」导致的最终一致性缺口：支付确认与订单状态更新同事务
 * 写入本表（status=0），由 MqOutboxRelayTask 定时投递到 RabbitMQ 并标记已发送；
 * 发送失败保留待发送状态按周期重试，保证 at-least-once，配合消费端幂等避免重复。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqOutbox implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待发送 */
    public static final Integer PENDING = 0;
    /** 已发送 */
    public static final Integer SENT = 1;
    /** 发送失败（将按周期重试） */
    public static final Integer FAILED = 2;

    private Long id;

    //关联订单id
    private Long orderId;

    //关联订单号
    private String orderNumber;

    //MQ 消息体（OrderAsyncMessage 序列化后的 JSON）
    private String payload;

    //发送状态 0待发送 1已发送 2发送失败
    private Integer status;

    //已重试次数
    private Integer retryCount;

    //创建时间
    private LocalDateTime createTime;

    //发送完成时间
    private LocalDateTime sendTime;
}
