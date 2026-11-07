-- ====================================================
-- MALL森百货 支付流水模块数据库变更脚本
-- 基于 mall-sen-shop-sales.sql 追加
-- ====================================================

USE `mall-sen-shop`;

-- ----------------------------
-- 支付流水表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_record` (
  `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`       bigint        NOT NULL COMMENT '订单id',
  `order_number`   varchar(50)   NOT NULL COMMENT '订单号',
  `transaction_id` varchar(64)   DEFAULT NULL COMMENT '微信支付交易号（模拟支付下为MOCK流水号）',
  `pay_method`     int           DEFAULT '1' COMMENT '支付方式 1微信 2支付宝',
  `amount`         decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '支付金额',
  `status`         tinyint       NOT NULL DEFAULT '0' COMMENT '支付状态 0待支付 1已支付 2支付失败',
  `create_time`    datetime      DEFAULT NULL COMMENT '创建时间',
  `pay_time`       datetime      DEFAULT NULL COMMENT '支付完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_number` (`order_number`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='支付流水';

-- ----------------------------
-- MQ 事务消息 Outbox 表
-- 解决支付确认「库已更新、MQ 发送失败导致销量永不确认」的最终一致性缺口：
-- 支付确认与订单状态更新同事务写入本表(status=0)，由 MqOutboxRelayTask 定时投递
-- 到 RabbitMQ 并标记已发送；发送失败保留待发送状态按周期重试（at-least-once）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `mq_outbox` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`     bigint       NOT NULL COMMENT '关联订单id',
  `order_number` varchar(50)  NOT NULL COMMENT '关联订单号',
  `payload`      text         NOT NULL COMMENT 'MQ消息体（OrderAsyncMessage 序列化后的JSON）',
  `status`       tinyint      NOT NULL DEFAULT '0' COMMENT '发送状态 0待发送 1已发送 2发送失败',
  `retry_count`  int          NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `create_time`  datetime     DEFAULT NULL COMMENT '创建时间',
  `send_time`    datetime     DEFAULT NULL COMMENT '发送完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_create` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='MQ事务消息Outbox表';

-- ----------------------------
-- orders.number 唯一索引（订单号生成兜底）
-- 多实例并发生成订单号可能撞号，唯一索引作为最终兜底，
-- 冲突时由 OrderServiceImpl 捕获 DuplicateKeyException 重新生成重试。
-- 若已有重复数据，请先清理再执行：
--   ALTER TABLE `orders` ADD UNIQUE KEY `idx_number` (`number`);
-- ----------------------------
ALTER TABLE `orders` ADD UNIQUE KEY `idx_number` (`number`);
