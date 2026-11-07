-- ====================================================
-- MALL森百货 采购订单模块数据库变更脚本
-- 基于 mall-sen-shop.sql 追加
-- ====================================================

USE `mall-sen-shop`;

-- ----------------------------
-- 1. 采购订单主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_number` varchar(64)  NOT NULL                 COMMENT '采购单号',
  `status`       varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING待审核 APPROVED已通过 RECEIVED已收货 CANCELLED已取消',
  `total_amount` decimal(10,2) NOT NULL DEFAULT 0.00   COMMENT '总金额',
  `notes`        varchar(500) DEFAULT NULL              COMMENT '备注',
  `create_time`  datetime     DEFAULT NULL              COMMENT '创建时间',
  `create_user`  bigint       DEFAULT NULL              COMMENT '创建人',
  `update_time`  datetime     DEFAULT NULL              COMMENT '修改时间',
  `update_user`  bigint       DEFAULT NULL              COMMENT '修改人',
  PRIMARY KEY (`id`),
  KEY `idx_status`      (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='采购订单';

-- ----------------------------
-- 2. 采购订单明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `purchase_order_item` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_order_id`  bigint       NOT NULL                 COMMENT '采购订单id',
  `product_id`         bigint       NOT NULL                 COMMENT '商品id',
  `product_name`       varchar(128) DEFAULT NULL             COMMENT '商品名称',
  `quantity`           int          NOT NULL                 COMMENT '采购数量',
  `unit_price`         decimal(10,2) NOT NULL DEFAULT 0.00   COMMENT '单价',
  `subtotal`           decimal(10,2) NOT NULL DEFAULT 0.00   COMMENT '小计',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_order_id` (`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='采购订单明细';
