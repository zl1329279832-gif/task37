package io.github.xxyopen.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 财务相关配置
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@ConfigurationProperties(prefix = "novel.finance")
@Component
@Data
public class FinanceProperties {

    /**
     * 默认章节价格（屋币）
     */
    private int defaultChapterPrice = 10;

    /**
     * 税率百分比，如 20 表示 20%
     */
    private int taxRate = 20;

    /**
     * 每月结算日
     */
    private int settlementDay = 1;

    /**
     * 退款窗口时长（小时）
     */
    private int refundWindowHours = 72;

    /**
     * 会员折扣购买时平台补贴比例（%），即折扣差价中平台承担的部分
     * 例如：章节价10，会员八折付8，差价2中平台补贴 membershipSubsidyRate% 给作者
     */
    private int membershipSubsidyRate = 50;

    /**
     * 基础会员每月免费章节配额
     */
    private int basicMemberFreeQuota = 5;

    /**
     * 高级会员每月免费章节配额
     */
    private int premiumMemberFreeQuota = 20;

    /**
     * 至尊会员每月免费章节配额
     */
    private int supremeMemberFreeQuota = 100;

    /**
     * 基础会员折扣（如 90 = 九折）
     */
    private int basicMemberDiscount = 90;

    /**
     * 高级会员折扣
     */
    private int premiumMemberDiscount = 80;

    /**
     * 至尊会员折扣
     */
    private int supremeMemberDiscount = 60;

    /**
     * 基础会员赠送阅读券数量
     */
    private int basicMemberVoucherCount = 1;

    /**
     * 高级会员赠送阅读券数量
     */
    private int premiumMemberVoucherCount = 3;

    /**
     * 至尊会员赠送阅读券数量
     */
    private int supremeMemberVoucherCount = 10;

    /**
     * 阅读券有效天数
     */
    private int voucherValidDays = 30;

}
