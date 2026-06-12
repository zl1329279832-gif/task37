package io.github.xxyopen.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会员相关配置
 */
@ConfigurationProperties(prefix = "novel.member")
@Component
@Data
public class MemberProperties {

    /**
     * 月度会员价格(屋币)
     */
    private int monthlyPrice = 300;

    /**
     * 季度会员价格(屋币)
     */
    private int quarterlyPrice = 800;

    /**
     * 年度会员价格(屋币)
     */
    private int yearlyPrice = 2800;

    /**
     * 每月免费阅读配额
     */
    private int monthlyFreeReadQuota = 30;

    /**
     * 月度会员折扣率(90=9折)
     */
    private int monthlyDiscountRate = 90;

    /**
     * 季度会员折扣率(80=8折)
     */
    private int quarterlyDiscountRate = 80;

    /**
     * 年度会员折扣率(70=7折)
     */
    private int yearlyDiscountRate = 70;

    /**
     * 每月发放阅读券数量
     */
    private int monthlyCouponCount = 3;

    /**
     * 阅读券抵扣金额(屋币)
     */
    private int couponDiscountAmount = 5;

    /**
     * 阅读券最低消费门槛(屋币)
     */
    private int couponMinPurchaseAmount = 10;

    /**
     * 阅读券有效天数
     */
    private int couponExpireDays = 30;

}
