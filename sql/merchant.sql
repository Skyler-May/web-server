-- ============================================================
-- NiuMa 聚合商户接入（转账钱包）—— 只新增表，不改动既有表结构
-- 自营玩家 merchant 相关字段为 NULL/无记录，逻辑完全不受影响
-- ============================================================

CREATE TABLE IF NOT EXISTS `merchant` (
  `id`              VARCHAR(32)  NOT NULL COMMENT '商户号（merchantId）',
  `name`            VARCHAR(64)  NOT NULL COMMENT '商户名称',
  `secret_key`      VARCHAR(128) NOT NULL COMMENT '当前密钥',
  `secret_key_prev` VARCHAR(128)          DEFAULT NULL COMMENT '轮换期的旧密钥（并行验签用）',
  `prev_expire_at`  DATETIME              DEFAULT NULL COMMENT '旧密钥失效时间',
  `env`             VARCHAR(16)  NOT NULL DEFAULT 'prod' COMMENT '环境 test/prod',
  `ip_whitelist`    VARCHAR(512)          DEFAULT NULL COMMENT 'IP 白名单，逗号分隔，空=不限',
  `launch_base_url` VARCHAR(255)          DEFAULT NULL COMMENT '启动地址前缀，空则用全局配置',
  `launch_param`    VARCHAR(32)  NOT NULL DEFAULT 'userData' COMMENT '启动参数名 userData/token',
  `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `remark`          VARCHAR(255)          DEFAULT NULL,
  `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聚合商户';

CREATE TABLE IF NOT EXISTS `merchant_player` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `merchant_id`      VARCHAR(32)  NOT NULL COMMENT '商户号',
  `player_id`        VARCHAR(32)  NOT NULL COMMENT '平台玩家ID',
  `vendor_player_id` VARCHAR(32)  NOT NULL COMMENT 'NiuMa 玩家ID',
  `nickname`         VARCHAR(45)           DEFAULT NULL,
  `create_time`      DATETIME              DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_player` (`merchant_id`, `player_id`),
  KEY `idx_vendor_player` (`vendor_player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户玩家映射';

CREATE TABLE IF NOT EXISTS `merchant_txn` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `merchant_id`   VARCHAR(32) NOT NULL,
  `txn_id`        VARCHAR(64) NOT NULL COMMENT '平台业务流水号',
  `player_id`     VARCHAR(32) NOT NULL COMMENT '平台玩家ID',
  `type`          VARCHAR(8)  NOT NULL COMMENT 'IN/OUT',
  `amount`        BIGINT      NOT NULL,
  `balance_after` BIGINT      NOT NULL,
  `status`        VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  `request_hash`  VARCHAR(64)          DEFAULT NULL COMMENT '请求参数指纹，用于识别 txnId 冲突',
  `remark`        VARCHAR(255)         DEFAULT NULL,
  `create_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_txn` (`merchant_id`, `txn_id`),
  KEY `idx_merchant_player_time` (`merchant_id`, `player_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户转账流水';

-- 测试商户（仅测试环境使用，生产请通过后台创建并保密）
INSERT INTO `merchant` (`id`, `name`, `secret_key`, `env`, `enabled`, `remark`)
SELECT 'gc_test', 'gamingclub test', 'CHANGE_ME_TEST_SECRET_32_BYTES_MIN', 'test', 1, '测试商户，请勿用于生产'
WHERE NOT EXISTS (SELECT 1 FROM `merchant` WHERE `id` = 'gc_test');
