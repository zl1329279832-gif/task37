-- ============================================================
-- 会员权益叠加与作者分账延迟结算 DDL
-- ============================================================

CREATE TABLE IF NOT EXISTS `member_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `member_level` INT NOT NULL DEFAULT 0 COMMENT '0-非会员 1-月度 2-季度 3-年度',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间',
  `free_read_quota` INT NOT NULL DEFAULT 0 COMMENT '每月免费阅读配额',
  `used_free_read` INT NOT NULL DEFAULT 0 COMMENT '当月已使用',
  `quota_reset_date` DATE NOT NULL COMMENT '配额重置日期',
  `discount_rate` INT NOT NULL DEFAULT 100 COMMENT '折扣率(80=8折)',
  `status` INT NOT NULL DEFAULT 0 COMMENT '0-正常 1-已过期',
  `create_time` DATETIME NOT NULL DEFAULT NOW(),
  `update_time` DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员信息';

CREATE TABLE IF NOT EXISTS `member_benefits_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `member_level` INT NOT NULL COMMENT '会员等级',
  `original_price` INT NOT NULL COMMENT '原价(屋币)',
  `actual_price` INT NOT NULL COMMENT '实付(屋币)',
  `benefit_type` INT NOT NULL COMMENT '0-免费读 1-折扣 2-阅读券',
  `benefit_value` INT NOT NULL COMMENT '权益值',
  `chapter_id` BIGINT DEFAULT NULL COMMENT '章节ID',
  `expire_time` DATETIME NOT NULL COMMENT '权益过期时间',
  `create_time` DATETIME NOT NULL DEFAULT NOW(),
  `update_time` DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员权益快照';

CREATE TABLE IF NOT EXISTS `reading_coupon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `coupon_name` VARCHAR(100) NOT NULL COMMENT '券名称',
  `discount_amount` INT NOT NULL COMMENT '抵扣金额(屋币)',
  `min_purchase_amount` INT NOT NULL DEFAULT 0 COMMENT '最低消费门槛',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间',
  `use_status` INT NOT NULL DEFAULT 0 COMMENT '0-未使用 1-已使用 2-已过期',
  `used_time` DATETIME DEFAULT NULL COMMENT '使用时间',
  `consume_log_id` BIGINT DEFAULT NULL COMMENT '关联消费记录ID',
  `create_time` DATETIME NOT NULL DEFAULT NOW(),
  `update_time` DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  KEY `idx_user_id_status` (`user_id`, `use_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阅读券';

CREATE TABLE IF NOT EXISTS `pending_settlement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `author_id` BIGINT NOT NULL COMMENT '作者ID',
  `book_id` BIGINT NOT NULL COMMENT '小说ID',
  `chapter_id` BIGINT NOT NULL COMMENT '章节ID',
  `consume_log_id` BIGINT DEFAULT NULL COMMENT '消费记录ID;NULL=会员免费读',
  `settlement_type` INT NOT NULL COMMENT '0-章节购买 1-会员免费读 2-平台活动',
  `original_amount` INT NOT NULL COMMENT '原价(屋币)',
  `discount_amount` INT NOT NULL DEFAULT 0 COMMENT '折扣减免',
  `coupon_amount` INT NOT NULL DEFAULT 0 COMMENT '阅读券抵扣',
  `actual_amount` INT NOT NULL COMMENT '实付金额',
  `status` INT NOT NULL DEFAULT 0 COMMENT '0-待结算 1-已结算 2-已取消',
  `freeze_end_time` DATETIME NOT NULL COMMENT '退款冻结结束时间',
  `batch_id` BIGINT DEFAULT NULL COMMENT '结算批次ID',
  `create_time` DATETIME NOT NULL DEFAULT NOW(),
  `update_time` DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  KEY `idx_status_freeze` (`status`, `freeze_end_time`),
  KEY `idx_author_book` (`author_id`, `book_id`),
  KEY `idx_consume_log` (`consume_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待结算流水';

CREATE TABLE IF NOT EXISTS `settlement_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_no` VARCHAR(64) NOT NULL COMMENT '批次号',
  `total_amount` INT NOT NULL DEFAULT 0 COMMENT '总金额',
  `chapter_purchase_amount` INT NOT NULL DEFAULT 0 COMMENT '章节购买金额',
  `member_subsidy_amount` INT NOT NULL DEFAULT 0 COMMENT '会员补贴金额',
  `platform_subsidy_amount` INT NOT NULL DEFAULT 0 COMMENT '平台活动补贴金额',
  `status` INT NOT NULL DEFAULT 0 COMMENT '0-处理中 1-已完成 2-失败',
  `settle_time` DATETIME NOT NULL COMMENT '结算时间',
  `create_time` DATETIME NOT NULL DEFAULT NOW(),
  `update_time` DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算批次';

CREATE TABLE IF NOT EXISTS `refund_freeze` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `consume_log_id` BIGINT NOT NULL COMMENT '消费记录ID',
  `pending_settlement_id` BIGINT NOT NULL COMMENT '待结算ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `freeze_amount` INT NOT NULL COMMENT '冻结金额',
  `freeze_end_time` DATETIME NOT NULL COMMENT '冻结结束时间',
  `status` INT NOT NULL DEFAULT 0 COMMENT '0-冻结中 1-已解冻 2-已退款',
  `create_time` DATETIME NOT NULL DEFAULT NOW(),
  `update_time` DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consume_log` (`consume_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款冻结';

CREATE TABLE IF NOT EXISTS `author_income_breakdown` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `author_id` BIGINT NOT NULL COMMENT '作者ID',
  `book_id` BIGINT NOT NULL COMMENT '小说ID',
  `pending_settlement_id` BIGINT NOT NULL COMMENT '待结算ID',
  `batch_id` BIGINT NOT NULL COMMENT '批次ID',
  `income_type` INT NOT NULL COMMENT '0-章节购买 1-会员补贴 2-平台补贴',
  `amount` INT NOT NULL COMMENT '金额(屋币)',
  `create_time` DATETIME NOT NULL DEFAULT NOW(),
  `update_time` DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  KEY `idx_author_book` (`author_id`, `book_id`),
  KEY `idx_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者收入拆分';
