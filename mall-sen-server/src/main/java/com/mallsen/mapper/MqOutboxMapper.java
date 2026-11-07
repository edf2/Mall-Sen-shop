package com.mallsen.mapper;

import com.mallsen.entity.MqOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MQ Outbox 表 Mapper：事务内写「待发送」消息，投递线程按批次拉取并标记。
 */
@Mapper
public interface MqOutboxMapper {

    /**
     * 事务内写入待发送消息
     */
    void insert(MqOutbox outbox);

    /**
     * 拉取一批待发送消息（按创建时间升序，先到先发）
     *
     * @param limit 批次大小
     */
    List<MqOutbox> selectPending(@Param("limit") int limit);

    /**
     * 投递成功：标记已发送
     */
    void markSent(@Param("id") Long id);

    /**
     * 投递失败：标记失败并累加重试次数
     */
    void markFailed(@Param("id") Long id);

    /**
     * 仅累加重试次数，不改变状态（用于重试未达阈值时）
     */
    void incrementRetryCount(@Param("id") Long id);
}
