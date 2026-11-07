-- ====================================================
-- MALL森百货 库存模块数据库变更脚本
-- 基于 mall-sen-shop.sql 追加
-- ====================================================

USE `mall-sen-shop`;

-- ----------------------------
-- 1. product 表加库存字段
-- ----------------------------
ALTER TABLE `product`
    ADD COLUMN `stock`             INT NOT NULL DEFAULT 0  COMMENT '库存数量' AFTER `status`,
    ADD COLUMN `low_stock_warning` INT NOT NULL DEFAULT 10 COMMENT '库存预警阈值' AFTER `stock`;

-- ----------------------------
-- 2. 库存变动记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_record` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`   bigint       NOT NULL                 COMMENT '商品id',
  `type`         tinyint      NOT NULL                 COMMENT '变动类型 1入库 2出库 3盘点调整',
  `quantity`     int          NOT NULL                 COMMENT '变动数量（正数入库，负数出库）',
  `stock_before` int          NOT NULL                 COMMENT '变动前库存',
  `stock_after`  int          NOT NULL                 COMMENT '变动后库存',
  `remark`       varchar(255) DEFAULT NULL             COMMENT '备注',
  `create_time`  datetime     DEFAULT NULL             COMMENT '创建时间',
  `create_user`  bigint       DEFAULT NULL             COMMENT '操作人',
  PRIMARY KEY (`id`),
  KEY `idx_product_id`  (`product_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='库存变动记录';
