package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读券 响应DTO
 */
@Data
@Builder
public class ReadingCouponRespDto {

    /** 阅读券ID */
    private Long id;

    /** 券名称 */
    private String couponName;

    /** 抵扣金额(屋币) */
    private Integer discountAmount;

    /** 最低消费门槛 */
    private Integer minPurchaseAmount;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 使用状态 */
    private Integer useStatus;

}
