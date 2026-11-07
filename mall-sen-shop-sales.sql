-- ====================================================
-- MALL森百货 销量模块数据库变更脚本
-- 基于 mall-sen-shop-inventory.sql 追加
-- ====================================================

USE `mall-sen-shop`;

-- ----------------------------
-- 1. product 表加销量字段
-- ----------------------------
ALTER TABLE `product`
    ADD COLUMN `sales` INT NOT NULL DEFAULT 0 COMMENT '销量' AFTER `low_stock_warning`;
